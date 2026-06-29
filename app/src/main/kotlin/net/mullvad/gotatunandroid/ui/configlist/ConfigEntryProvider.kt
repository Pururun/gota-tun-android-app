package net.mullvad.gotatunandroid.ui.configlist

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.ConnectController
import net.mullvad.gotatunandroid.ui.manual.ManualEntry
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.ui.navigation.Navigator
import net.mullvad.gotatunandroid.ui.splittunneling.SplitTunneling

fun EntryProviderScope<Destination>.configListEntry(
    navigator: Navigator,
) {
  entry<ConfigList> {
    val listViewModel: ConfigListViewModel = metroViewModel()
    val configs by listViewModel.allConfigs.collectAsState()
    val activeConfig by listViewModel.activeConfig.collectAsState()

    ConfigListScreen(
        configs = configs,
        activeConfig = activeConfig,
        onBack = navigator::goBack,
        onEditConfig = { config ->
          navigator.navigate(ManualEntry(editConfigId = config.id))
        },
        onDeleteConfig = listViewModel::deleteConfig,
        onSelectConfig = listViewModel::setActiveConfig,
        onSplitTunneling = { configId -> navigator.navigate(SplitTunneling(configId)) },
    )
  }
}
