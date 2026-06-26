package net.mullvad.gotatunandroid.ui.configlist

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.mullvad.gotatunandroid.domain.model.InterfaceConfig
import net.mullvad.gotatunandroid.domain.model.PeerConfig
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import net.mullvad.gotatunandroid.ui.theme.GotaTunAndroidTheme

@Composable
private fun DeleteConfigDialog(
    configName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Delete Configuration") },
      text = {
        Text("Delete \"${configName ?: "this configuration"}\"? This cannot be undone.")
      },
      confirmButton = {
        TextButton(onClick = onConfirm) {
          Text("Delete", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
      },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigListScreen(
    configs: List<VpnConfig>,
    activeConfig: VpnConfig?,
    onBack: () -> Unit,
    onEditConfig: (VpnConfig) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onSelectConfig: (String) -> Unit,
    onSplitTunneling: (String) -> Unit = {},
) {
  var pendingDeleteId by remember { mutableStateOf<String?>(null) }

  if (pendingDeleteId != null) {
    val configName = configs.find { it.id == pendingDeleteId }?.name
    DeleteConfigDialog(
        configName = configName,
        onConfirm = {
          pendingDeleteId?.let { onDeleteConfig(it) }
          pendingDeleteId = null
        },
        onDismiss = { pendingDeleteId = null },
    )
  }

  Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        LargeTopAppBar(
            title = { Text("Configurations", fontWeight = FontWeight.Bold) },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
              }
            },
        )
      },
  ) { innerPadding ->
    if (configs.isEmpty()) {
      Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
              "No configurations yet",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
          )
          Spacer(Modifier.height(8.dp))
          Text(
              "Add a configuration from the dashboard",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 24.dp),
      ) {
        items(configs, key = { it.id }) { config ->
          val isActive = config.id == activeConfig?.id
          ConfigListItem(
              config = config,
              isActive = isActive,
              onSelect = { onSelectConfig(config.id) },
              onEdit = { onEditConfig(config) },
              onDelete = { pendingDeleteId = config.id },
              onSplitTunneling = { onSplitTunneling(config.id) },
          )
        }
      }
    }
  }
}

@Suppress("LongMethod")
@Composable
private fun ConfigListItem(
    config: VpnConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSplitTunneling: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
      shape = RoundedCornerShape(16.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                  }
          ),
      border = if (isActive) CardDefaults.outlinedCardBorder() else null,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector =
              if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
          contentDescription = if (isActive) "Active" else "Inactive",
          tint =
              if (isActive) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
              },
          modifier = Modifier.size(24.dp),
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = config.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color =
                if (isActive) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
        )
        val peer = config.peers.firstOrNull()
        val endpointDisplay =
            peer
                ?.endpointHost
                ?.takeIf { it.isNotEmpty() }
                ?.let { host -> peer.endpointPort?.let { "$host:$it" } ?: host }
        if (!endpointDisplay.isNullOrEmpty()) {
          Text(
              text = endpointDisplay,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        val addresses = config.interfaceConfig.addresses
        if (addresses.isNotEmpty()) {
          Text(
              text = addresses.joinToString(", "),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          )
        }
      }
      IconButton(onClick = onSplitTunneling) {
        Icon(
            Icons.Rounded.Splitscreen,
            contentDescription = "Split Tunneling",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = onEdit) {
        Icon(
            Icons.Rounded.Edit,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = onDelete) {
        Icon(
            Icons.Rounded.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun ConfigListPreview() {
  val configs =
      listOf(
          VpnConfig(
              id = "1",
              name = "Mullvad SE",
              interfaceConfig =
                  InterfaceConfig("key", listOf("10.0.0.1/32"), listOf("100.64.0.63")),
              peers = listOf(PeerConfig("pubkey", listOf("0.0.0.0/0"), "146.70.116.162:3574")),
          ),
          VpnConfig(
              id = "2",
              name = "Mullvad DE",
              interfaceConfig =
                  InterfaceConfig("key2", listOf("10.0.0.2/32"), listOf("100.64.0.1")),
              peers = listOf(PeerConfig("pubkey2", listOf("0.0.0.0/0"), "193.138.218.74:51820")),
          ),
      )
  GotaTunAndroidTheme {
    ConfigListScreen(
        configs = configs,
        activeConfig = configs.first(),
        onBack = {},
        onEditConfig = {},
        onDeleteConfig = {},
        onSelectConfig = {},
    )
  }
}
