package net.mullvad.gotatunandroid.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import net.mullvad.gotatunandroid.vpn.GotaTunService
import net.mullvad.gotatunandroid.vpn.TunnelStats
import net.mullvad.gotatunandroid.vpn.VpnController
import net.mullvad.gotatunandroid.vpn.VpnState

class DashboardViewModel(
    private val vpnController: VpnController,
    private val configRepository: ConfigRepository
) : ViewModel() {

    val vpnState: StateFlow<VpnState> = vpnController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VpnState.Idle)

    val activeConfig: StateFlow<VpnConfig?> = configRepository.activeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allConfigs: StateFlow<List<VpnConfig>> = configRepository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tunnelStats: StateFlow<TunnelStats?> = GotaTunService.tunnelStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  fun toggleConnection() {
    val currentState = vpnState.value
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
        if (vpnState.value is VpnState.Connected) {
            val config = configRepository.activeConfig.value
            if (config != null) vpnController.connect(config)
        }
    }
}
