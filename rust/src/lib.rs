#![allow(non_snake_case, clippy::missing_safety_doc)]

use std::io;
use std::net::SocketAddr;
use std::os::unix::io::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::str::FromStr;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use base64::{prelude::BASE64_STANDARD, Engine as _};
use gotatun::device::uapi::command::{Get, Request, Response};
use gotatun::device::uapi::UapiServer;
use gotatun::device::{DeviceBuilder, Peer};
use gotatun::packet::{Ip, Packet, PacketBufPool};
use gotatun::tun::{IpRecv, IpSend, MtuWatcher};
use gotatun::udp::socket::{UdpSocket, UdpSocketFactory};
use gotatun::udp::{UdpTransportFactory, UdpTransportFactoryParams};
use gotatun::x25519::{PublicKey, StaticSecret};
use ipnetwork::IpNetwork;
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

// ======== Socket protection callback ========

/// Kotlin implements this to call VpnService.protect() on every UDP socket
/// opened by the WireGuard device, preventing routing loops.
#[uniffi::export(callback_interface)]
pub trait SocketProtector: Send + Sync {
    fn protect(&self, fd: i32) -> bool;
}

// ======== UDP factory ========

struct ProtectedUdpFactory {
    protector: Arc<dyn SocketProtector>,
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
            if !self.protector.protect(v4_raw) {
                return Err(io::Error::new(io::ErrorKind::Other, "VpnService.protect() failed for v4 socket"));
            }
            if !self.protector.protect(v6_raw) {
                return Err(io::Error::new(io::ErrorKind::Other, "VpnService.protect() failed for v6 socket"));
            }
        }
        Ok(result)
    }
}

// ======== WireGuard config parser ========

struct ParsedConfig {
    private_key: StaticSecret,
    peers: Vec<Peer>,
    mtu: u16,
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
    let mut mtu: u16 = 1420;
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
                } else if key.eq_ignore_ascii_case("MTU") {
                    if let Ok(m) = value.parse::<u16>() {
                        mtu = m;
                    }
                }
            } else {
                if key.eq_ignore_ascii_case("PublicKey") {
                    let bytes = parse_key(value)?;
                    pub_key = Some(PublicKey::from(bytes));
                } else if key.eq_ignore_ascii_case("Endpoint") {
                    // Try literal IP:port first, then fall back to blocking DNS resolution
                    // for hostname:port endpoints (e.g. relay.example.com:51820).
                    let addr = if let Ok(a) = value.parse::<SocketAddr>() {
                        a
                    } else {
                        use std::net::ToSocketAddrs;
                        value.to_socket_addrs()
                            .map_err(|e| format!("Cannot resolve endpoint '{value}': {e}"))?
                            .next()
                            .ok_or_else(|| format!("No addresses found for endpoint '{value}'"))?
                    };
                    endpoint = Some(addr);
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
    Ok(ParsedConfig { private_key, peers, mtu })
}

// ======== uniffi exports ========

/// Start a WireGuard tunnel.
/// `fd` is the TUN file descriptor handed over by Android.
/// `config` is the WireGuard config in wg-quick format.
/// `protector` is a Kotlin callback that calls VpnService.protect() on each UDP socket.
/// Returns true on success.
#[uniffi::export]
fn start_tunnel(fd: i32, config: String, protector: Box<dyn SocketProtector>) -> bool {
    init_logging();

    let parsed = match parse_config(&config) {
        Ok(p) => p,
        Err(e) => { log::error!("start_tunnel_impl: failed to parse config: {e}"); return false; }
    };

    unsafe {
        let flags = libc::fcntl(fd, libc::F_GETFL);
        libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
    }

    // Promote to Arc so it can be shared with the async UDP factory
    let protector: Arc<dyn SocketProtector> = Arc::from(protector);
    let udp_factory = ProtectedUdpFactory { protector };

    stop_tunnel();

    let (stop_tx, stop_rx) = tokio::sync::oneshot::channel::<()>();
    let (uapi_client, uapi_server) = UapiServer::new();
    let stats = Arc::new(Mutex::new(TunnelStats::default()));
    let stats_clone = Arc::clone(&stats);
    let mtu = parsed.mtu;

    let thread = std::thread::spawn(move || {
        let rt = match tokio::runtime::Runtime::new() {
            Ok(rt) => rt,
            Err(e) => { log::error!("tunnel thread: failed to create Tokio runtime: {e}"); return; }
        };

        rt.block_on(async move {
            let tun_device = match AndroidTunDevice::new(fd, mtu) {
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

                    let stats_arc = stats_clone;
                    tokio::spawn(async move {
                        loop {
                            tokio::time::sleep(Duration::from_secs(2)).await;
                            let request = Request::from(Get::default());
                            if let Ok(Response::Get(resp)) = uapi_client.send(request).await {
                                // Aggregate across all peers: sum bytes, take the most recent handshake.
                                let mut total_rx = 0u64;
                                let mut total_tx = 0u64;
                                let mut last_handshake = 0i64;
                                for peer in &resp.peers {
                                    if let Some(secs) = peer.last_handshake_time_sec {
                                        last_handshake = last_handshake.max(secs as i64);
                                    }
                                    if let Some(rx) = peer.rx_bytes { total_rx += rx; }
                                    if let Some(tx) = peer.tx_bytes { total_tx += tx; }
                                }
                                let mut s = stats_arc.lock().unwrap();
                                s.last_handshake_epoch_secs = last_handshake;
                                s.rx_bytes = total_rx;
                                s.tx_bytes = total_tx;
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

    true
}

/// Stop the active WireGuard tunnel. Returns true if a tunnel was running.
#[uniffi::export]
fn stop_tunnel() -> bool {
    match TUNNEL.lock().unwrap().take() {
        Some(mut handle) => {
            let _ = handle.stop_tx.send(());
            if let Some(thread) = handle.thread.take() { let _ = thread.join(); }
            true
        }
        None => false,
    }
}

/// Returns current tunnel stats as "lastHandshakeEpochSecs|rxBytes|txBytes",
/// or None when no tunnel is active.
#[uniffi::export]
fn get_stats() -> Option<String> {
    TUNNEL.lock().unwrap().as_ref().map(|h| {
        let s = h.stats.lock().unwrap();
        format!("{}|{}|{}", s.last_handshake_epoch_secs, s.rx_bytes, s.tx_bytes)
    })
}

uniffi::setup_scaffolding!();
