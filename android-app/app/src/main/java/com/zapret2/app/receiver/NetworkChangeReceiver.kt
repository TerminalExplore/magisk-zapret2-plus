package com.zapret2.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.zapret2.app.service.NetworkMonitorService

class NetworkChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkChange"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) {
            return
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = intent.getParcelableExtra<android.net.Network>(ConnectivityManager.EXTRA_NETWORK)
        @Suppress("DEPRECATION")
        val networkType = intent.getIntExtra(
            ConnectivityManager.EXTRA_NETWORK_TYPE,
            -1
        )
        val noConnectivity = intent.getBooleanExtra(
            ConnectivityManager.EXTRA_NO_CONNECTIVITY,
            false
        )

        if (noConnectivity) {
            Log.d(TAG, "No connectivity")
            return
        }

        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                Log.d(TAG, "WiFi connected")
                handleWifiConnected(context)
            }
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                Log.d(TAG, "Mobile connected")
                handleMobileConnected(context)
            }
        }
    }

    private fun handleWifiConnected(context: Context) {
        Log.d(TAG, "Switching to Zapret2 mode (WiFi)")
        
        // Stop VPN if running
        Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null").exec()
        
        // Small delay for clean switch
        Thread {
            Thread.sleep(1500)
            
            // Stop existing Zapret2
            Shell.cmd("su -c zapret2-stop 2>/dev/null").exec()
            
            Thread.sleep(500)
            
            // Start Zapret2
            Shell.cmd("su -c zapret2-start").exec()
        }.start()
    }

    private fun handleMobileConnected(context: Context) {
        Log.d(TAG, "Switching to VPN mode (Mobile)")
        
        // Stop Zapret2 if running
        Shell.cmd("su -c zapret2-stop 2>/dev/null").exec()
        
        // Small delay for clean switch
        Thread {
            Thread.sleep(1500)
            
            // Stop existing VPN
            Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null").exec()
            
            Thread.sleep(500)
            
            // Start VPN
            Shell.cmd("su -c zapret2-vpn-start").exec()
        }.start()
    }
}
