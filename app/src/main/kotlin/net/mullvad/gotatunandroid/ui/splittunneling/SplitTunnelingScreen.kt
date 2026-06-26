package net.mullvad.gotatunandroid.ui.splittunneling

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingMode

@Composable
private fun ModeSelectorSection(
    currentMode: SplitTunnelingMode,
    onSetMode: (SplitTunnelingMode) -> Unit,
) {
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
        "Mode",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    SplitTunnelingMode.entries.forEach { mode ->
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      ) {
        RadioButton(selected = currentMode == mode, onClick = { onSetMode(mode) })
        Spacer(Modifier.width(8.dp))
        Column {
          Text(
              text =
                  when (mode) {
                    SplitTunnelingMode.DISABLED -> "Disabled"
                    SplitTunnelingMode.EXCLUDE -> "Exclude selected apps"
                    SplitTunnelingMode.INCLUDE_ONLY -> "Include only selected apps"
                  },
              style = MaterialTheme.typography.bodyLarge,
          )
          Text(
              text =
                  when (mode) {
                    SplitTunnelingMode.DISABLED -> "All traffic goes through the VPN"
                    SplitTunnelingMode.EXCLUDE -> "Selected apps bypass the VPN"
                    SplitTunnelingMode.INCLUDE_ONLY -> "Only selected apps use the VPN"
                  },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun AppListHeader(
    selectedCount: Int,
    visibleCount: Int,
    showSystemApps: Boolean,
    onToggleShowSystemApps: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = "Applications  ·  $selectedCount selected",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
      )
      Text(
          text = "$visibleCount shown · VPN apps hidden",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          "System",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.width(4.dp))
      Switch(checked = showSystemApps, onCheckedChange = { onToggleShowSystemApps() })
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingScreen(
    state: SplitTunnelingUiState,
    onBack: () -> Unit,
    onSetMode: (SplitTunnelingMode) -> Unit,
    onToggleApp: (String) -> Unit,
    onToggleShowSystemApps: () -> Unit,
    onSave: () -> Unit,
) {
  Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        LargeTopAppBar(
            title = { Text("Split Tunneling", fontWeight = FontWeight.Bold) },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
              }
            },
            actions = {
              Button(
                  onClick = {
                    onSave()
                    onBack()
                  },
                  modifier = Modifier.padding(end = 8.dp),
              ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
              }
            },
        )
      },
  ) { innerPadding ->
    if (state.isLoading) {
      Loading(innerPadding)
      return@Scaffold
    }

    val visibleApps =
        remember(state.apps, state.showSystemApps) {
          if (state.showSystemApps) state.apps else state.apps.filter { !it.isSystem }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item {
        ModeSelectorSection(currentMode = state.mode, onSetMode = onSetMode)
        HorizontalDivider()
      }

      if (state.mode != SplitTunnelingMode.DISABLED) {
        item {
          AppListHeader(
              selectedCount = state.selected.size,
              visibleCount = visibleApps.size,
              showSystemApps = state.showSystemApps,
              onToggleShowSystemApps = onToggleShowSystemApps,
          )
        }

        appItems(
            visibleApps = visibleApps,
            selectedApps = state.selected,
            onToggleApp = onToggleApp,
        )
      }
    }
  }
}

@Composable
private fun Loading(innerPadding: PaddingValues) {
  Box(
      Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator()
  }
}

private fun LazyListScope.appItems(
    visibleApps: List<AppItem>,
    selectedApps: Set<String>,
    onToggleApp: (packageName: String) -> Unit,
) {
  items(visibleApps, key = { it.packageName }) { app ->
    ListItem(
        leadingContent = {
          Image(
              bitmap = app.icon.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
          )
        },
        headlineContent = {
          Text(app.label, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
          Text(
              app.packageName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        trailingContent = {
          Checkbox(
              checked = app.packageName in selectedApps,
              onCheckedChange = { onToggleApp(app.packageName) },
          )
        },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
  }
}
