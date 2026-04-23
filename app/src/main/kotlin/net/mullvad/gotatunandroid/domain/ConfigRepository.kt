package net.mullvad.gotatunandroid.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import androidx.core.content.edit

class ConfigRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeConfig = MutableStateFlow(loadConfig())
    val activeConfig: StateFlow<VpnConfig?> = _activeConfig.asStateFlow()

    fun saveConfig(config: VpnConfig) {
        _activeConfig.value = config
        prefs.edit {
            putString(KEY_CONFIG_NAME, config.name)
                .putString(KEY_CONFIG_CONTENT, WireGuardConfigParser.serialize(config))
        }
    }

    private fun loadConfig(): VpnConfig? {
        val name = prefs.getString(KEY_CONFIG_NAME, null) ?: return null
        val content = prefs.getString(KEY_CONFIG_CONTENT, null) ?: return null
        return try {
            WireGuardConfigParser.parse(content, name)
        } catch (e: Exception) {
            Log.e("ConfigRepository", "Failed to parse config", e)
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "gotatun_config"
        private const val KEY_CONFIG_NAME = "config_name"
        private const val KEY_CONFIG_CONTENT = "config_content"
    }
}


