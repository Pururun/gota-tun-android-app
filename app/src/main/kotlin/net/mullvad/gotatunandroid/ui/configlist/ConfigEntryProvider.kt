package net.mullvad.gotatunandroid.ui.configlist

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.ConnectController
import net.mullvad.gotatunandroid.ui.navigation.Destination

fun EntryProviderScope<Destination>.configListEntry(
    configRepository: ConfigRepository,
    connectController: ConnectController,
    navigateTo: (Destination) -> Unit,
    onBack: () -> Unit,
) {
  entry<Destination.ConfigList> {
    val listViewModel: ConfigListViewModel = viewModel {
      ConfigListViewModel(configRepository)
    }
    val configs by listViewModel.allConfigs.collectAsState()
    val activeConfig by listViewModel.activeConfig.collectAsState()

    ConfigListScreen(
        configs = configs,
        activeConfig = activeConfig,
        onBack = onBack,
        onEditConfig = { config ->
          navigateTo(Destination.ManualEntry(editConfigId = config.id))
        },
        onDeleteConfig = { listViewModel.deleteConfig(it) },
        onSelectConfig = { connectController.selectConfig(it) },
        onSplitTunneling = { configId -> navigateTo(Destination.SplitTunneling(configId)) },
    )
  }
}
