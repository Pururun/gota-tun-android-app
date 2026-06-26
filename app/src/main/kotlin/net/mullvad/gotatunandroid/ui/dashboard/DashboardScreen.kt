package net.mullvad.gotatunandroid.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import net.mullvad.gotatunandroid.domain.model.InterfaceConfig
import net.mullvad.gotatunandroid.domain.model.PeerConfig
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingMode
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme
import net.mullvad.gotatunandroid.vpn.TunnelStats
import net.mullvad.gotatunandroid.vpn.VpnState

private const val BYTES_PER_MB = 1_048_576L
private const val BYTES_PER_KB = 1_024L
private const val DETAIL_LABEL_WEIGHT = 0.35f
private const val DETAIL_VALUE_WEIGHT = 0.65f
private val TUNNEL_STATS_DELAY = 1.seconds

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
  val configs =
      listOf(
          VpnConfig(
              name = "Mullvad SE",
              interfaceConfig =
                  InterfaceConfig(
                      privateKey = "hidden",
                      addresses = listOf("10.67.30.83/32"),
                      dns = listOf("100.64.0.63"),
                  ),
              peers =
                  listOf(
                      PeerConfig(
                          publicKey = "hidden",
                          allowedIps = listOf("0.0.0.0/0", "::/0"),
                          endpointHost = "146.70.116.162",
                          endpointPort = 3574,
                      )
                  ),
          )
      )
  GotaTunAndroidTheme {
    DashboardScreen(
        uiState =
            DashboardUiState.ConfigurationAvailable(
                activeConfig = configs.first(),
                allConfigs = configs,
                vpnState = VpnState.Connected(configs.first()),
                tunnelStats = null,
            ),
        onToggle = {},
        onSelectConfig = {},
        onAddManual = {},
        onImportFile = {},
        onManageConfigs = {},
        onSettings = {},
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onToggle: () -> Unit,
    onSelectConfig: (String) -> Unit,
    onAddManual: () -> Unit,
    onImportFile: () -> Unit,
    onManageConfigs: () -> Unit,
    onSettings: () -> Unit,
) {
  Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        CenterAlignedTopAppBar(
            title = { Text("GotaTun", fontWeight = FontWeight.Bold) },
            actions = {
              IconButton(onClick = onManageConfigs) {
                Icon(
                    Icons.AutoMirrored.Rounded.FormatListBulleted,
                    contentDescription = "Manage Configurations",
                )
              }
              IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
              }
            },
        )
      },
      floatingActionButton = {
        AddTunnelFab(onAddManual = onAddManual, onImportFile = onImportFile)
      },
  ) { innerPadding ->
    when (uiState) {
      DashboardUiState.Loading -> LoadingScreen()
      DashboardUiState.NoConfigurationAvailable -> NoConfiguration(innerPadding)
      is DashboardUiState.ConfigurationAvailable ->
          ConfigurationAvailable(
              uiState = uiState,
              innerPadding = innerPadding,
              onToggle = onToggle,
              onSelectConfig = onSelectConfig,
          )
    }
  }
}

@Composable
private fun ConfigurationAvailable(
    uiState: DashboardUiState.ConfigurationAvailable,
    innerPadding: PaddingValues,
    onToggle: () -> Unit,
    onSelectConfig: (String) -> Unit,
) {
  val connectedConfig = (uiState.vpnState as? VpnState.Connected)?.resolvedConfig
  val displayConfig = connectedConfig ?: uiState.activeConfig

  Column(
      modifier =
          Modifier.fillMaxSize()
              .padding(innerPadding)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 24.dp)
              .padding(top = 12.dp, bottom = 96.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
  ) {
    ConnectionButton(
        isEnabled = true,
        state = uiState.vpnState,
        onClick = onToggle,
    )

    Spacer(modifier = Modifier.height(20.dp))
    StatusCard(state = uiState.vpnState)
    Spacer(modifier = Modifier.height(12.dp))
    ConfigSelectorDropdown(
        activeConfig = uiState.activeConfig,
        allConfigs = uiState.allConfigs,
        onSelectConfig = onSelectConfig,
    )

    if (
        uiState.activeConfig != null &&
            (uiState.vpnState is VpnState.Connected || uiState.vpnState is VpnState.Connecting)
    ) {
      Spacer(modifier = Modifier.height(12.dp))
      ConnectionDetailsCard(config = displayConfig ?: uiState.activeConfig)
    }

    if (uiState.tunnelStats != null && uiState.vpnState is VpnState.Connected) {
      Spacer(modifier = Modifier.height(12.dp))
      TunnelStatsCard(stats = uiState.tunnelStats)
    }
  }
}

@Composable
private fun AddTunnelFab(onAddManual: () -> Unit, onImportFile: () -> Unit) {
  var showMenu by remember { mutableStateOf(false) }
  Column(horizontalAlignment = Alignment.End) {
    if (showMenu) {
      SmallFloatingActionButton(
          onClick = {
            showMenu = false
            onImportFile()
          },
          modifier = Modifier.padding(bottom = 8.dp),
      ) {
        Icon(Icons.Rounded.FileUpload, contentDescription = "Import File")
      }
      SmallFloatingActionButton(
          onClick = {
            showMenu = false
            onAddManual()
          },
          modifier = Modifier.padding(bottom = 16.dp),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add Manual")
      }
    }
    FloatingActionButton(
        onClick = { showMenu = !showMenu },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
      Icon(
          if (showMenu) Icons.Default.Add else Icons.Rounded.Add,
          contentDescription = "Add Tunnel",
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSelectorDropdown(
    activeConfig: VpnConfig?,
    allConfigs: List<VpnConfig>,
    onSelectConfig: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = it },
  ) {
    OutlinedTextField(
        value = activeConfig?.name ?: "No configuration selected",
        onValueChange = {},
        readOnly = true,
        label = { Text("Active Configuration") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier =
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
      if (allConfigs.isEmpty()) {
        DropdownMenuItem(
            text = {
              Text(
                  "No configurations saved",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            },
            onClick = { expanded = false },
        )
      } else {
        allConfigs.forEach { config ->
          DropdownMenuItem(
              text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(config.name, fontWeight = FontWeight.Medium)
                    val peer = config.peers.firstOrNull()
                    val endpointDisplay =
                        peer
                            ?.endpointHost
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { host -> peer.endpointPort?.let { "$host:$it" } ?: host }
                    if (!endpointDisplay.isNullOrEmpty()) {
                      Text(
                          endpointDisplay,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  if (config.id == activeConfig?.id) {
                    Text(
                        "✓",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                  }
                }
              },
              onClick = {
                onSelectConfig(config.id)
                expanded = false
              },
          )
        }
      }
    }
  }
}

@Composable
fun ConnectionButton(
    isEnabled: Boolean,
    state: VpnState,
    onClick: () -> Unit,
) {
  val isConnected = state is VpnState.Connected
  val isConnecting = state is VpnState.Connecting || state is VpnState.Disconnecting

  val backgroundColor by
      animateColorAsState(
          targetValue =
              when {
                isConnected -> MaterialTheme.colorScheme.primary
                state is VpnState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.surfaceVariant
              },
          label = "color",
      )

  val iconColor by
      animateColorAsState(
          targetValue =
              if (isConnected) {
                MaterialTheme.colorScheme.onPrimary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          label = "iconColor",
      )

  val elevation by
      animateDpAsState(
          targetValue = if (isConnected) 8.dp else 2.dp,
          label = "elevation",
      )

  Surface(
      enabled = isEnabled,
      onClick = onClick,
      modifier = Modifier.size(148.dp).clip(CircleShape),
      color = backgroundColor,
      shadowElevation = elevation,
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (isConnecting) {
        CircularProgressIndicator(
            modifier = Modifier.size(88.dp),
            color = iconColor,
            strokeWidth = 6.dp,
        )
      }
      Icon(
          imageVector = Icons.Default.PowerSettingsNew,
          contentDescription = "Toggle Connection",
          modifier = Modifier.size(52.dp),
          tint = iconColor,
      )
    }
  }
}

@Composable
fun StatusCard(state: VpnState) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
          ),
  ) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          modifier =
              Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column {
        Text(
            text =
                when (state) {
                  is VpnState.Connected -> "Secured"
                  is VpnState.Connecting -> "Protecting..."
                  is VpnState.Disconnecting -> "Disconnecting..."
                  is VpnState.Error -> "Protection Failed"
                  else -> "Unprotected"
                },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text =
                when (state) {
                  is VpnState.Connected -> "Your traffic is encrypted"
                  is VpnState.Error -> state.message
                  else -> "Connect to start browsing privately"
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
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
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
          ),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
          text = config.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      if (peer != null && peer.endpointHost.isNotEmpty()) {
        val endpointDisplay =
            peer.endpointPort?.let { "${peer.endpointHost}:$it" } ?: peer.endpointHost
        DetailRow(label = "Server", value = endpointDisplay)
      }
      if (config.interfaceConfig.addresses.isNotEmpty()) {
        DetailRow(
            label = "Tunnel IP",
            value = config.interfaceConfig.addresses.joinToString(", "),
        )
      }
      if (config.interfaceConfig.dns.isNotEmpty()) {
        DetailRow(label = "DNS", value = config.interfaceConfig.dns.joinToString(", "))
      }
      if (peer != null && peer.allowedIps.isNotEmpty()) {
        DetailRow(label = "Allowed IPs", value = peer.allowedIps.joinToString(", "))
      }
      if (config.interfaceConfig.mtu != null) {
        DetailRow(label = "MTU", value = config.interfaceConfig.mtu.toString())
      }
      if (config.splitTunneling.mode != SplitTunnelingMode.DISABLED) {
        DetailRow(
            label = "Split Tunneling",
            value =
                when (config.splitTunneling.mode) {
                  SplitTunnelingMode.EXCLUDE -> "Excluding apps"
                  SplitTunnelingMode.INCLUDE_ONLY -> "Selected apps only"
                  SplitTunnelingMode.DISABLED -> ""
                },
        )
      }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.weight(DETAIL_LABEL_WEIGHT),
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(DETAIL_VALUE_WEIGHT),
    )
  }
}

@Composable
fun TunnelStatsCard(stats: TunnelStats) {
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(TUNNEL_STATS_DELAY)
      now = System.currentTimeMillis()
    }
  }

  val ageSecs =
      if (stats.lastHandshakeEpochSecs > 0)
          now.milliseconds.inWholeSeconds - stats.lastHandshakeEpochSecs
      else -1L

  fun fmt(bytes: Long) =
      when {
        bytes >= BYTES_PER_MB -> "${"%.1f".format(bytes / BYTES_PER_MB.toDouble())} MB"
        bytes >= BYTES_PER_KB -> "${"%.0f".format(bytes / BYTES_PER_KB.toDouble())} KB"
        else -> "$bytes B"
      }

  fun fmtAge(secs: Duration) =
      when {
        secs.isNegative() -> "never"
        else -> secs.toIsoString()
      }

  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
          ),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
          "Tunnel Stats",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.tertiary,
          fontWeight = FontWeight.SemiBold,
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem("↑ Sent", fmt(stats.txBytes))
        StatItem("↓ Received", fmt(stats.rxBytes))
        StatItem("Handshake", fmtAge(ageSecs.seconds))
      }
    }
  }
}

@Composable
private fun StatItem(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun NoConfiguration(innerPadding: PaddingValues) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .padding(innerPadding)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 24.dp)
              .padding(top = 12.dp, bottom = 96.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
  ) {
    ConnectionButton(
        isEnabled = false,
        state = VpnState.Idle,
        onClick = {},
    )
    Spacer(modifier = Modifier.height(20.dp))
    NoConfigurationAvailableCard()
  }
}

@Composable
fun NoConfigurationAvailableCard() {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
          ),
  ) {
    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = "No configurations available",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = "Add a configuration to start using the VPN",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
      )
    }
  }
}

@Composable
private fun LoadingScreen() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}
