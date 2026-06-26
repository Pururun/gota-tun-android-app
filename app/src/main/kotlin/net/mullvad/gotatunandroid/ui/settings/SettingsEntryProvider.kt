package net.mullvad.gotatunandroid.ui.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import net.mullvad.gotatunandroid.domain.AppSettingsRepository
import net.mullvad.gotatunandroid.ui.navigation.Destination

fun EntryProviderScope<Destination>.settingsEntry(
    appSettingsRepository: AppSettingsRepository,
    onBack: () -> Unit,
) {
  entry<Destination.Settings> {
    val allowRemoteControl by appSettingsRepository.allowRemoteControl.collectAsState()

    SettingsScreen(
        onBack = onBack,
        allowRemoteControl = allowRemoteControl,
        onToggleRemoteControl = { appSettingsRepository.setAllowRemoteControl(it) },
    )
  }
}
