package net.mullvad.gotatunandroid.ui.config

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.WireGuardConfigParser
import net.mullvad.gotatunandroid.domain.model.VpnConfig

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ConfigImportViewModel(
    private val configRepository: ConfigRepository
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data class Preview(val config: VpnConfig) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    @Suppress("TooGenericExceptionCaught")
    fun processFileContent(content: String, name: String) {
        try {
            val config = WireGuardConfigParser.parse(content, name)
            _state.value = State.Preview(config)
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Failed to parse configuration")
        }
    }

    fun showError(message: String) {
        _state.value = State.Error(message)
    }

    fun confirmImport() {
        val preview = _state.value as? State.Preview ?: return
        configRepository.saveConfig(preview.config)
        configRepository.setActiveConfig(preview.config.id)
        _navigateBack.tryEmit(Unit)
    }

    fun reset() {
        _state.value = State.Idle
    }
}
