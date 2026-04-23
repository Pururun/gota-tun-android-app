package net.mullvad.gotatunandroid.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.model.VpnConfig
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

    fun toggleConnection() {
        val currentState = vpnState.value
        if (currentState is VpnState.Connected) {
            vpnController.disconnect()
        } else if (currentState is VpnState.Idle || currentState is VpnState.Error) {
            val config = configRepository.activeConfig.value
            if (config != null) {
                vpnController.connect(config)
            }
        }
    }
}
