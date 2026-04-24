package net.mullvad.gotatunandroid.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import dev.zacsweers.metro.createGraphFactory
import net.mullvad.gotatunandroid.di.AppGraph

/**
 * Receives automation intents for tunnel control from third-party apps (e.g. Tasker).
 *
 * Requires the permission:  net.mullvad.gotatunandroid.CONTROL_VPN
 * AND the in-app "Allow remote control" toggle must be enabled.
 *
 * Actions:
 *   com.gotatun.android.action.SET_TUNNEL_UP   — extra: tunnel_name (String)
 *   com.gotatun.android.action.SET_TUNNEL_DOWN — extra: tunnel_name (String)
 */
class TunnelBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appGraph = createGraphFactory<AppGraph.Factory>().create(context)

        // Gate on the in-app setting
        if (!appGraph.appSettingsRepository.allowRemoteControl.value) return

        val tunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME) ?: return

        when (intent.action) {
            ACTION_TUNNEL_UP -> {
                // VPN permission must already be granted — we can't show the dialog from a receiver
                if (VpnService.prepare(context) != null) return

                val config = appGraph.configRepository.allConfigs.value
                    .firstOrNull { it.name == tunnelName } ?: return
                appGraph.vpnController.connect(config)
            }
            ACTION_TUNNEL_DOWN -> {
                appGraph.vpnController.disconnect()
            }
        }
    }

    companion object {
        const val ACTION_TUNNEL_UP = "com.gotatun.android.action.SET_TUNNEL_UP"
        const val ACTION_TUNNEL_DOWN = "com.gotatun.android.action.SET_TUNNEL_DOWN"
        const val EXTRA_TUNNEL_NAME = "tunnel_name"
    }
}

