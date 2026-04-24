package net.mullvad.gotatunandroid.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.zacsweers.metro.createGraphFactory
import net.mullvad.gotatunandroid.MainActivity
import net.mullvad.gotatunandroid.di.AppGraph

/**
 * Quick Settings tile to toggle the active VPN tunnel from the notification shade.
 * If VPN permission has not been granted the app is opened instead.
 */
class TunnelTileService : TileService() {

    private val appGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile(GotaTunService.serviceState.value)
    }

    override fun onClick() {
        super.onClick()
        when (GotaTunService.serviceState.value) {
            is VpnState.Connected, is VpnState.Connecting -> {
                appGraph.vpnController.disconnect()
                updateTile(VpnState.Disconnecting)
            }
            else -> {
                if (VpnService.prepare(applicationContext) != null) {
                    // Permission not granted — open the app
                    openApp()
                    return
                }
                val config = appGraph.configRepository.activeConfig.value
                if (config == null) {
                    openApp()
                    return
                }
                appGraph.vpnController.connect(config)
                updateTile(VpnState.Connecting)
            }
        }
    }

    private fun updateTile(state: VpnState) {
        qsTile?.apply {
            this.state = when (state) {
                is VpnState.Connected -> Tile.STATE_ACTIVE
                is VpnState.Connecting, is VpnState.Disconnecting -> Tile.STATE_UNAVAILABLE
                else -> Tile.STATE_INACTIVE
            }
            label = when (state) {
                is VpnState.Connected -> "GotaTun: On"
                is VpnState.Connecting -> "GotaTun: Connecting…"
                is VpnState.Disconnecting -> "GotaTun: Disconnecting…"
                else -> "GotaTun: Off"
            }
            updateTile()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(
                        this, 0, intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                // startActivity without collapsing; tile closes when the activity comes to front
                startActivity(intent)
            }
        }
    }
}


