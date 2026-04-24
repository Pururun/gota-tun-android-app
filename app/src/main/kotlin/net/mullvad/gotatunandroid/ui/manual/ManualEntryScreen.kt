package net.mullvad.gotatunandroid.ui.manual

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.mullvad.gotatunandroid.domain.model.InterfaceConfig
import net.mullvad.gotatunandroid.domain.model.PeerConfig
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    initialConfig: VpnConfig? = null,
    onBack: () -> Unit,
    onSave: (VpnConfig) -> Unit
) {
    val isEditing = initialConfig != null
    val firstPeer = initialConfig?.peers?.firstOrNull()

    var name by remember { mutableStateOf(initialConfig?.name ?: "") }
    var privateKey by remember { mutableStateOf(initialConfig?.interfaceConfig?.privateKey ?: "") }
    var address by remember { mutableStateOf(initialConfig?.interfaceConfig?.addresses?.joinToString(", ") ?: "") }
    var dns by remember { mutableStateOf(initialConfig?.interfaceConfig?.dns?.joinToString(", ") ?: "") }
    var mtu by remember { mutableStateOf(initialConfig?.interfaceConfig?.mtu?.toString() ?: "") }
    var publicKey by remember { mutableStateOf(firstPeer?.publicKey ?: "") }
    var allowedIps by remember { mutableStateOf(firstPeer?.allowedIps?.joinToString(", ") ?: "") }
    var endpointHost by remember { mutableStateOf(firstPeer?.endpointHost ?: "") }
    var endpointPort by remember { mutableStateOf(firstPeer?.endpointPort?.toString() ?: "") }

    var nameError by remember { mutableStateOf(false) }
    var privateKeyError by remember { mutableStateOf(false) }
    var publicKeyError by remember { mutableStateOf(false) }
    var mtuError by remember { mutableStateOf(false) }
    var endpointPortError by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        nameError = name.isBlank()
        privateKeyError = privateKey.isBlank()
        publicKeyError = publicKey.isBlank()
        mtuError = mtu.isNotBlank() && (mtu.toIntOrNull()?.let { it !in 1280..1420 } ?: true)
        endpointPortError = endpointPort.isNotBlank() && (endpointPort.toIntOrNull()?.let { it !in 0..65535 } ?: true)
        return !nameError && !privateKeyError && !publicKeyError && !mtuError && !endpointPortError
    }

    fun buildAndSave() {
        if (!validate()) return
        val config = VpnConfig(
            id = initialConfig?.id ?: UUID.randomUUID().toString(),
            name = name,
            interfaceConfig = InterfaceConfig(
                privateKey = privateKey,
                addresses = address.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                dns = dns.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                mtu = mtu.toIntOrNull()
            ),
            peers = listOf(
                    PeerConfig(
                        publicKey = publicKey,
                        allowedIps = allowedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        endpointHost = endpointHost,
                        endpointPort = endpointPort.toIntOrNull()
                    )
                )
        )
        onSave(config)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Text(
                        if (isEditing) "Edit Configuration" else "Manual Configuration",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { buildAndSave() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Configuration Name") },
                placeholder = { Text("e.g. Home Server, Work VPN…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = nameError,
                supportingText = { if (nameError) Text("Name is required") }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Interface", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = privateKey,
                onValueChange = { privateKey = it; privateKeyError = false },
                label = { Text("Private Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = privateKeyError,
                supportingText = { if (privateKeyError) Text("Private key is required") }
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Addresses (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = dns,
                onValueChange = { dns = it },
                label = { Text("DNS Servers") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = mtu,
                onValueChange = { mtu = it; mtuError = false },
                label = { Text("MTU (optional, 1280–1420)") },
                placeholder = { Text("e.g. 1420") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = mtuError,
                supportingText = { if (mtuError) Text("MTU must be between 1280 and 1420") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Peer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = publicKey,
                onValueChange = { publicKey = it; publicKeyError = false },
                label = { Text("Public Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = publicKeyError,
                supportingText = { if (publicKeyError) Text("Public key is required") }
            )
            OutlinedTextField(
                value = allowedIps,
                onValueChange = { allowedIps = it },
                label = { Text("Allowed IPs") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = endpointHost,
                onValueChange = { endpointHost = it },
                label = { Text("Endpoint Host") },
                placeholder = { Text("e.g. 45.129.56.67") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = endpointPort,
                onValueChange = { endpointPort = it; endpointPortError = false },
                label = { Text("Port (optional, 0–65535)") },
                placeholder = { Text("Random if empty") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = endpointPortError,
                supportingText = { if (endpointPortError) Text("Port must be between 0 and 65535") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ManualEntryPreview() {
    GotaTunAndroidTheme {
        ManualEntryScreen(onBack = {}, onSave = {})
    }
}
