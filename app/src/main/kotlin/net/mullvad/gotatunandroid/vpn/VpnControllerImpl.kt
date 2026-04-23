package net.mullvad.gotatunandroid.vpn

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.WireGuardConfigParser
import net.mullvad.gotatunandroid.domain.model.VpnConfig

class VpnControllerImpl(
    private val context: Context,
    private val configRepository: ConfigRepository
) : VpnController {

    override val state: StateFlow<VpnState> = GotaTunService.serviceState

    override fun connect(config: VpnConfig) {
        configRepository.saveConfig(config)
        val intent = Intent(context, GotaTunService::class.java).apply {
            action = GotaTunService.ACTION_CONNECT
            putExtra(GotaTunService.EXTRA_CONFIG, WireGuardConfigParser.serialize(config))
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
