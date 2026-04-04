package com.zapret2.app.ui.screen

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import com.zapret2.app.ui.components.FluentCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var wifiEnabled by remember { mutableStateOf(false) }
    var mobileEnabled by remember { mutableStateOf(false) }
    var wifiApps by remember { mutableStateOf(setOf<String>()) }
    var mobileApps by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }

    val commonApps = listOf(
        "com.google.android.youtube" to "YouTube",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.discord" to "Discord",
        "com.discord.staff" to "Discord Beta",
        "com.telegram.messenger" to "Telegram",
        "com.whatsapp" to "WhatsApp",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.twitter.android" to "Twitter/X",
        "com.netflix.mediaclient" to "Netflix",
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.googlevoice" to "Google Voice"
    )

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val pm = context.packageManager
            
            val apps = commonApps.mapNotNull { (pkg, name) ->
                try {
                    pm.getApplicationInfo(pkg, 0)
                    AppInfo(pkg, name)
                } catch (e: Exception) {
                    null
                }
            }
            
            installedApps = apps
            
            loadSettings(
                onWifiEnabledLoaded = { wifiEnabled = it },
                onMobileEnabledLoaded = { mobileEnabled = it },
                onWifiAppsLoaded = { wifiApps = it },
                onMobileAppsLoaded = { mobileApps = it }
            )
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("App Filter") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("WiFi") },
                icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Mobile") },
                icon = { Icon(Icons.Default.SignalCellular4Bar, contentDescription = null) }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    val enabled = if (selectedTab == 0) wifiEnabled else mobileEnabled
                    val onEnabledChange: (Boolean) -> Unit = if (selectedTab == 0) {
                        { value: Boolean -> wifiEnabled = value; saveWifiEnabled(value) }
                    } else {
                        { value: Boolean -> mobileEnabled = value; saveMobileEnabled(value) }
                    }

                    FluentCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "App Filter ${if (selectedTab == 0) "WiFi" else "Mobile"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (enabled) "Enabled" else "Disabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (enabled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = onEnabledChange
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = if (selectedTab == 0)
                                    "Only these apps will use Zapret2 on WiFi"
                                else
                                    "Only these apps will use VPN on Mobile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Select Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val selectedApps = if (selectedTab == 0) wifiApps else mobileApps
                
                items(installedApps) { app ->
                    val isSelected = selectedApps.contains(app.packageName)
                    val onToggle = { selected: Boolean ->
                        val newSet = if (selected) {
                            if (selectedTab == 0) wifiApps + app.packageName else mobileApps + app.packageName
                        } else {
                            if (selectedTab == 0) wifiApps - app.packageName else mobileApps - app.packageName
                        }
                        
                        if (selectedTab == 0) {
                            wifiApps = newSet
                            saveWifiApps(newSet)
                        } else {
                            mobileApps = newSet
                            saveMobileApps(newSet)
                        }
                    }

                    AppFilterItem(
                        app = app,
                        isSelected = isSelected,
                        onToggle = onToggle
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AppFilterItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    FluentCard(
        modifier = Modifier.clickable { onToggle(!isSelected) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when {
                        app.packageName.contains("youtube") -> Icons.Default.PlayCircle
                        app.packageName.contains("discord") -> Icons.Default.Chat
                        app.packageName.contains("telegram") -> Icons.Default.Send
                        app.packageName.contains("whatsapp") -> Icons.Default.ChatBubble
                        app.packageName.contains("instagram") -> Icons.Default.PhotoCamera
                        app.packageName.contains("facebook") -> Icons.Default.People
                        app.packageName.contains("twitter") -> Icons.Default.TextFields
                        app.packageName.contains("netflix") -> Icons.Default.Movie
                        app.packageName.contains("spotify") -> Icons.Default.MusicNote
                        else -> Icons.Default.Apps
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle
            )
        }
    }
}

private suspend fun loadSettings(
    onWifiEnabledLoaded: (Boolean) -> Unit,
    onMobileEnabledLoaded: (Boolean) -> Unit,
    onWifiAppsLoaded: (Set<String>) -> Unit,
    onMobileAppsLoaded: (Set<String>) -> Unit
) {
    val result = Shell.cmd(
        "cat /data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()

    if (result.isSuccess) {
        val content = result.out.joinToString("\n")
        
        val wifiEnabledVal = content.contains("WIFI_APP_FILTER=1")
        val mobileEnabledVal = content.contains("MOBILE_APP_FILTER=1")
        
        val wifiAppsRegex = Regex("WIFI_APPS=\"([^\"]*)\"").find(content)
        val mobileAppsRegex = Regex("MOBILE_APPS=\"([^\"]*)\"").find(content)
        
        val wifiAppsVal = wifiAppsRegex?.groupValues?.get(1)
            ?.split(" ")
            ?.filter { app -> app.isNotBlank() }
            ?.toSet() ?: emptySet()
        
        val mobileAppsVal = mobileAppsRegex?.groupValues?.get(1)
            ?.split(" ")
            ?.filter { app -> app.isNotBlank() }
            ?.toSet() ?: emptySet()
        
        onWifiEnabledLoaded(wifiEnabledVal)
        onMobileEnabledLoaded(mobileEnabledVal)
        onWifiAppsLoaded(wifiAppsVal)
        onMobileAppsLoaded(mobileAppsVal)
    }
}

private fun saveWifiEnabled(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/WIFI_APP_FILTER=[01]/WIFI_APP_FILTER=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()
}

private fun saveMobileEnabled(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/MOBILE_APP_FILTER=[01]/MOBILE_APP_FILTER=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()
}

private fun saveWifiApps(apps: Set<String>) {
    val appsStr = apps.joinToString(" ")
    Shell.cmd(
        "sed -i 's/WIFI_APPS=\"[^\"]*\"/WIFI_APPS=\"$appsStr\"/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()
}

private fun saveMobileApps(apps: Set<String>) {
    val appsStr = apps.joinToString(" ")
    Shell.cmd(
        "sed -i 's/MOBILE_APPS=\"[^\"]*\"/MOBILE_APPS=\"$appsStr\"/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()
}
