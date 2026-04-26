package net.mullvad.gotatunandroid.vpn

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mullvad.gotatunandroid.MainActivity
import net.mullvad.gotatunandroid.domain.WireGuardConfigParser
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingConfig
import net.mullvad.gotatunandroid.domain.model.SplitTunnelingMode
import net.mullvad.gotatunandroid.ffi.SocketProtector
import net.mullvad.gotatunandroid.ffi.getStats
import net.mullvad.gotatunandroid.ffi.startTunnel
import net.mullvad.gotatunandroid.ffi.stopTunnel as stopTunnelRust

data class TunnelStats(val lastHandshakeEpochSecs: Long, val rxBytes: Long, val txBytes: Long)

class GotaTunService : VpnService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var tunnelInterface: ParcelFileDescriptor? = null
  private var statsJob: Job? = null
  /** Serialises all start/stop operations so they never run concurrently. */
  private val tunnelMutex = Mutex()

  /** SocketProtector implementation — delegates to VpnService.protect(). */
  private val socketProtector =
      object : SocketProtector {
        override fun protect(fd: Int): Boolean = this@GotaTunService.protect(fd)
      }

  companion object {
    const val ACTION_CONNECT = "net.mullvad.gotatunandroid.vpn.CONNECT"
    const val ACTION_DISCONNECT = "net.mullvad.gotatunandroid.vpn.DISCONNECT"
    const val EXTRA_CONFIG = "extra_config"
    const val EXTRA_SPLIT_TUNNELING = "extra_split_tunneling"
    private const val CHANNEL_ID = "vpn_service_channel"
    private const val NOTIFICATION_ID = 1

    private val _serviceState = MutableStateFlow<VpnState>(VpnState.Idle)
    val serviceState = _serviceState.asStateFlow()

    private val _tunnelStats = MutableStateFlow<TunnelStats?>(null)
    val tunnelStats = _tunnelStats.asStateFlow()
  }

  override fun onCreate() {
    super.onCreate()
    // Reset potentially stale state from a previous service instance (START_STICKY restart).
    _serviceState.value = VpnState.Idle
    _tunnelStats.value = null
    createNotificationChannel()
  }

  @SuppressLint("NewApi")
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_CONNECT -> {
        val config = intent.getStringExtra(EXTRA_CONFIG) ?: ""
        val splitExtra = intent.getStringExtra(EXTRA_SPLIT_TUNNELING) ?: "DISABLED"
        val notification = createNotification("Connecting...", connected = false)
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        startTunnel(config, splitExtra)
      }
      ACTION_DISCONNECT -> stopTunnel()
    }
    return START_STICKY
  }

  private fun startTunnel(config: String, splitExtra: String) {
    _serviceState.value = VpnState.Connecting
    serviceScope.launch(Dispatchers.IO) {
      tunnelMutex.withLock {
        try {
          // Stop any previously running tunnel before starting a new one.
          // This is the key fix for config-switch / reconnect failures.
          if (_serviceState.value !is VpnState.Idle) {
            statsJob?.cancel()
            statsJob = null
            runCatching { stopTunnelRust() }
            tunnelInterface?.close()
            tunnelInterface = null
          }
          _serviceState.value = VpnState.Connecting

          val vpnConfig = WireGuardConfigParser.parse(config)
          val builder = Builder()

        vpnConfig.interfaceConfig.addresses.forEach { cidr ->
          val parts = cidr.split("/")
          builder.addAddress(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 32)
        }
        vpnConfig.peers.forEach { peer ->
          peer.allowedIps.forEach { cidr ->
            val parts = cidr.split("/")
            builder.addRoute(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 32)
          }
        }
        vpnConfig.interfaceConfig.dns.forEach { builder.addDnsServer(it) }
        vpnConfig.interfaceConfig.mtu?.let { builder.setMtu(it) }

        // Apply split tunneling
        var splitTunneling = SplitTunnelingConfig(SplitTunnelingMode.DISABLED, emptyList())

        if (splitExtra != "DISABLED") {
          val colon = splitExtra.indexOf(':')
          if (colon > 0) {
            val mode = splitExtra.substring(0, colon)
            val packages = splitExtra.substring(colon + 1).split("|").filter { it.isNotEmpty() }
            packages.forEach { pkg ->
              runCatching {
                if (mode == "EXCLUDE") builder.addDisallowedApplication(pkg)
                else builder.addAllowedApplication(pkg)
              }
            }
            splitTunneling =
                SplitTunnelingConfig(
                    if (mode == "EXCLUDE") SplitTunnelingMode.EXCLUDE
                    else SplitTunnelingMode.INCLUDE_ONLY,
                    packageNames = packages,
                )
          }
        }

        builder.setSession("GotaTun")
          tunnelInterface = builder.establish()

          tunnelInterface?.let { pfd ->
            val fd = pfd.detachFd()
            // After detachFd() the PFD no longer owns the fd; clear the field so
            // stopTunnel() doesn't try to close an already-detached descriptor.
            tunnelInterface = null
            val result = startTunnel(fd, config, socketProtector)
            if (result) {
              _serviceState.value =
                  VpnState.Connected(vpnConfig.copy(splitTunneling = splitTunneling))
              updateNotification("Connected", connected = true)
              startStatsPolling()
            } else {
              _serviceState.value = VpnState.Error("Native tunnel start failed")
              stopForeground(STOP_FOREGROUND_REMOVE)
              stopSelf()
            }
          }
              ?: run {
                _serviceState.value = VpnState.Error("Failed to establish tunnel interface")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
              }
        } catch (e: Exception) {
          Log.e("GotaTunService", "Error starting tunnel", e)
          _serviceState.value = VpnState.Error(e.message ?: "Unknown error")
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        }
      }
    }
  }

  private fun startStatsPolling() {
    statsJob?.cancel()
    statsJob =
        serviceScope.launch(Dispatchers.IO) {
          var prevRx = 0L
          var prevTx = 0L
          while (true) {
            delay(2_000L)
            val raw = runCatching { getStats() }.getOrNull() ?: continue
            if (raw.isBlank()) continue
            val parts = raw.split("|")
            if (parts.size != 3) continue
            val handshake = parts[0].toLongOrNull() ?: 0L
            val rx = parts[1].toLongOrNull() ?: 0L
            val tx = parts[2].toLongOrNull() ?: 0L
            _tunnelStats.value = TunnelStats(handshake, rx, tx)

            val rxRate = ((rx - prevRx) / 2).coerceAtLeast(0)
            val txRate = ((tx - prevTx) / 2).coerceAtLeast(0)
            prevRx = rx
            prevTx = tx
            updateNotification(
                "↑ ${formatBytes(txRate)}/s  ↓ ${formatBytes(rxRate)}/s",
                connected = true,
            )
          }
        }
  }

  private fun stopTunnel() {
    statsJob?.cancel()
    statsJob = null
    _tunnelStats.value = null
    _serviceState.value = VpnState.Disconnecting
    serviceScope.launch(Dispatchers.IO) {
      tunnelMutex.withLock {
        try {
          stopTunnelRust()
          tunnelInterface?.close()
          tunnelInterface = null
          _serviceState.value = VpnState.Idle
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        } catch (e: Exception) {
          Log.e("GotaTunService", "Error stopping tunnel", e)
          _serviceState.value = VpnState.Error(e.message ?: "Unknown error")
          stopForeground(STOP_FOREGROUND_REMOVE)
          stopSelf()
        }
      }
    }
  }

  private fun formatBytes(bytes: Long): String =
      when {
        bytes >= 1_048_576 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
        bytes >= 1_024 -> "${"%.0f".format(bytes / 1_024.0)} KB"
        else -> "$bytes B"
      }

  private fun createNotificationChannel() {
    val channel =
        NotificationChannel(CHANNEL_ID, "VPN Service Channel", NotificationManager.IMPORTANCE_LOW)
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun disconnectPendingIntent(): PendingIntent {
    val intent = Intent(this, GotaTunService::class.java).apply { action = ACTION_DISCONNECT }
    return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
  }

  private fun createNotification(content: String, connected: Boolean): Notification {
    val openAppIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
    val builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GotaTun VPN")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent)
            .setOngoing(connected)
    if (connected) {
      builder.addAction(android.R.drawable.ic_delete, "Disconnect", disconnectPendingIntent())
    }
    return builder.build()
  }

  private fun updateNotification(content: String, connected: Boolean) {
    getSystemService(NotificationManager::class.java)
        .notify(NOTIFICATION_ID, createNotification(content, connected))
  }

  override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
  }
}
