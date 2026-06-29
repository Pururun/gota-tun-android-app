package net.mullvad.gotatunandroid.ui.dashboard

import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.mullvad.gotatunandroid.ui.config.ConfigImport
import net.mullvad.gotatunandroid.ui.configlist.ConfigList
import net.mullvad.gotatunandroid.ui.manual.ManualEntry
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.ui.navigation.Navigator
import net.mullvad.gotatunandroid.ui.settings.Settings

@OptIn(ExperimentalPermissionsApi::class)
fun EntryProviderScope<Destination>.dashboardEntry(
    navigator: Navigator,
    requestVpnPermissionThenConnect: () -> Unit,
) {
  entry<Dashboard> {
    // Request POST_NOTIFICATIONS permission on Android 13+ so the
    // foreground-service notification is visible to the user.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val notifPermission = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
      LaunchedEffect(notifPermission.status) {
        if (!notifPermission.status.isGranted) {
          notifPermission.launchPermissionRequest()
        }
      }
    }
    val viewModel: DashboardViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsState()

    DashboardScreen(
        uiState = uiState,
        onToggle = { requestVpnPermissionThenConnect() },
        onSelectConfig = viewModel::selectConfig,
        onAddManual = { navigator.navigate(ManualEntry()) },
        onImportFile = { navigator.navigate(ConfigImport) },
        onManageConfigs = {
          navigator.navigate(ConfigList)
        },
        onSettings = { navigator.navigate(Settings) },
    )
  }
}
