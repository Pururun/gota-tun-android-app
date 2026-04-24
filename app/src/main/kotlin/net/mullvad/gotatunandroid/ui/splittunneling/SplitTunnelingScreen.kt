package net.mullvad.gotatunandroid.ui.splittunneling

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingScreen(
    state: SplitTunnelingUiState,
    onBack: () -> Unit,
    onSetMode: (SplitTunnelingMode) -> Unit,
    onToggleApp: (String) -> Unit,
    onToggleShowSystemApps: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("Split Tunneling", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { onSave(); onBack() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->

        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        // Only user-installed apps when the toggle is off; all apps when on
        val visibleApps = remember(state.apps, state.showSystemApps) {
            if (state.showSystemApps) state.apps else state.apps.filter { !it.isSystem }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Mode selector ─────────────────────────────────────────────────
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SplitTunnelingMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = state.mode == mode,
                                onClick = { onSetMode(mode) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when (mode) {
                                        SplitTunnelingMode.DISABLED -> "Disabled"
                                        SplitTunnelingMode.EXCLUDE -> "Exclude selected apps"
                                        SplitTunnelingMode.INCLUDE_ONLY -> "Include only selected apps"
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = when (mode) {
                                        SplitTunnelingMode.DISABLED -> "All traffic goes through the VPN"
                                        SplitTunnelingMode.EXCLUDE -> "Selected apps bypass the VPN"
                                        SplitTunnelingMode.INCLUDE_ONLY -> "Only selected apps use the VPN"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

            if (state.mode != SplitTunnelingMode.DISABLED) {
                // ── Apps header with system-apps toggle ───────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Applications  ·  ${state.selected.size} selected",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${visibleApps.size} shown · VPN apps hidden",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "System",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Switch(
                                checked = state.showSystemApps,
                                onCheckedChange = { onToggleShowSystemApps() }
                            )
                        }
                    }
                }

                // ── App list ──────────────────────────────────────────────────
                items(visibleApps, key = { it.packageName }) { app ->
                    ListItem(
                        leadingContent = {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        },
                        headlineContent = {
                            Text(app.label, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Checkbox(
                                checked = app.packageName in state.selected,
                                onCheckedChange = { onToggleApp(app.packageName) }
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
