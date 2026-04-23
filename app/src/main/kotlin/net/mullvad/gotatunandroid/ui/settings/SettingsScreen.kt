package net.mullvad.gotatunandroid.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            SettingsSectionTitle("General")
            SettingsToggleItem(
                title = "Kill Switch",
                subtitle = "Block internet if VPN disconnects",
                checked = false,
                onCheckedChange = {}
            )
            HorizontalDivider()
            SettingsSectionTitle("About")
            SettingsInfoItem(title = "Version", value = "1.0.0")
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var state by remember { mutableStateOf(checked) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = state,
                onCheckedChange = {
                    state = it
                    onCheckedChange(it)
                }
            )
        }
    )
}

@Composable
private fun SettingsInfoItem(title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    GotaTunAndroidTheme {
        SettingsScreen(onBack = {})
    }
}

