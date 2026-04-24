package net.mullvad.gotatunandroid.vpn

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow
import net.mullvad.gotatunandroid.domain.WireGuardConfigParser
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingMode
import net.mullvad.gotatunandroid.domain.model.VpnConfig

class VpnControllerImpl(
    private val context: Context
) : VpnController {

    override val state: StateFlow<VpnState> = GotaTunService.serviceState

    override fun connect(config: VpnConfig) {
        // Resolve null endpoint ports to random values at connect time (not at save time)
        val resolved = config.copy(
            peers = config.peers.map { peer ->
                if (peer.endpointHost.isNotEmpty() && peer.endpointPort == null)
                    peer.copy(endpointPort = (0..65535).random())
                else peer
            }
        )

        val st = resolved.splitTunneling
        val splitExtra = if (st.mode == SplitTunnelingMode.DISABLED) "DISABLED"
        else "${st.mode.name}:${st.packageNames.joinToString("|")}"

        val intent = Intent(context, GotaTunService::class.java).apply {
            action = GotaTunService.ACTION_CONNECT
            putExtra(GotaTunService.EXTRA_CONFIG, WireGuardConfigParser.serialize(resolved))
            putExtra(GotaTunService.EXTRA_SPLIT_TUNNELING, splitExtra)
        }
        context.startService(intent)
    }

    override fun disconnect() {
        val intent = Intent(context, GotaTunService::class.java).apply {
            action = GotaTunService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}
