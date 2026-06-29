package net.mullvad.gotatunandroid.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.ConnectController
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import net.mullvad.gotatunandroid.vpn.GotaTunService
import net.mullvad.gotatunandroid.vpn.TunnelStats
import net.mullvad.gotatunandroid.vpn.VpnController
import net.mullvad.gotatunandroid.vpn.VpnState

sealed interface DashboardUiState {
  data object Loading : DashboardUiState

  data object NoConfigurationAvailable : DashboardUiState

  data class ConfigurationAvailable(
      val vpnState: VpnState,
      val activeConfig: VpnConfig?,
      val allConfigs: List<VpnConfig>,
      val tunnelStats: TunnelStats?,
  ) : DashboardUiState
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class DashboardViewModel(
    vpnController: VpnController,
    configRepository: ConfigRepository,
    private val connectController: ConnectController,
) : ViewModel() {

  val uiState =
      combine(
              vpnController.state,
              configRepository.activeConfig,
              configRepository.allConfigs,
              GotaTunService.tunnelStats,
          ) { vpnState, activeConfig, allConfigs, tunnelStats ->
            if (allConfigs.isEmpty()) {
              DashboardUiState.NoConfigurationAvailable
            } else {
              DashboardUiState.ConfigurationAvailable(
                  vpnState = vpnState,
                  activeConfig = activeConfig,
                  allConfigs = allConfigs,
                  tunnelStats = tunnelStats,
              )
            }
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.Loading)

  /**
   * Select a different active configuration. If currently connected, reconnects immediately with
   * the new configuration.
   */
  fun selectConfig(id: String) {
    connectController.selectConfig(id)
  }
}
