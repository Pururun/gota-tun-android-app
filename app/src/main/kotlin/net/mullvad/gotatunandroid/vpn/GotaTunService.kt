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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.mullvad.gotatunandroid.MainActivity
import net.mullvad.gotatunandroid.domain.WireGuardConfigParser

class GotaTunService : VpnService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var tunnelInterface: ParcelFileDescriptor? = null
  private val gotaTunWrapper = GotaTunWrapper()

  companion object {
    const val ACTION_CONNECT = "net.mullvad.gotatunandroid.vpn.CONNECT"
    const val ACTION_DISCONNECT = "net.mullvad.gotatunandroid.vpn.DISCONNECT"
    const val EXTRA_CONFIG = "extra_config"
    private const val CHANNEL_ID = "vpn_service_channel"
    private const val NOTIFICATION_ID = 1

    private val _serviceState = MutableStateFlow<VpnState>(VpnState.Idle)
    val serviceState = _serviceState.asStateFlow()
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  @SuppressLint("NewApi")
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_CONNECT -> {
        val config = intent.getStringExtra(EXTRA_CONFIG) ?: ""
        startForeground(NOTIFICATION_ID, createNotification("Connecting..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        startTunnel(config)
      }
      ACTION_DISCONNECT -> {
        stopTunnel()
      }
    }
    return START_STICKY
  }

  private fun startTunnel(config: String) {
    _serviceState.value = VpnState.Connecting
    serviceScope.launch(Dispatchers.IO) {
      try {
        val vpnConfig = WireGuardConfigParser.parse(config)
        val builder = Builder()

        vpnConfig.interfaceConfig.addresses.forEach { cidr ->
          val parts = cidr.split("/")
          val address = parts[0]
          val prefix = parts.getOrNull(1)?.toIntOrNull() ?: 32
          builder.addAddress(address, prefix)
        }

        vpnConfig.peers.forEach { peer ->
          peer.allowedIps.forEach { cidr ->
            val parts = cidr.split("/")
            val address = parts[0]
            val prefix = parts.getOrNull(1)?.toIntOrNull() ?: 32
            builder.addRoute(address, prefix)
          }
        }

        vpnConfig.interfaceConfig.dns.forEach { dns -> builder.addDnsServer(dns) }

        builder.setSession("GotaTun")

        tunnelInterface = builder.establish()

        tunnelInterface?.let { pfd ->
          // Important: We detach the FD to pass ownership to the native library
          val fd = pfd.detachFd()
          val result = gotaTunWrapper.startTunnel(fd, config, this@GotaTunService)
          if (result == 0) {
            Log.d("GotaTunService", "Tunnel established with FD: $fd")
            _serviceState.value = VpnState.Connected
            updateNotification("Connected")
          } else {
            _serviceState.value = VpnState.Error("Native tunnel start failed: $result")
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

  private fun stopTunnel() {
    _serviceState.value = VpnState.Disconnecting
    serviceScope.launch(Dispatchers.IO) {
      try {
        gotaTunWrapper.stopTunnel()
        tunnelInterface?.close()
        tunnelInterface = null
        Log.d("GotaTunService", "Tunnel stopped")
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

  private fun createNotificationChannel() {
    val channel =
        NotificationChannel(CHANNEL_ID, "VPN Service Channel", NotificationManager.IMPORTANCE_LOW)
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
  }

  private fun createNotification(content: String): Notification {
    val pendingIntent =
        Intent(this, MainActivity::class.java).let {
          PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("GotaTun VPN")
        .setContentText(content)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(pendingIntent)
        .build()
  }

  private fun updateNotification(content: String) {
    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, createNotification(content))
  }

  override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
  }
}
