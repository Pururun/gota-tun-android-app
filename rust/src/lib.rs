#![allow(non_snake_case, clippy::missing_safety_doc)]

use std::io;
use std::net::SocketAddr;
use std::os::unix::io::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::str::FromStr;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use base64::{Engine as _, prelude::BASE64_STANDARD};
use gotatun::device::uapi::command::{Get, Request, Response};
use gotatun::device::uapi::UapiServer;
use gotatun::device::{DeviceBuilder, Peer};
use gotatun::packet::{Ip, Packet, PacketBufPool};
use gotatun::tun::{IpRecv, IpSend, MtuWatcher};
use gotatun::udp::socket::{UdpSocket, UdpSocketFactory};
use gotatun::udp::{UdpTransportFactory, UdpTransportFactoryParams};
use gotatun::x25519::{PublicKey, StaticSecret};
use ipnetwork::IpNetwork;
use jni::objects::{GlobalRef, JObject, JString, JValue};
use jni::sys::{jint, jstring};
use jni::{JNIEnv, JavaVM};
use tokio::io::unix::AsyncFd;

// ======== Logging ========

fn init_logging() {
    #[cfg(target_os = "android")]
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("GotaTun"),
    );
}

// ======== Shared stats ========

#[derive(Default, Clone, Copy)]
pub struct TunnelStats {
    pub last_handshake_epoch_secs: i64,
    pub rx_bytes: u64,
    pub tx_bytes: u64,
}

// ======== Global tunnel state ========

struct TunnelHandle {
    stop_tx: tokio::sync::oneshot::Sender<()>,
    thread: Option<std::thread::JoinHandle<()>>,
    stats: Arc<Mutex<TunnelStats>>,
}

static TUNNEL: Mutex<Option<TunnelHandle>> = Mutex::new(None);

// ======== Android TUN device (IpSend + IpRecv over raw fd) ========

#[derive(Clone)]
struct AndroidTunDevice {
    fd: Arc<AsyncFd<OwnedFd>>,
    mtu: u16,
}

impl AndroidTunDevice {
    fn new(raw_fd: RawFd, mtu: u16) -> io::Result<Self> {
        let owned = unsafe { OwnedFd::from_raw_fd(raw_fd) };
        Ok(Self {
            fd: Arc::new(AsyncFd::new(owned)?),
            mtu,
        })
    }
}

impl IpSend for AndroidTunDevice {
    async fn send(&mut self, packet: Packet<Ip>) -> io::Result<()> {
        let raw = packet.into_bytes();
        let bytes: Vec<u8> = (&*raw as &[u8]).to_vec();
        loop {
            let mut guard = self.fd.writable().await?;
            match guard.try_io(|inner| {
                let n = unsafe {
                    libc::write(
                        inner.get_ref().as_raw_fd(),
                        bytes.as_ptr() as *const libc::c_void,
                        bytes.len(),
                    )
                };
                if n < 0 { Err(io::Error::last_os_error()) } else { Ok(n as usize) }
            }) {
                Ok(Ok(_)) => return Ok(()),
                Ok(Err(e)) => return Err(e),
                Err(_would_block) => continue,
            }
        }
    }
}

impl IpRecv for AndroidTunDevice {
    async fn recv<'a>(
        &'a mut self,
        pool: &mut PacketBufPool,
    ) -> io::Result<impl Iterator<Item = Packet<Ip>> + Send + 'a> {
        loop {
            let mut pkt = pool.get();
            let n = {
                let mut guard = self.fd.readable().await?;
                let buf: &mut [u8] = &mut pkt;
                let mtu = buf.len().min(self.mtu as usize);
                match guard.try_io(|inner| {
                    let n = unsafe {
                        libc::read(
                            inner.get_ref().as_raw_fd(),
                            buf.as_mut_ptr() as *mut libc::c_void,
                            mtu,
                        )
                    };
                    if n < 0 { Err(io::Error::last_os_error()) } else { Ok(n as usize) }
                }) {
                    Ok(Ok(n)) => n,
                    Ok(Err(e)) => return Err(e),
                    Err(_would_block) => continue,
                }
            };
            pkt.truncate(n);
            match pkt.try_into_ip() {
                Ok(ip_pkt) => return Ok(std::iter::once(ip_pkt)),
                Err(e) => { log::debug!("Skipping non-IP packet from TUN: {e}"); continue; }
            }
        }
    }

    fn mtu(&self) -> MtuWatcher { MtuWatcher::new(self.mtu) }
}

// ======== UDP factory ========

struct ProtectedUdpFactory {
    vm: Arc<JavaVM>,
    service: GlobalRef,
}

impl UdpTransportFactory for ProtectedUdpFactory {
    type SendV4 = UdpSocket;
    type SendV6 = UdpSocket;
    type RecvV4 = UdpSocket;
    type RecvV6 = UdpSocket;

    async fn bind(
        &mut self,
        params: &UdpTransportFactoryParams,
    ) -> io::Result<((Self::SendV4, Self::RecvV4), (Self::SendV6, Self::RecvV6))> {
        let mut std_factory = UdpSocketFactory;
        let result = std_factory.bind(params).await?;
        {
            use std::os::unix::io::AsFd;
            let ((ref send_v4, _), (ref send_v6, _)) = result;
            let v4_raw = send_v4.as_fd().as_raw_fd();
            let v6_raw = send_v6.as_fd().as_raw_fd();
            protect_socket(&self.vm, &self.service, v4_raw)
                .map_err(|e| io::Error::new(io::ErrorKind::Other, e))?;
            protect_socket(&self.vm, &self.service, v6_raw)
                .map_err(|e| io::Error::new(io::ErrorKind::Other, e))?;
        }
        Ok(result)
    }
}

fn protect_socket(vm: &JavaVM, service: &GlobalRef, fd: RawFd) -> Result<(), String> {
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| format!("JNI attach_current_thread failed: {e}"))?;
    let result = env
        .call_method(service, "protect", "(I)Z", &[JValue::Int(fd)])
        .map_err(|e| format!("JNI protect() call failed: {e}"))?;
    match result.z() {
        Ok(true) => Ok(()),
        Ok(false) => Err("VpnService.protect() returned false".to_string()),
        Err(e) => Err(format!("JNI protect() result error: {e}")),
    }
}

// ======== WireGuard config parser ========

struct ParsedConfig {
    private_key: StaticSecret,
    peers: Vec<Peer>,
}

fn parse_key(s: &str) -> Result<[u8; 32], String> {
    let s = s.trim();
    let mut key = [0u8; 32];
    match s.len() {
        64 => {
            for i in 0..32 {
                key[i] = u8::from_str_radix(&s[i * 2..i * 2 + 2], 16)
                    .map_err(|_| format!("Invalid hex key at byte {i}"))?;
            }
        }
        43 | 44 => {
            let decoded = BASE64_STANDARD
                .decode(s)
                .map_err(|e| format!("Invalid base64 key: {e}"))?;
            if decoded.len() != 32 {
                return Err(format!("Key wrong length after base64: {}", decoded.len()));
            }
            key.copy_from_slice(&decoded);
        }
        len => return Err(format!("Invalid key length: {len}")),
    }
    Ok(key)
}

fn flush_peer(
    pub_key: Option<PublicKey>,
    endpoint: Option<SocketAddr>,
    allowed_ips: Vec<IpNetwork>,
    peers: &mut Vec<Peer>,
) {
    if let Some(pk) = pub_key {
        let mut peer = Peer::new(pk);
        if let Some(ep) = endpoint { peer = peer.with_endpoint(ep); }
        peer = peer.with_allowed_ips(allowed_ips);
        peers.push(peer);
    }
}

fn parse_config(config_str: &str) -> Result<ParsedConfig, String> {
    let mut private_key: Option<StaticSecret> = None;
    let mut peers: Vec<Peer> = Vec::new();
    let mut in_peer = false;
    let mut pub_key: Option<PublicKey> = None;
    let mut endpoint: Option<SocketAddr> = None;
    let mut allowed_ips: Vec<IpNetwork> = Vec::new();

    for line in config_str.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') { continue; }
        if line.eq_ignore_ascii_case("[Interface]") {
            if in_peer {
                flush_peer(pub_key.take(), endpoint.take(), std::mem::take(&mut allowed_ips), &mut peers);
                in_peer = false;
            }
            continue;
        }
        if line.eq_ignore_ascii_case("[Peer]") {
            if in_peer {
                flush_peer(pub_key.take(), endpoint.take(), std::mem::take(&mut allowed_ips), &mut peers);
            }
            in_peer = true;
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            let key = key.trim();
            let value = value.trim();
            if !in_peer {
                if key.eq_ignore_ascii_case("PrivateKey") {
                    let bytes = parse_key(value)?;
                    private_key = Some(StaticSecret::from(bytes));
                }
            } else {
                if key.eq_ignore_ascii_case("PublicKey") {
                    let bytes = parse_key(value)?;
                    pub_key = Some(PublicKey::from(bytes));
                } else if key.eq_ignore_ascii_case("Endpoint") {
                    endpoint = Some(value.parse().map_err(|e| format!("Invalid endpoint '{value}': {e}"))?);
                } else if key.eq_ignore_ascii_case("AllowedIPs") {
                    for cidr in value.split(',') {
                        let cidr = cidr.trim();
                        let net = IpNetwork::from_str(cidr)
                            .map_err(|e| format!("Invalid CIDR '{cidr}': {e}"))?;
                        allowed_ips.push(net);
                    }
                }
            }
        }
    }
    if in_peer {
        flush_peer(pub_key.take(), endpoint.take(), std::mem::take(&mut allowed_ips), &mut peers);
    }
    let private_key = private_key.ok_or("Missing PrivateKey in [Interface] section")?;
    Ok(ParsedConfig { private_key, peers })
}

// ======== JNI exports ========

#[unsafe(no_mangle)]
pub extern "C" fn Java_net_mullvad_gotatunandroid_vpn_GotaTunWrapper_startTunnel<'local>(
    mut env: JNIEnv<'local>,
    _obj: JObject<'local>,
    fd: jint,
    config: JString<'local>,
    service: JObject<'local>,
) -> jint {
    init_logging();

    let config_str: String = match env.get_string(&config) {
        Ok(s) => s.into(),
        Err(e) => { log::error!("startTunnel: failed to read config string: {e}"); return -1; }
    };

    let parsed = match parse_config(&config_str) {
        Ok(p) => p,
        Err(e) => { log::error!("startTunnel: failed to parse config: {e}"); return -2; }
    };

    unsafe {
        let flags = libc::fcntl(fd, libc::F_GETFL);
        libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
    }

    let vm = match env.get_java_vm() {
        Ok(vm) => Arc::new(vm),
        Err(e) => { log::error!("startTunnel: failed to get JavaVM: {e}"); return -4; }
    };
    let service_ref = match env.new_global_ref(&service) {
        Ok(r) => r,
        Err(e) => { log::error!("startTunnel: failed to create GlobalRef: {e}"); return -5; }
    };

    let udp_factory = ProtectedUdpFactory { vm, service: service_ref };

    stop_tunnel_impl();

    let (stop_tx, stop_rx) = tokio::sync::oneshot::channel::<()>();
    let (uapi_client, uapi_server) = UapiServer::new();
    // stats are written by the polling task and read by the JNI getStats function
    let stats = Arc::new(Mutex::new(TunnelStats::default()));
    let stats_clone = Arc::clone(&stats);

    let thread = std::thread::spawn(move || {
        let rt = match tokio::runtime::Runtime::new() {
            Ok(rt) => rt,
            Err(e) => { log::error!("tunnel thread: failed to create Tokio runtime: {e}"); return; }
        };

        rt.block_on(async move {
            let tun_device = match AndroidTunDevice::new(fd, 1420) {
                Ok(d) => d,
                Err(e) => { log::error!("tunnel: failed to wrap TUN fd {fd}: {e}"); return; }
            };

            let mut builder = DeviceBuilder::new()
                .with_udp(udp_factory)
                .with_ip(tun_device)
                .with_private_key(parsed.private_key)
                .with_uapi(uapi_server);

            for peer in parsed.peers {
                builder = builder.with_peer(peer);
            }

            match builder.build().await {
                Ok(device) => {
                    log::info!("WireGuard tunnel started successfully");

                    // Move uapi_client into the polling task — avoids holding MutexGuard across await
                    let stats_arc = stats_clone;
                    tokio::spawn(async move {
                        loop {
                            tokio::time::sleep(Duration::from_secs(2)).await;
                            let request = Request::from(Get::default());
                            if let Ok(Response::Get(resp)) = uapi_client.send(request).await {
                                let mut s = stats_arc.lock().unwrap();
                                for peer in &resp.peers {
                                    if let Some(secs) = peer.last_handshake_time_sec {
                                        s.last_handshake_epoch_secs = secs as i64;
                                    }
                                    if let Some(rx) = peer.rx_bytes { s.rx_bytes = rx; }
                                    if let Some(tx) = peer.tx_bytes { s.tx_bytes = tx; }
                                }
                            }
                        }
                    });

                    let _ = stop_rx.await;
                    device.stop().await;
                    log::info!("WireGuard tunnel stopped");
                }
                Err(e) => { log::error!("Failed to build WireGuard device: {e}"); }
            }
        });
    });

    *TUNNEL.lock().unwrap() = Some(TunnelHandle {
        stop_tx,
        thread: Some(thread),
        stats,
    });

    0
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_net_mullvad_gotatunandroid_vpn_GotaTunWrapper_stopTunnel<'local>(
    _env: JNIEnv<'local>,
    _obj: JObject<'local>,
) -> jint {
    if stop_tunnel_impl() { 0 } else { -1 }
}

/// Returns tunnel stats as "lastHandshakeSecs|rxBytes|txBytes" or empty string if no tunnel.
#[unsafe(no_mangle)]
pub extern "C" fn Java_net_mullvad_gotatunandroid_vpn_GotaTunWrapper_getStats<'local>(
    env: JNIEnv<'local>,
    _obj: JObject<'local>,
) -> jstring {
    let result = TUNNEL.lock().unwrap().as_ref().map(|h| {
        let s = h.stats.lock().unwrap();
        format!("{}|{}|{}", s.last_handshake_epoch_secs, s.rx_bytes, s.tx_bytes)
    }).unwrap_or_default();

    env.new_string(result)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn stop_tunnel_impl() -> bool {
    match TUNNEL.lock().unwrap().take() {
        Some(mut handle) => {
            let _ = handle.stop_tx.send(());
            if let Some(thread) = handle.thread.take() { let _ = thread.join(); }
            true
        }
        None => false,
    }
}
