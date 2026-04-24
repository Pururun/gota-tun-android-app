package net.mullvad.gotatunandroid.vpn

import android.util.Log

/**
 * Wrapper for the GotaTun Rust library JNI interface.
 */
class GotaTunWrapper {
    init {
        try {
            System.loadLibrary("gotatun_jni")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("GotaTunWrapper", "Could not load gotatun_jni library", e)
        }
    }

    /**
     * Starts the GotaTun VPN tunnel.
     * @param fd The file descriptor of the TUN interface.
     * @param config The WireGuard configuration string.
     * @param service The VpnService instance used to protect UDP sockets from routing loops.
     * @return 0 on success, error code otherwise.
     */
    external fun startTunnel(fd: Int, config: String, service: android.net.VpnService): Int

    /**
     * Stops the GotaTun VPN tunnel.
     * @return 0 on success, error code otherwise.
     */
    external fun stopTunnel(): Int

    /**
     * Returns current tunnel stats as "lastHandshakeEpochSecs|rxBytes|txBytes",
     * or an empty string when no tunnel is active.
     */
    external fun getStats(): String
}
