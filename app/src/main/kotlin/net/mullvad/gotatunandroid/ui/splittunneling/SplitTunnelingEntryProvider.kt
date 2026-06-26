package net.mullvad.gotatunandroid.ui.splittunneling

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.vpn.VpnController

fun EntryProviderScope<Destination>.splitTunnelingEntry(
    applicationContext: Context,
    configRepository: ConfigRepository,
    vpnController: VpnController,
    onBack: () -> Unit,
) {
  entry<Destination.SplitTunneling> { destination ->
    val stViewModel: SplitTunnelingViewModel = viewModel {
      SplitTunnelingViewModel(
          configId = destination.configId,
          configRepository = configRepository,
          vpnController = vpnController,
          context = applicationContext,
      )
    }
    val stState by stViewModel.state.collectAsState()

    SplitTunnelingScreen(
        state = stState,
        onBack = onBack,
        onSetMode = { stViewModel.setMode(it) },
        onToggleApp = { stViewModel.toggleApp(it) },
        onToggleShowSystemApps = { stViewModel.toggleShowSystemApps() },
        onSave = { stViewModel.save() },
    )
  }
}
