package net.mullvad.gotatunandroid.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    allowRemoteControl: Boolean = false,
    onToggleRemoteControl: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    // Check at composition time whether the VPN settings screen exists on this device
    val vpnSettingsIntent = remember {
        Intent(Settings.ACTION_VPN_SETTINGS)
    }
    val vpnSettingsAvailable = remember {
        context.packageManager.resolveActivity(
            vpnSettingsIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null
    }

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
            if (vpnSettingsAvailable) {
                SettingsSectionTitle("System")
                SettingsLinkItem(
                    title = "VPN Settings",
                    subtitle = "Configure always-on VPN and kill switch in Android system settings",
                    onClick = { context.startActivity(vpnSettingsIntent) }
                )
                HorizontalDivider()
            }

            SettingsSectionTitle("Automation")
            ListItem(
                headlineContent = { Text("Allow remote control") },
                supportingContent = {
                    Text("Let third-party apps (e.g. Tasker) toggle the tunnel via broadcast intents")
                },
                trailingContent = {
                    Switch(
                        checked = allowRemoteControl,
                        onCheckedChange = onToggleRemoteControl
                    )
                }
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
private fun SettingsLinkItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
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
