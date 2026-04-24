package net.mullvad.gotatunandroid.ui.configlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.model.VpnConfig

class ConfigListViewModel(
    private val configRepository: ConfigRepository
) : ViewModel() {

    val allConfigs: StateFlow<List<VpnConfig>> = configRepository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeConfig: StateFlow<VpnConfig?> = configRepository.activeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setActiveConfig(id: String) {
        configRepository.setActiveConfig(id)
    }

    fun deleteConfig(id: String) {
        configRepository.deleteConfig(id)
    }
}

