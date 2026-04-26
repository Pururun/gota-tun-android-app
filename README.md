# GotaTunAndroid

A WireGuard VPN client for Android built on top of [gotatun](https://github.com/mullvad/gotatun).

Claude Sonnet 4.6 was used to create most of this app.

## Overview

GotaTunAndroid lets you import and manage WireGuard configurations and connect to VPN relays directly from your Android device.

## Configuration File Format

Configurations can be imported from standard WireGuard `.conf` files (via the **Import from file** button on the dashboard). The file must follow the format below.

```ini
[Interface]
PrivateKey = <base64-encoded private key>       # required
Address    = 10.0.0.2/32, fd00::2/128           # one or more CIDR addresses, comma-separated
DNS        = 1.1.1.1, 1.0.0.1                   # optional, comma-separated
MTU        = 1420                               # optional, 1280–1420 (default: omitted)

[Peer]
PublicKey  = <base64-encoded public key>        # required
AllowedIPs = 0.0.0.0/0, ::/0                   # comma-separated CIDR ranges
Endpoint   = relay.example.com:51820            # host:port (IPv4, IPv6 [addr]:port, or hostname)
```

**Notes:**
- Lines starting with `#` and blank lines are ignored.
- Multiple `[Peer]` sections are supported.
- `Address` and `DNS` accept a comma-separated list on a single line, or the key may be repeated on separate lines to add more entries (e.g. two `Address =` lines).
- `MTU` values outside the range 1280–1420 are silently ignored and left unset.
- Endpoint supports IPv4 (`1.2.3.4:51820`), IPv6 (`[2001:db8::1]:51820`), and hostnames (`relay.example.com:51820`).
- Fields not listed above (e.g. `PresharedKey`, `PersistentKeepalive`, `ListenPort`) are accepted in the file but currently not used by the app.

### Example

```ini
[Interface]
PrivateKey = YBL2BqFiITJBqO5J3m9W8fQ7j3G1sH4kL9pN0rT5uE=
Address    = 10.64.0.3/32
DNS        = 10.64.0.1

[Peer]
PublicKey  = XKqEDWG9+1ABcDefGhI2jK3LmNopQrStUvWxYz01234=
AllowedIPs = 0.0.0.0/0
Endpoint   = se-got-wg-001.example.com:51820
```

## Building

### Prerequisites

- Android Studio (with Android SDK)
- Rust toolchain (`rustup`) with Android targets:
  ```
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
  ```
- Android NDK (installable via Android Studio → SDK Manager → SDK Tools)

### Build

The app can be built and run from Android Studio normally.

To build from the command line:
```
./gradlew assembleDebug
```

## References

- **[gotatun](https://github.com/mullvad/gotatun)** — Userspace WireGuard implementation in Rust.

- **[UniFFI](https://mozilla.github.io/uniffi-rs/)** — Mozilla's framework for generating cross-language FFI bindings.

- **[rust-android-gradle](https://github.com/mullvad/rust-android-gradle)** — Gradle plugin that compiles Rust code for Android targets.




