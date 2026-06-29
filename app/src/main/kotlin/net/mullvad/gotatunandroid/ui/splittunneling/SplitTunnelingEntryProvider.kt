package net.mullvad.gotatunandroid.ui.splittunneling

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation3.runtime.EntryProviderScope
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.ui.navigation.Navigator
import net.mullvad.gotatunandroid.ui.splittunneling.SplitTunnelingViewModel.Companion.KEY_ID

fun EntryProviderScope<Destination>.splitTunnelingEntry(
    navigator: Navigator,
) {
  entry<SplitTunneling> { destination ->
    val viewModel: SplitTunnelingViewModel =
        assistedMetroViewModel(
            extras =
                CreationExtras {
                  set(KEY_ID, destination.configId)
                }
        )
    val stState by viewModel.state.collectAsState()

    SplitTunnelingScreen(
        state = stState,
        onBack = navigator::goBack,
        onSetMode = viewModel::setMode,
        onToggleApp = viewModel::toggleApp,
        onToggleShowSystemApps = viewModel::toggleShowSystemApps,
        onSave = viewModel::save,
    )
  }
}
