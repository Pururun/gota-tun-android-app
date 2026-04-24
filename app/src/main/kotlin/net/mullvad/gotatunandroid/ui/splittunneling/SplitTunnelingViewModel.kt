package net.mullvad.gotatunandroid.ui.splittunneling

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mullvad.gotatunandroid.domain.ConfigRepository
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingConfig
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingMode
import androidx.core.graphics.createBitmap

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
    val isSystem: Boolean
)

data class SplitTunnelingUiState(
    val mode: SplitTunnelingMode = SplitTunnelingMode.DISABLED,
    val apps: List<AppItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = true
)

class SplitTunnelingViewModel(
    private val configId: String,
    private val configRepository: ConfigRepository,
    context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SplitTunnelingUiState())
    val state: StateFlow<SplitTunnelingUiState> = _state.asStateFlow()

    private val appContext = context.applicationContext

    init {
        val config = configRepository.allConfigs.value.find { it.id == configId }
        val st = config?.splitTunneling ?: SplitTunnelingConfig()
        _state.value = _state.value.copy(
            mode = st.mode,
            selected = st.packageNames.toSet()
        )
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = appContext.packageManager

            // Packages that declare a VPN service — never shown in split tunneling
            val vpnPackages = pm.queryIntentServices(
                Intent("android.net.VpnService"), 0
            ).map { it.serviceInfo.packageName }.toSet()

            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { info ->
                    info.packageName != appContext.packageName &&
                        info.packageName !in vpnPackages &&
                        // Only show apps that can actually use the network
                        pm.checkPermission(
                            android.Manifest.permission.INTERNET,
                            info.packageName
                        ) == PackageManager.PERMISSION_GRANTED
                }
                .map { info ->
                    AppItem(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info).toBitmap(),
                        // A "system" app is one that has no launcher entry
                        isSystem = !isLaunchable(pm, info.packageName)
                    )
                }
                .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))

            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(apps = apps, isLoading = false)
            }
        }
    }

    /** An app is considered user-visible if it has a standard or leanback launcher entry. */
    private fun isLaunchable(pm: PackageManager, packageName: String): Boolean =
        pm.getLaunchIntentForPackage(packageName) != null ||
            pm.getLeanbackLaunchIntentForPackage(packageName) != null

    fun setMode(mode: SplitTunnelingMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun toggleApp(packageName: String) {
        val current = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (packageName in current) current - packageName else current + packageName
        )
    }

    fun toggleShowSystemApps() {
        _state.value = _state.value.copy(showSystemApps = !_state.value.showSystemApps)
    }

    fun save() {
        val s = _state.value
        val config = configRepository.allConfigs.value.find { it.id == configId } ?: return
        val updated = config.copy(
            splitTunneling = SplitTunnelingConfig(
                mode = s.mode,
                packageNames = s.selected.toList()
            )
        )
        configRepository.saveConfig(updated)
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val bmp = createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1))
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bmp
    }
}

