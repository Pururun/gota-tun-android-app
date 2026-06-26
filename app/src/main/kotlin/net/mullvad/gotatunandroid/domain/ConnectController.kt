package net.mullvad.gotatunandroid.domain

import net.mullvad.gotatunandroid.vpn.VpnController
import net.mullvad.gotatunandroid.vpn.VpnState

class ConnectController(
    private val vpnController: VpnController,
    private val configRepository: ConfigRepository
) {
    fun toggleConnection() {
        val currentState = vpnController.state.value
        if (currentState is VpnState.Connected || currentState is VpnState.Connecting) {
            vpnController.disconnect()
        } else if (currentState is VpnState.Idle || currentState is VpnState.Error) {
            val config = configRepository.activeConfig.value
            if (config != null) vpnController.connect(config)
        }
    }

    /**
     * Select a different active configuration. If currently connected, reconnects immediately
     * with the new configuration.
     */
    fun selectConfig(id: String) {
        configRepository.setActiveConfig(id)
        if (vpnController.state.value is VpnState.Connected) {
            val config = configRepository.activeConfig.value
            if (config != null) vpnController.connect(config)
        }
    }
}
