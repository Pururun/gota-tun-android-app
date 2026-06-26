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
import kotlin.time.Duration.Companion.seconds
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
import net.mullvad.gotatunandroid.domain.model.VpnConfig
import net.mullvad.gotatunandroid.ffi.SocketProtector
import net.mullvad.gotatunandroid.ffi.getStats
import net.mullvad.gotatunandroid.ffi.startTunnel
import net.mullvad.gotatunandroid.ffi.stopTunnel as stopTunnelRust

data class TunnelStats(val lastHandshakeEpochSecs: Long, val rxBytes: Long, val txBytes: Long)

@Suppress("TooManyFunctions")
class GotaTunService : VpnService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var tunnelInterface: ParcelFileDescriptor? = null
  private var statsJob: Job? = null
  private val tunnelMutex = Mutex()

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
    private const val HANDSHAKE_TIMEOUT_MS = 30_000L
    private const val DEFAULT_PREFIX_LENGTH = 32
    private val STATS_POLL_INTERVAL = 2.seconds
    private val HANDSHAKE_POLL_INTERVAL = 1.seconds
    private const val STATS_PARTS_COUNT = 3
    private const val BYTES_PER_MB = 1_048_576L
    private const val BYTES_PER_KB = 1_024L

    private val _serviceState = MutableStateFlow<VpnState>(VpnState.Idle)
    val serviceState = _serviceState.asStateFlow()

    private val _tunnelStats = MutableStateFlow<TunnelStats?>(null)
    val tunnelStats = _tunnelStats.asStateFlow()
  }

  override fun onCreate() {
    super.onCreate()
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

  @Suppress("TooGenericExceptionCaught")
  private fun startTunnel(config: String, splitExtra: String) {
    _serviceState.value = VpnState.Connecting
    serviceScope.launch(Dispatchers.IO) {
      tunnelMutex.withLock {
        try {
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
          applyNetworkConfig(builder, vpnConfig)
          val splitTunneling = applySplitTunneling(builder, splitExtra)
          tunnelInterface = builder.establish()

          tunnelInterface?.let { pfd ->
            val fd = pfd.detachFd()
            tunnelInterface = null
            val result = startTunnel(fd, config, socketProtector)
            if (result) {
              startHandshakeWait(vpnConfig.copy(splitTunneling = splitTunneling))
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

  private fun applyNetworkConfig(builder: Builder, vpnConfig: VpnConfig) {
    vpnConfig.interfaceConfig.addresses.forEach { cidr ->
      val parts = cidr.split("/")
      builder.addAddress(
          parts[0],
          parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PREFIX_LENGTH,
      )
    }
    vpnConfig.peers.forEach { peer ->
      peer.allowedIps.forEach { cidr ->
        val parts = cidr.split("/")
        builder.addRoute(
            parts[0],
            parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PREFIX_LENGTH,
        )
      }
    }
    vpnConfig.interfaceConfig.dns.forEach { builder.addDnsServer(it) }
    vpnConfig.interfaceConfig.mtu?.let { builder.setMtu(it) }
    builder.setSession("GotaTun")
  }

  private fun applySplitTunneling(builder: Builder, splitExtra: String): SplitTunnelingConfig {
    val disabledConfig = SplitTunnelingConfig(SplitTunnelingMode.DISABLED, emptyList())
    val colon = splitExtra.indexOf(':')
    val mode = if (splitExtra != "DISABLED" && colon > 0) splitExtra.substring(0, colon) else null
    return if (mode != null) {
      val packages = splitExtra.substring(colon + 1).split("|").filter { it.isNotEmpty() }
      packages.forEach { pkg ->
        runCatching {
          if (mode == "EXCLUDE") builder.addDisallowedApplication(pkg)
          else builder.addAllowedApplication(pkg)
        }
      }
      SplitTunnelingConfig(
          if (mode == "EXCLUDE") SplitTunnelingMode.EXCLUDE else SplitTunnelingMode.INCLUDE_ONLY,
          packageNames = packages,
      )
    } else {
      disabledConfig
    }
  }

  private fun startStatsPolling(configName: String) {
    statsJob?.cancel()
    statsJob =
        serviceScope.launch(Dispatchers.IO) {
          var prevRx = 0L
          var prevTx = 0L
          while (true) {
            delay(STATS_POLL_INTERVAL)
            val raw = runCatching { getStats() }.getOrNull()
            if (!raw.isNullOrBlank()) {
              val parts = raw.split("|")
              if (parts.size == STATS_PARTS_COUNT) {
                val handshake = parts[0].toLongOrNull() ?: 0L
                val rx = parts[1].toLongOrNull() ?: 0L
                val tx = parts[2].toLongOrNull() ?: 0L
                _tunnelStats.value = TunnelStats(handshake, rx, tx)
                val pollIntervalSecs = STATS_POLL_INTERVAL.inWholeSeconds
                val rxRate = ((rx - prevRx) / pollIntervalSecs).coerceAtLeast(0)
                val txRate = ((tx - prevTx) / pollIntervalSecs).coerceAtLeast(0)
                prevRx = rx
                prevTx = tx
                updateNotification(
                    content = "↑ ${formatBytes(txRate)}/s  ↓ ${formatBytes(rxRate)}/s",
                    connected = true,
                    configName = configName,
                )
              }
            }
          }
        }
  }

  private fun startHandshakeWait(vpnConfig: VpnConfig) {
    statsJob?.cancel()
    statsJob =
        serviceScope.launch(Dispatchers.IO) {
          val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
          while (System.currentTimeMillis() < deadline) {
            delay(HANDSHAKE_POLL_INTERVAL)
            val raw = runCatching { getStats() }.getOrNull()
            if (raw != null && raw.isNotBlank()) {
              val parts = raw.split("|")
              if (parts.size == STATS_PARTS_COUNT && (parts[0].toLongOrNull() ?: 0L) > 0L) {
                transitionToConnected(vpnConfig)
                return@launch
              }
            }
          }
          if (_serviceState.value is VpnState.Connecting) {
            transitionToConnected(vpnConfig)
          }
        }
  }

  private fun transitionToConnected(vpnConfig: VpnConfig) {
    _serviceState.value = VpnState.Connected(vpnConfig)
    updateNotification("Connected", connected = true, configName = vpnConfig.name)
    startStatsPolling(vpnConfig.name)
  }

  @Suppress("TooGenericExceptionCaught")
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
        bytes >= BYTES_PER_MB -> "${"%.1f".format(bytes / BYTES_PER_MB.toDouble())} MB"
        bytes >= BYTES_PER_KB -> "${"%.0f".format(bytes / BYTES_PER_KB.toDouble())} KB"
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

  private fun createNotification(
      content: String,
      connected: Boolean,
      configName: String? = null,
  ): Notification {
    val openAppIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
    val title = if (configName != null) "GotaTun – $configName" else "GotaTun VPN"
    val builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent)
            .setOngoing(connected)
    if (connected) {
      builder.addAction(android.R.drawable.ic_delete, "Disconnect", disconnectPendingIntent())
    }
    return builder.build()
  }

  private fun updateNotification(
      content: String,
      connected: Boolean,
      configName: String? = null,
  ) {
    getSystemService(NotificationManager::class.java)
        .notify(NOTIFICATION_ID, createNotification(content, connected, configName))
  }

  override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
  }
}
