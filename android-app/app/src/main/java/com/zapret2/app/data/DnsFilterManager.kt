package com.zapret2.app.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsFilterManager @Inject constructor() {

    private val zapretDir = "/data/adb/modules/zapret2/zapret2"
    private val dnsFilterScript = "$zapretDir/scripts/dns-filter.sh"
    private val dnsFilterConfig = "$zapretDir/dns-filter.ini"

    data class AppDnsConfig(
        val packageName: String,
        val displayName: String,
        val dnsServer: String?,
        val isEnabled: Boolean,
        val isInstalled: Boolean
    )

    data class DnsServer(
        val ip: String,
        val name: String
    )

    companion object {
        val DNS_SERVERS = listOf(
            DnsServer("1.1.1.1", "Cloudflare"),
            DnsServer("1.0.0.1", "Cloudflare Secondary"),
            DnsServer("8.8.8.8", "Google"),
            DnsServer("8.8.4.4", "Google Secondary"),
            DnsServer("94.140.14.14", "AdGuard"),
            DnsServer("94.140.15.15", "AdGuard Secondary"),
            DnsServer("77.88.8.8", "Yandex"),
            DnsServer("77.88.8.1", "Yandex Secondary"),
            DnsServer("system", "System DNS")
        )

        val PRESET_APPS = listOf(
            PresetApp("com.openai.chatbot", "ChatGPT", "AI assistant"),
            PresetApp("com.google.android.apps.search.assistant.yuni", "Google Gemini", "AI assistant"),
            PresetApp("com.anthropic.claude", "Claude", "AI assistant"),
            PresetApp("com.microsoft.emix", "Microsoft Copilot", "AI assistant"),
            PresetApp("com.google.android.youtube", "YouTube", "Video"),
            PresetApp("com.spotify.music", "Spotify", "Music"),
            PresetApp("com.discord", "Discord", "Messaging")
        )
    }

    data class PresetApp(
        val packageName: String,
        val displayName: String,
        val category: String
    )

    suspend fun getInstalledApps(): List<AppDnsConfig> = withContext(Dispatchers.IO) {
        val installedPackages = getInstalledPackages()
        
        PRESET_APPS.mapNotNull { preset ->
            val isInstalled = preset.packageName in installedPackages
            val existingConfig = getExistingConfig(preset.packageName)
            
            AppDnsConfig(
                packageName = preset.packageName,
                displayName = preset.displayName,
                dnsServer = existingConfig?.first,
                isEnabled = existingConfig?.second ?: false,
                isInstalled = isInstalled
            )
        }
    }

    suspend fun getAppUid(packageName: String): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("dumpsys package $packageName 2>/dev/null | grep 'userId=' | head -1").exec()
        val output = result.out.firstOrNull() ?: return@withContext null
        
        Regex("userId=(\\d+)").find(output)?.groupValues?.get(1)
    }

    suspend fun applyDnsFilter(configs: List<AppDnsConfig>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Generate config file
            val configContent = buildString {
                appendLine("# Per-App DNS Filter Configuration")
                appendLine("# Format: [app:package.name] = DNS server IP")
                appendLine()
                
                for (config in configs.filter { it.isEnabled && it.dnsServer != null && it.dnsServer != "system" }) {
                    appendLine("[app:${config.packageName}]")
                    appendLine("# ${config.displayName}")
                    appendLine("${config.dnsServer}")
                    appendLine()
                }
            }
            
            // Write config
            Shell.cmd("cat > $dnsFilterConfig << 'EOFCONFIG'\n$configContent\nEOFCONFIG").exec()
            
            // Apply rules
            val result = Shell.cmd("sh $dnsFilterScript start").exec()
            
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.err.joinToString("\n")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun flushDnsFilter(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("sh $dnsFilterScript stop").exec()
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.err.joinToString("\n")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDnsFilterStatus(): String = withContext(Dispatchers.IO) {
        val result = Shell.cmd("sh $dnsFilterScript status").exec()
        result.out.joinToString("\n")
    }

    private fun getInstalledPackages(): Set<String> {
        val result = Shell.cmd("pm list packages").exec()
        result.out.mapNotNull { line ->
            line.removePrefix("package:").trim().takeIf { it.isNotEmpty() }
        }.toSet()
    }

    private fun getExistingConfig(packageName: String): Pair<String, Boolean>? {
        val result = Shell.cmd("grep -A2 '\\[app:$packageName\\]' $dnsFilterConfig 2>/dev/null").exec()
        val lines = result.out.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("[") }
        
        if (lines.isNotEmpty()) {
            return Pair(lines.first().trim(), true)
        }
        return null
    }
}
