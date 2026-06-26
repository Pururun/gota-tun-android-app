package net.mullvad.gotatunandroid

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dev.zacsweers.metro.createGraphFactory
import net.mullvad.gotatunandroid.di.AppGraph
import net.mullvad.gotatunandroid.domain.ConnectController
import net.mullvad.gotatunandroid.ui.config.configImportEntry
import net.mullvad.gotatunandroid.ui.configlist.configListEntry
import net.mullvad.gotatunandroid.ui.dashboard.dashboardEntry
import net.mullvad.gotatunandroid.ui.manual.manualEntry
import net.mullvad.gotatunandroid.ui.navigation.NavigationViewModel
import net.mullvad.gotatunandroid.ui.settings.settingsEntry
import net.mullvad.gotatunandroid.ui.splittunneling.splitTunnelingEntry
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme
import net.mullvad.gotatunandroid.vpn.VpnController

class MainActivity : ComponentActivity() {

    private lateinit var vpnControllerRef: VpnController
    private lateinit var connectController: ConnectController
    private lateinit var navigationViewModelRef: NavigationViewModel

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
        val navigationViewModel = appGraph.navigationViewModel
        val configRepository = appGraph.configRepository
        val appSettingsRepository = appGraph.appSettingsRepository

        connectController = appGraph.connectController
        vpnControllerRef = vpnController
        navigationViewModelRef = navigationViewModel

        setContent {
            GotaTunAndroidTheme {
                NavDisplay(
                    backStack = navigationViewModel.backStack,
                    onBack = { navigationViewModel.popBackStack() },
                    entryProvider = entryProvider {
                        dashboardEntry(
                            vpnController = vpnController,
                            configRepository = configRepository,
                            connectController = connectController,
                            requestVpnPermissionThenConnect = { requestVpnPermissionThenConnect() },
                            navigateTo = { navigationViewModel.navigateTo(it) },
                        )

                        manualEntry(
                            configRepository = configRepository,
                            vpnController = vpnController,
                            onBack = { navigationViewModel.popBackStack() },
                        )

                        configImportEntry(
                            configRepository = configRepository,
                            onBack = { navigationViewModel.popBackStack() },
                        )

                        configListEntry(
                            configRepository = configRepository,
                            connectController = connectController,
                            navigateTo = { navigationViewModel.navigateTo(it) },
                            onBack = { navigationViewModel.popBackStack() },
                        )

                        splitTunnelingEntry(
                            applicationContext = applicationContext,
                            configRepository = configRepository,
                            vpnController = vpnController,
                            onBack = { navigationViewModel.popBackStack() },
                        )

                        settingsEntry(
                            appSettingsRepository = appSettingsRepository,
                            onBack = { navigationViewModel.popBackStack() },
                        )
                    },
                )
            }
        }
    }
}
