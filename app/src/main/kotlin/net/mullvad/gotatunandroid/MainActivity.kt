package net.mullvad.gotatunandroid

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.zacsweers.metro.createGraphFactory
import net.mullvad.gotatunandroid.di.AppGraph
import net.mullvad.gotatunandroid.domain.WireGuardConfigParser
import net.mullvad.gotatunandroid.ui.config.ConfigImportScreen
import net.mullvad.gotatunandroid.ui.config.ConfigImportViewModel
import net.mullvad.gotatunandroid.ui.dashboard.DashboardScreen
import net.mullvad.gotatunandroid.ui.dashboard.DashboardViewModel
import net.mullvad.gotatunandroid.ui.manual.ManualEntryScreen
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.ui.settings.SettingsScreen
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme

class MainActivity : ComponentActivity() {

    private lateinit var vpnControllerRef: net.mullvad.gotatunandroid.vpn.VpnController
    private lateinit var navigationViewModelRef: net.mullvad.gotatunandroid.ui.navigation.NavigationViewModel
    private lateinit var configImportViewModelRef: ConfigImportViewModel
    private lateinit var dashboardViewModelRef: DashboardViewModel

    // Launched when VPN permission has been granted by the user
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted — proceed with the connection
            dashboardViewModelRef.toggleConnection()
        }
    }

    private fun requestVpnPermissionThenConnect() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            // Show the system VPN permission dialog
            vpnPermissionLauncher.launch(permissionIntent)
        } else {
            // Already has permission
            dashboardViewModelRef.toggleConnection()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val content = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@registerForActivityResult

            val fileName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?: "Imported"

            configImportViewModelRef.processFileContent(content, fileName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Setup DI
        val appGraph = createGraphFactory<AppGraph.Factory>().create(this)
        val vpnController = appGraph.vpnController
        val navigationViewModel = appGraph.navigationViewModel
        val configRepository = appGraph.configRepository

        vpnControllerRef = vpnController
        navigationViewModelRef = navigationViewModel

        setContent {
            GotaTunAndroidTheme {
                NavDisplay(
                    backStack = navigationViewModel.backStack,
                    onBack = { navigationViewModel.popBackStack() },
                    entryProvider = entryProvider {
                        entry<Destination.Dashboard> {
                            val viewModel: DashboardViewModel = viewModel {
                                DashboardViewModel(vpnController, configRepository)
                            }
                            dashboardViewModelRef = viewModel
                            val state by viewModel.vpnState.collectAsState()
                            val activeConfig by viewModel.activeConfig.collectAsState()

                            DashboardScreen(
                                state = state,
                                activeConfig = activeConfig,
                                onToggle = { requestVpnPermissionThenConnect() },
                                onAddManual = { navigationViewModel.navigateTo(Destination.ManualEntry) },
                                onImportFile = { navigationViewModel.navigateTo(Destination.ConfigImport) },
                                onSettings = { navigationViewModel.navigateTo(Destination.Settings) }
                            )
                        }
                        entry<Destination.ManualEntry> {
                            ManualEntryScreen(
                                onBack = { navigationViewModel.popBackStack() },
                                onSave = { pk, addr, dns, pub, allowed, end ->
                                    val config = net.mullvad.gotatunandroid.domain.model.VpnConfig(
                                        name = "Manual",
                                        interfaceConfig = net.mullvad.gotatunandroid.domain.model.InterfaceConfig(
                                            privateKey = pk,
                                            addresses = addr.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                            dns = dns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        ),
                                        peers = listOf(
                                            net.mullvad.gotatunandroid.domain.model.PeerConfig(
                                                publicKey = pub,
                                                allowedIps = allowed.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                                endpoint = end
                                            )
                                        )
                                    )
                                    vpnController.connect(config)
                                    navigationViewModel.popBackStack()
                                }
                            )
                        }
                        entry<Destination.ConfigImport> {
                            val importViewModel: ConfigImportViewModel = viewModel {
                                ConfigImportViewModel(vpnController)
                            }
                            configImportViewModelRef = importViewModel

                            val importState by importViewModel.state.collectAsState()

                            LaunchedEffect(importViewModel) {
                                importViewModel.navigateBack.collect {
                                    navigationViewModel.popBackStack()
                                }
                            }

                            ConfigImportScreen(
                                state = importState,
                                onBack = { navigationViewModel.popBackStack() },
                                onOpenFilePicker = { filePickerLauncher.launch("*/*") },
                                onConfirm = { importViewModel.confirmImport() },
                                onReset = { importViewModel.reset() }
                            )
                        }
                        entry<Destination.Settings> {
                            SettingsScreen(
                                onBack = { navigationViewModel.popBackStack() }
                            )
                        }
                    }
                )
            }
        }
    }
}
