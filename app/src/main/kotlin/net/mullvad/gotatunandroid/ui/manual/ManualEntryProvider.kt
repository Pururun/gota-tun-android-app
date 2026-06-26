package net.mullvad.gotatunandroid.ui.manual

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.vpn.VpnController
import net.mullvad.gotatunandroid.vpn.VpnState

fun EntryProviderScope<Destination>.manualEntry(
    configRepository: ConfigRepository,
    vpnController: VpnController,
    onBack: () -> Unit,
) {
  entry<Destination.ManualEntry> { destination ->
    val allConfigs by configRepository.allConfigs.collectAsState()
    val initialConfig = destination.editConfigId?.let { id -> allConfigs.find { it.id == id } }

    ManualEntryScreen(
        initialConfig = initialConfig,
        onBack = onBack,
        onSave = { config ->
          configRepository.saveConfig(config)
          if (destination.editConfigId == null) {
            // New config: make it active
            configRepository.setActiveConfig(config.id)
          } else if (
              vpnController.state.value is VpnState.Connected &&
                  configRepository.activeConfig.value?.id == config.id
          ) {
            // Edited the currently-connected config: reconnect to apply changes
            vpnController.connect(config)
          }
          onBack()
        },
    )
  }
}
