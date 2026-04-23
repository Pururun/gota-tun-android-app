package net.mullvad.gotatunandroid.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable
sealed class Destination {
    @Serializable
    data object Dashboard : Destination()
    @Serializable
    data object ManualEntry : Destination()
    @Serializable
    data object ConfigImport : Destination()
    @Serializable
    data object Settings : Destination()
}

class NavigationViewModel @Inject constructor() : ViewModel() {
    private val _backStack = mutableStateListOf<Destination>(Destination.Dashboard)
    val backStack: List<Destination> get() = _backStack

    fun navigateTo(destination: Destination) {
        _backStack.add(destination)
    }

    fun popBackStack() {
        if (_backStack.size > 1) {
            _backStack.removeAt(_backStack.size - 1)
        }
    }
}
