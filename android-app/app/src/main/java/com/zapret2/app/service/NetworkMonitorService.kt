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
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "NetworkMonitor"
    }

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        
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
        startForeground(NOTIFICATION_ID, createNotification("Monitoring network..."))
        
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
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                switchToWifiMode()
            }
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                switchToMobileMode()
            }
        }
    }

    private fun handleNetworkLost() {
        updateNotification("No network connection")
        stopAllServices()
    }

    private fun switchToWifiMode() {
        updateNotification("WiFi connected - Using Zapret2")
        
        Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null || true").exec()
        sleep(1000)
        Shell.cmd("su -c zapret2-stop 2>/dev/null || true").exec()
        sleep(500)
        Shell.cmd("su -c zapret2-start").exec()
    }

    private fun switchToMobileMode() {
        updateNotification("Mobile connected - Using VPN")
        
        Shell.cmd("su -c zapret2-stop 2>/dev/null || true").exec()
        sleep(1000)
        Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null || true").exec()
        sleep(500)
        Shell.cmd("su -c zapret2-vpn-start").exec()
    }

    private fun stopAllServices() {
        Shell.cmd("su -c zapret2-stop 2>/dev/null || true").exec()
        Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null || true").exec()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors network changes for auto-switch"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zapret2 Auto-Switch")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
