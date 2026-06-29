package net.mullvad.gotatunandroid

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import net.mullvad.gotatunandroid.di.AppGraph
import net.mullvad.gotatunandroid.domain.ConnectController
import net.mullvad.gotatunandroid.ui.config.configImportEntry
import net.mullvad.gotatunandroid.ui.configlist.configListEntry
import net.mullvad.gotatunandroid.ui.dashboard.Dashboard
import net.mullvad.gotatunandroid.ui.dashboard.dashboardEntry
import net.mullvad.gotatunandroid.ui.manual.manualEntry
import net.mullvad.gotatunandroid.ui.navigation.Navigator
import net.mullvad.gotatunandroid.ui.navigation.rememberNavigationState
import net.mullvad.gotatunandroid.ui.settings.settingsEntry
import net.mullvad.gotatunandroid.ui.splittunneling.splitTunnelingEntry
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme

class MainActivity : ComponentActivity() {

  private lateinit var connectController: ConnectController

  private val vpnPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
          connectController.toggleConnection()
        }
      }

  private fun requestVpnPermissionThenConnect() {
    val permissionIntent = VpnService.prepare(this)
    if (permissionIntent != null) {
      vpnPermissionLauncher.launch(permissionIntent)
    } else {
      connectController.toggleConnection()
    }
  }

  @OptIn(ExperimentalPermissionsApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val appGraph = createGraphFactory<AppGraph.Factory>().create(this)
    val vpnController = appGraph.vpnController
    val configRepository = appGraph.configRepository
    val appSettingsRepository = appGraph.appSettingsRepository
    val metroVmf = appGraph.metroViewModelFactory

    connectController = appGraph.connectController

    setContent {
      GotaTunAndroidTheme {
        CompositionLocalProvider(LocalMetroViewModelFactory provides metroVmf) {
          val navigationState = rememberNavigationState(Dashboard)

          val navigator = remember {
            Navigator(
                state = navigationState,
            )
          }

          NavDisplay(
              backStack = navigator.backStack,
              onBack = { navigator.goBack() },
              entryProvider =
                  entryProvider {
                    dashboardEntry(
                        requestVpnPermissionThenConnect = { requestVpnPermissionThenConnect() },
                        navigator = navigator,
                    )

                    manualEntry(
                        configRepository = configRepository,
                        vpnController = vpnController,
                        navigator = navigator,
                    )

                    configImportEntry(
                        navigator = navigator,
                    )

                    configListEntry(
                        navigator = navigator,
                    )

                    splitTunnelingEntry(
                        navigator = navigator,
                    )

                    settingsEntry(
                        appSettingsRepository = appSettingsRepository,
                        navigator = navigator,
                    )
                  },
          )
        }
      }
    }
  }
}
