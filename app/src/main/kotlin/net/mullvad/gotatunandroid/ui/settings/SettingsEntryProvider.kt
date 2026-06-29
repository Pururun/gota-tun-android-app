package net.mullvad.gotatunandroid.ui.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import net.mullvad.gotatunandroid.domain.AppSettingsRepository
import net.mullvad.gotatunandroid.ui.navigation.Destination
import net.mullvad.gotatunandroid.ui.navigation.Navigator

fun EntryProviderScope<Destination>.settingsEntry(
    appSettingsRepository: AppSettingsRepository,
    navigator: Navigator,
) {
  entry<Settings> {
    val allowRemoteControl by appSettingsRepository.allowRemoteControl.collectAsState()

    SettingsScreen(
        onBack = navigator::goBack,
        allowRemoteControl = allowRemoteControl,
        onToggleRemoteControl = { appSettingsRepository.setAllowRemoteControl(it) },
    )
  }
}
