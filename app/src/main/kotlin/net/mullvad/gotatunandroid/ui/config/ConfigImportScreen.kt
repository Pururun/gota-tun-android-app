package net.mullvad.gotatunandroid.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.mullvad.gotatunandroid.domain.model.InterfaceConfig
import net.mullvad.gotatunandroid.domain.model.PeerConfig
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigImportScreen(
    state: ConfigImportViewModel.State,
    onBack: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("Import Configuration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (state) {
            is ConfigImportViewModel.State.Idle -> IdleContent(
                modifier = Modifier.padding(innerPadding),
                onOpenFilePicker = onOpenFilePicker
            )
            is ConfigImportViewModel.State.Preview -> PreviewContent(
                config = state.config,
                modifier = Modifier.padding(innerPadding),
                onConfirm = onConfirm,
                onChooseAnother = onReset
            )
            is ConfigImportViewModel.State.Error -> ErrorContent(
                message = state.message,
                modifier = Modifier.padding(innerPadding),
                onRetry = onReset
            )
        }
    }
}

@Composable
private fun IdleContent(modifier: Modifier = Modifier, onOpenFilePicker: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.FileOpen,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Select a WireGuard .conf file",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You can import configuration files from providers like Mullvad to quickly set up your tunnel.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onOpenFilePicker,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Choose File")
        }
    }
}

@Composable
private fun PreviewContent(
    config: VpnConfig,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    onChooseAnother: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            config.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        ConfigDetailCard(title = "Interface") {
            ConfigDetailRow("Addresses", config.interfaceConfig.addresses.joinToString(", "))
            if (config.interfaceConfig.dns.isNotEmpty()) {
                ConfigDetailRow("DNS", config.interfaceConfig.dns.joinToString(", "))
            }
            ConfigDetailRow(
                "Private Key",
                config.interfaceConfig.privateKey.take(8) + "…"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        config.peers.forEachIndexed { index, peer ->
            ConfigDetailCard(title = if (config.peers.size > 1) "Peer ${index + 1}" else "Peer") {
                ConfigDetailRow("Endpoint", peer.endpoint)
                ConfigDetailRow("Allowed IPs", peer.allowedIps.joinToString(", "))
                ConfigDetailRow("Public Key", peer.publicKey.take(8) + "…")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Connect")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onChooseAnother,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Choose Another File")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Failed to Import",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Try Again")
        }
    }
}

@Composable
private fun ConfigDetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun ConfigDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConfigImportIdlePreview() {
    GotaTunAndroidTheme {
        ConfigImportScreen(
            state = ConfigImportViewModel.State.Idle,
            onBack = {}, onOpenFilePicker = {}, onConfirm = {}, onReset = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConfigImportPreviewState() {
    GotaTunAndroidTheme {
        ConfigImportScreen(
            state = ConfigImportViewModel.State.Preview(
                VpnConfig(
                    name = "Mullvad VPN",
                    interfaceConfig = InterfaceConfig(
                        privateKey = "MPsJKUuUrsEWyqtiiXjvbkPA4/a+GsCXI1bB13D2oF8=",
                        addresses = listOf("10.67.30.83/32", "fc00:bbbb:bbbb:bb01::4:1e52/128"),
                        dns = listOf("100.64.0.63")
                    ),
                    peers = listOf(
                        PeerConfig(
                            publicKey = "rWiQxq5lAWD8v/bws9ITSAvThyZW8cR2x+Ins9ZvvRo=",
                            allowedIps = listOf("0.0.0.0/0", "::0/0"),
                            endpoint = "146.70.116.162:3574"
                        )
                    )
                )
            ),
            onBack = {}, onOpenFilePicker = {}, onConfirm = {}, onReset = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConfigImportErrorPreview() {
    GotaTunAndroidTheme {
        ConfigImportScreen(
            state = ConfigImportViewModel.State.Error("Invalid WireGuard configuration file."),
            onBack = {}, onOpenFilePicker = {}, onConfirm = {}, onReset = {}
        )
    }
}

