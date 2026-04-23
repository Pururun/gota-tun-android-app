package net.mullvad.gotatunandroid.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.mullvad.gotatunandroid.domain.model.InterfaceConfig
import net.mullvad.gotatunandroid.domain.model.PeerConfig
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme
import net.mullvad.gotatunandroid.vpn.VpnState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: VpnState,
    activeConfig: VpnConfig?,
    onToggle: () -> Unit,
    onAddManual: () -> Unit,
    onImportFile: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("GotaTun", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            var showMenu by remember { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.End) {
                if (showMenu) {
                    SmallFloatingActionButton(
                        onClick = onImportFile,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Import File")
                    }
                    SmallFloatingActionButton(
                        onClick = onAddManual,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Manual")
                    }
                }
                LargeFloatingActionButton(
                    onClick = { showMenu = !showMenu },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        if (showMenu) Icons.Default.Add else Icons.Rounded.Add,
                        contentDescription = "Add Tunnel"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ConnectionButton(
                state = state,
                onClick = onToggle
            )

            Spacer(modifier = Modifier.height(48.dp))

            StatusCard(state = state)

            if (activeConfig != null && (state is VpnState.Connected || state is VpnState.Connecting)) {
                Spacer(modifier = Modifier.height(16.dp))
                ConnectionDetailsCard(config = activeConfig)
            }
        }
    }
}

@Composable
fun ConnectionButton(
    state: VpnState,
    onClick: () -> Unit
) {
    val isConnected = state is VpnState.Connected
    val isConnecting = state is VpnState.Connecting || state is VpnState.Disconnecting

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isConnected -> MaterialTheme.colorScheme.primary
            state is VpnState.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "color"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "iconColor"
    )

    val elevation by animateDpAsState(
        targetValue = if (isConnected) 8.dp else 2.dp,
        label = "elevation"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape),
        color = backgroundColor,
        shadowElevation = elevation
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(120.dp),
                    color = iconColor,
                    strokeWidth = 8.dp
                )
            }
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Toggle Connection",
                modifier = Modifier.size(80.dp),
                tint = iconColor
            )
        }
    }
}

@Composable
fun StatusCard(state: VpnState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = when (state) {
                        is VpnState.Connected -> "Secured"
                        is VpnState.Connecting -> "Protecting..."
                        is VpnState.Disconnecting -> "Disconnecting..."
                        is VpnState.Error -> "Protection Failed"
                        else -> "Unprotected"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (state) {
                        is VpnState.Connected -> "Your traffic is encrypted"
                        is VpnState.Error -> state.message
                        else -> "Connect to start browsing privately"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ConnectionDetailsCard(config: VpnConfig) {
    val peer = config.peers.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = config.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (peer != null && peer.endpoint.isNotEmpty()) {
                DetailRow(label = "Server", value = peer.endpoint)
            }
            if (config.interfaceConfig.addresses.isNotEmpty()) {
                DetailRow(label = "Tunnel IP", value = config.interfaceConfig.addresses.joinToString(", "))
            }
            if (config.interfaceConfig.dns.isNotEmpty()) {
                DetailRow(label = "DNS", value = config.interfaceConfig.dns.joinToString(", "))
            }
            if (peer != null && peer.allowedIps.isNotEmpty()) {
                DetailRow(label = "Allowed IPs", value = peer.allowedIps.joinToString(", "))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.65f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    GotaTunAndroidTheme {
        DashboardScreen(
            state = VpnState.Connected,
            activeConfig = VpnConfig(
                name = "Mullvad SE",
                interfaceConfig = InterfaceConfig(
                    privateKey = "hidden",
                    addresses = listOf("10.67.30.83/32"),
                    dns = listOf("100.64.0.63")
                ),
                peers = listOf(
                    PeerConfig(
                        publicKey = "hidden",
                        allowedIps = listOf("0.0.0.0/0", "::/0"),
                        endpoint = "146.70.116.162:3574"
                    )
                )
            ),
            onToggle = {},
            onAddManual = {},
            onImportFile = {},
            onSettings = {}
        )
    }
}
