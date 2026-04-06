package com.zapret2.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.topjohnwu.superuser.Shell
import com.zapret2.app.MainActivity
import com.zapret2.app.R

class NetworkMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "zapret2_network"
        private const val CHANNEL_ID_ZAPRET = "zapret2_status"
        private const val CHANNEL_ID_VPN = "zapret2_vpn_status"
        private const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_ID_ZAPRET = 1002
        const val NOTIFICATION_ID_VPN = 1003
        private const val TAG = "NetworkMonitor"

        const val ACTION_UPDATE_STATUS = "com.zapret2.app.UPDATE_STATUS"
        const val EXTRA_ZAPRET_RUNNING = "zapret_running"
        const val EXTRA_VPN_RUNNING = "vpn_running"
    }

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                handleNetworkChange(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                handleNetworkLost()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                handleNetworkChange(network)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_STATUS -> {
                val zapretRunning = intent.getBooleanExtra(EXTRA_ZAPRET_RUNNING, false)
                val vpnRunning = intent.getBooleanExtra(EXTRA_VPN_RUNNING, false)
                updateStatusNotifications(zapretRunning, vpnRunning)
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createMonitorNotification("Monitoring network..."))
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleNetworkChange(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> switchToWifiMode()
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> switchToMobileMode()
        }
    }

    private fun handleNetworkLost() {
        updateMonitorNotification("No network connection")
        stopAllServices()
    }

    private fun switchToWifiMode() {
        updateMonitorNotification("WiFi — Zapret2 active")
        Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null || true").exec()
        sleep(1000)
        Shell.cmd("su -c zapret2-stop 2>/dev/null || true").exec()
        sleep(500)
        Shell.cmd("su -c zapret2-start").exec()
        updateStatusNotifications(zapretRunning = true, vpnRunning = false)
    }

    private fun switchToMobileMode() {
        updateMonitorNotification("Mobile — VPN active")
        Shell.cmd("su -c zapret2-stop 2>/dev/null || true").exec()
        sleep(1000)
        Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null || true").exec()
        sleep(500)
        Shell.cmd("su -c zapret2-vpn-start").exec()
        updateStatusNotifications(zapretRunning = false, vpnRunning = true)
    }

    private fun stopAllServices() {
        Shell.cmd("su -c zapret2-stop 2>/dev/null || true").exec()
        Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null || true").exec()
        updateStatusNotifications(zapretRunning = false, vpnRunning = false)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Network Monitor", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Auto-switch network monitor"
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_ZAPRET, "Zapret2 Status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows Zapret2 DPI bypass status"
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_VPN, "VPN Status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows VPN connection status"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun pendingMainIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    private fun createMonitorNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zapret2 Auto-Switch")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_network)
            .setContentIntent(pendingMainIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateMonitorNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createMonitorNotification(text))
    }

    fun updateStatusNotifications(zapretRunning: Boolean, vpnRunning: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)

        if (zapretRunning) {
            manager.notify(
                NOTIFICATION_ID_ZAPRET,
                NotificationCompat.Builder(this, CHANNEL_ID_ZAPRET)
                    .setContentTitle("Zapret2 active")
                    .setContentText("DPI bypass is running")
                    .setSmallIcon(R.drawable.ic_shield)
                    .setContentIntent(pendingMainIntent())
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            )
        } else {
            manager.cancel(NOTIFICATION_ID_ZAPRET)
        }

        if (vpnRunning) {
            manager.notify(
                NOTIFICATION_ID_VPN,
                NotificationCompat.Builder(this, CHANNEL_ID_VPN)
                    .setContentTitle("VPN active")
                    .setContentText("VPN tunnel is running")
                    .setSmallIcon(R.drawable.ic_shield)
                    .setContentIntent(pendingMainIntent())
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            )
        } else {
            manager.cancel(NOTIFICATION_ID_VPN)
        }
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
