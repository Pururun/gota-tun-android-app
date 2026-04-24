package net.mullvad.gotatunandroid.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _allowRemoteControl = MutableStateFlow(prefs.getBoolean(KEY_REMOTE_CONTROL, false))
    val allowRemoteControl: StateFlow<Boolean> = _allowRemoteControl.asStateFlow()

    fun setAllowRemoteControl(allow: Boolean) {
        _allowRemoteControl.value = allow
        prefs.edit { putBoolean(KEY_REMOTE_CONTROL, allow) }
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_REMOTE_CONTROL = "allow_remote_control"
    }
}

