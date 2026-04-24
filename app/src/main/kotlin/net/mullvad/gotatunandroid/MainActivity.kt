package net.mullvad.gotatunandroid

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import android.provider.OpenableColumns
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
import net.mullvad.gotatunandroid.ui.config.ConfigImportScreen
import net.mullvad.gotatunandroid.ui.config.ConfigImportViewModel
import net.mullvad.gotatunandroid.ui.configlist.ConfigListScreen
import net.mullvad.gotatunandroid.ui.configlist.ConfigListViewModel
import net.mullvad.gotatunandroid.ui.dashboard.DashboardScreen
import net.mullvad.gotatunandroid.ui.dashboard.DashboardViewModel
import net.mullvad.gotatunandroid.ui.manual.ManualEntryScreen
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.ui.settings.SettingsScreen
import net.mullvad.gotatunandroid.ui.splittunneling.SplitTunnelingScreen
import net.mullvad.gotatunandroid.ui.splittunneling.SplitTunnelingViewModel
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme

class MainActivity : ComponentActivity() {

  private lateinit var vpnControllerRef: net.mullvad.gotatunandroid.vpn.VpnController
  private lateinit var navigationViewModelRef:
      net.mullvad.gotatunandroid.ui.navigation.NavigationViewModel
  private lateinit var configImportViewModelRef: ConfigImportViewModel
  private lateinit var dashboardViewModelRef: DashboardViewModel

  // Launched when VPN permission has been granted by the user
  private val vpnPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
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

  private val filePickerLauncher =
      registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
          val content =
              contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                  ?: return@registerForActivityResult

          val fileName =
              contentResolver
                  .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                  ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                      cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                  }
                  ?.substringBeforeLast('.') ?: "Imported"

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
    val appSettingsRepository = appGraph.appSettingsRepository

    vpnControllerRef = vpnController
    navigationViewModelRef = navigationViewModel

    setContent {
      GotaTunAndroidTheme {
        NavDisplay(
            backStack = navigationViewModel.backStack,
            onBack = { navigationViewModel.popBackStack() },
            entryProvider =
                entryProvider {
                  entry<Destination.Dashboard> {
                    val viewModel: DashboardViewModel = viewModel {
                      DashboardViewModel(vpnController, configRepository)
                    }
                    dashboardViewModelRef = viewModel
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
                        onAddManual = { navigationViewModel.navigateTo(Destination.ManualEntry()) },
                        onImportFile = { navigationViewModel.navigateTo(Destination.ConfigImport) },
                        onManageConfigs = {
                          navigationViewModel.navigateTo(Destination.ConfigList)
                        },
                        onSettings = { navigationViewModel.navigateTo(Destination.Settings) },
                    )
                  }

                  entry<Destination.ManualEntry> { destination ->
                    val allConfigs by configRepository.allConfigs.collectAsState()
                    val initialConfig =
                        destination.editConfigId?.let { id -> allConfigs.find { it.id == id } }

                    ManualEntryScreen(
                        initialConfig = initialConfig,
                        onBack = { navigationViewModel.popBackStack() },
                        onSave = { config ->
                          configRepository.saveConfig(config)
                          // Only set as active when adding a brand-new config
                          if (destination.editConfigId == null) {
                            configRepository.setActiveConfig(config.id)
                          }
                          navigationViewModel.popBackStack()
                        },
                    )
                  }

                  entry<Destination.ConfigImport> {
                    val importViewModel: ConfigImportViewModel = viewModel {
                      ConfigImportViewModel(configRepository)
                    }
                    configImportViewModelRef = importViewModel

                    val importState by importViewModel.state.collectAsState()

                    LaunchedEffect(importViewModel) {
                      importViewModel.navigateBack.collect { navigationViewModel.popBackStack() }
                    }

                    ConfigImportScreen(
                        state = importState,
                        onBack = { navigationViewModel.popBackStack() },
                        onOpenFilePicker = { filePickerLauncher.launch("*/*") },
                        onConfirm = { importViewModel.confirmImport() },
                        onReset = { importViewModel.reset() },
                    )
                  }

                  entry<Destination.ConfigList> {
                    val listViewModel: ConfigListViewModel = viewModel {
                      ConfigListViewModel(configRepository)
                    }
                    val configs by listViewModel.allConfigs.collectAsState()
                    val activeConfig by listViewModel.activeConfig.collectAsState()

                    ConfigListScreen(
                        configs = configs,
                        activeConfig = activeConfig,
                        onBack = { navigationViewModel.popBackStack() },
                        onEditConfig = { config ->
                          navigationViewModel.navigateTo(
                              Destination.ManualEntry(editConfigId = config.id)
                          )
                        },
                        onDeleteConfig = { listViewModel.deleteConfig(it) },
                        onSelectConfig = { listViewModel.setActiveConfig(it) },
                        onSplitTunneling = { configId ->
                          navigationViewModel.navigateTo(Destination.SplitTunneling(configId))
                        },
                    )
                  }

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
                        onBack = { navigationViewModel.popBackStack() },
                        onSetMode = { stViewModel.setMode(it) },
                        onToggleApp = { stViewModel.toggleApp(it) },
                        onToggleShowSystemApps = { stViewModel.toggleShowSystemApps() },
                        onSave = { stViewModel.save() },
                    )
                  }

                  entry<Destination.Settings> {
                    val allowRemoteControl by
                        appSettingsRepository.allowRemoteControl.collectAsState()

                    SettingsScreen(
                        onBack = { navigationViewModel.popBackStack() },
                        allowRemoteControl = allowRemoteControl,
                        onToggleRemoteControl = { appSettingsRepository.setAllowRemoteControl(it) },
                    )
                  }
                },
        )
      }
    }
  }
}
