package net.mullvad.gotatunandroid.ui.configlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.ConnectController
import net.mullvad.gotatunandroid.domain.model.VpnConfig

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ConfigListViewModel(
    private val configRepository: ConfigRepository,
    private val connectController: ConnectController,
) : ViewModel() {

  val allConfigs: StateFlow<List<VpnConfig>> =
      configRepository.allConfigs.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          emptyList(),
      )

  val activeConfig: StateFlow<VpnConfig?> =
      configRepository.activeConfig.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          null,
      )

  fun setActiveConfig(id: String) {
    connectController.selectConfig(id)
  }

  fun deleteConfig(id: String) {
    configRepository.deleteConfig(id)
  }
}
