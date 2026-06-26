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
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.ConnectController
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.vpn.VpnController

@OptIn(ExperimentalPermissionsApi::class)
fun EntryProviderScope<Destination>.dashboardEntry(
    vpnController: VpnController,
    configRepository: ConfigRepository,
    connectController: ConnectController,
    navigateTo: (Destination) -> Unit,
    requestVpnPermissionThenConnect: () -> Unit,
) {
  entry<Destination.Dashboard> {
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
    val viewModel: DashboardViewModel = viewModel {
      DashboardViewModel(vpnController, configRepository, connectController)
    }
    val state by viewModel.vpnState.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()
    val allConfigs by viewModel.allConfigs.collectAsState()
    val tunnelStats by viewModel.tunnelStats.collectAsState()

    DashboardScreen(
        state = state,
        activeConfig = activeConfig,
        allConfigs = allConfigs,
        tunnelStats = tunnelStats,
        onToggle = { requestVpnPermissionThenConnect() },
        onSelectConfig = { viewModel.selectConfig(it) },
        onAddManual = { navigateTo(Destination.ManualEntry()) },
        onImportFile = { navigateTo(Destination.ConfigImport) },
        onManageConfigs = {
          navigateTo(Destination.ConfigList)
        },
        onSettings = { navigateTo(Destination.Settings) },
    )
  }
}
