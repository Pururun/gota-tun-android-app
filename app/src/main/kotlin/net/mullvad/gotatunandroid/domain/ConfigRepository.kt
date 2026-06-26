package net.mullvad.gotatunandroid.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingConfig
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingMode
import net.mullvad.gotatunandroid.domain.model.VpnConfig

class ConfigRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configs = mutableListOf<VpnConfig>()

    private val _allConfigs = MutableStateFlow<List<VpnConfig>>(emptyList())
    val allConfigs: StateFlow<List<VpnConfig>> = _allConfigs.asStateFlow()

    private val _activeConfig = MutableStateFlow<VpnConfig?>(null)
    val activeConfig: StateFlow<VpnConfig?> = _activeConfig.asStateFlow()

    init {
        loadAll()
    }

    fun saveConfig(config: VpnConfig) {
        val idx = _configs.indexOfFirst { it.id == config.id }
        if (idx >= 0) _configs[idx] = config else _configs.add(config)
        _allConfigs.value = _configs.toList()
        persistConfig(config)
        persistSplitTunneling(config)
        persistIds()
        if (_activeConfig.value?.id == config.id) {
            _activeConfig.value = config
        }
    }

    fun deleteConfig(id: String) {
        _configs.removeIf { it.id == id }
        _allConfigs.value = _configs.toList()
        prefs.edit {
            remove(nameKey(id))
            remove(contentKey(id))
        }
        persistIds()
        if (_activeConfig.value?.id == id) {
            val next = _configs.firstOrNull()
            _activeConfig.value = next
            prefs.edit { putString(KEY_ACTIVE_ID, next?.id) }
        }
    }

    fun setActiveConfig(id: String) {
        val config = _configs.find { it.id == id }
        _activeConfig.value = config
        prefs.edit { putString(KEY_ACTIVE_ID, id) }
    }

    private fun loadAll() {
        val ids = prefs.getString(KEY_IDS, "")
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val configs = ids.mapNotNull { loadConfigById(it) }
        _configs.clear()
        _configs.addAll(configs)
        _allConfigs.value = configs.toList()

        val activeId = prefs.getString(KEY_ACTIVE_ID, null)
        _activeConfig.value = if (activeId != null) {
            configs.find { it.id == activeId }
        } else {
            configs.firstOrNull()
        }
    }

    private fun loadConfigById(id: String): VpnConfig? {
        val name = prefs.getString(nameKey(id), null)
        val content = prefs.getString(contentKey(id), null)
        return if (name != null && content != null) {
            runCatching {
                val split = loadSplitTunneling(id)
                WireGuardConfigParser.parse(content, name).copy(id = id, splitTunneling = split)
            }.onFailure {
                Log.e("ConfigRepository", "Failed to parse config $id", it)
            }.getOrNull()
        } else {
            null
        }
    }

    private fun persistConfig(config: VpnConfig) {
        prefs.edit {
            putString(nameKey(config.id), config.name)
            putString(contentKey(config.id), WireGuardConfigParser.serialize(config))
        }
    }

    private fun persistSplitTunneling(config: VpnConfig) {
        val st = config.splitTunneling
        val value = if (st.mode == SplitTunnelingMode.DISABLED) {
            "DISABLED"
        } else {
            "${st.mode.name}:${st.packageNames.joinToString("|")}"
        }
        prefs.edit { putString(splitKey(config.id), value) }
    }

    private fun loadSplitTunneling(id: String): SplitTunnelingConfig {
        val defaultConfig = SplitTunnelingConfig()
        val raw = prefs.getString(splitKey(id), "DISABLED") ?: "DISABLED"
        val colonIdx = raw.indexOf(':')
        val mode = if (raw != "DISABLED" && colonIdx >= 0) {
            runCatching { SplitTunnelingMode.valueOf(raw.substring(0, colonIdx)) }.getOrNull()
        } else {
            null
        }
        return if (mode != null) {
            val packages = raw.substring(colonIdx + 1).split("|").filter { it.isNotEmpty() }
            SplitTunnelingConfig(mode = mode, packageNames = packages)
        } else {
            defaultConfig
        }
    }

    private fun persistIds() {
        prefs.edit { putString(KEY_IDS, _configs.joinToString(",") { it.id }) }
    }

    companion object {
        private const val PREFS_NAME = "gotatun_configs"
        private const val KEY_IDS = "config_ids"
        private const val KEY_ACTIVE_ID = "active_config_id"
        private fun nameKey(id: String) = "name_$id"
        private fun contentKey(id: String) = "content_$id"
        private fun splitKey(id: String) = "split_$id"
    }
}
