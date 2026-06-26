package net.mullvad.gotatunandroid.ui.config

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.ui.navigation.Destination

fun EntryProviderScope<Destination>.configImportEntry(
    configRepository: ConfigRepository,
    onBack: () -> Unit,
) {
    entry<Destination.ConfigImport> {
        val importViewModel: ConfigImportViewModel = viewModel {
            ConfigImportViewModel(configRepository)
        }
        val context = LocalContext.current
        val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (content == null) {
                importViewModel.showError("Failed to read file")
                return@rememberLauncherForActivityResult
            }
            val fileName = runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        } else {
                            null
                        }
                    }
                    ?.substringBeforeLast('.')
            }.getOrNull() ?: "Imported"
            importViewModel.processFileContent(content, fileName)
        }

        val importState by importViewModel.state.collectAsState()

        LaunchedEffect(importViewModel) {
            importViewModel.navigateBack.collect { onBack() }
        }

        ConfigImportScreen(
            state = importState,
            onBack = onBack,
            onOpenFilePicker = { filePickerLauncher.launch("*/*") },
            onConfirm = { importViewModel.confirmImport() },
            onReset = { importViewModel.reset() },
        )
    }
}
