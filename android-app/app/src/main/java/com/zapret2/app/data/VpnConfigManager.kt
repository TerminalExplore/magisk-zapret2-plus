package com.zapret2.app.data

import android.util.Base64
import com.topjohnwu.superuser.Shell
import com.zapret2.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class VpnConfigManager {

    companion object {
        private const val MODULE_DIR = "/data/adb/modules/zapret2"
        private const val ZAPRET_DIR = "$MODULE_DIR/zapret2"
        private const val VPN_ENV = "$ZAPRET_DIR/vpn-config.env"
        private const val VPN_CONFIG = "$ZAPRET_DIR/vpn-config.json"
        private const val XRAY_CONFIG = "$ZAPRET_DIR/xray-config.json"
        private const val VPN_PID = "$ZAPRET_DIR/xray.pid"
        private const val VPN_START_SCRIPT = "$ZAPRET_DIR/scripts/vpn-start.sh"
        private const val SUBSCRIPTION_PARSER = "$ZAPRET_DIR/scripts/subscription-parser.sh"
        private const val VPN_STOP_SCRIPT = "$ZAPRET_DIR/scripts/vpn-stop.sh"
        private const val VPN_LOG = "/data/local/tmp/zapret2-vpn.log"
        private const val SUBSCRIPTION_LOG = "/data/local/tmp/zapret2-subscription.log"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
    }

    data class OperationResult(
        val success: Boolean,
        val message: String,
        val details: String = ""
    )

    data class VpnSettingsSnapshot(
        val vpnEnabled: Boolean,
        val vpnAutostart: Boolean,
        val killSwitch: Boolean,
        val autoSelectFastest: Boolean,
        val subscriptionUrl: String,
        val selectedServerUri: String?,
        val pingMethod: String,
        val pingTimeout: Int
    )

    data class VpnRuntimeStatus(
        val running: Boolean,
        val summary: String,
        val detail: String,
        val vpnEnabled: Boolean,
        val configReady: Boolean,
        val selectedServerUri: String?,
        val subscriptionUrl: String?
    )

    data class LogSnapshot(
        val vpnLog: String,
        val subscriptionLog: String
    )

    private data class FetchResult(
        val success: Boolean,
        val content: String? = null,
        val message: String
    )

    suspend fun loadSettings(): VpnSettingsSnapshot = withContext(Dispatchers.IO) {
        VpnSettingsSnapshot(
            vpnEnabled = readBoolValue("VPN_ENABLED"),
            vpnAutostart = readBoolValue("VPN_AUTOSTART", default = true),
            killSwitch = readBoolValue("KILL_SWITCH"),
            autoSelectFastest = readBoolValue("AUTO_SELECT_SERVER"),
            subscriptionUrl = readEnvValue("VPN_SUBSCRIPTION_URL").orEmpty(),
            selectedServerUri = readEnvValue("VPN_SELECTED_URI")?.takeIf { it.isNotBlank() },
            pingMethod = readEnvValue("PING_METHOD")?.ifBlank { null } ?: "tcp",
            pingTimeout = readEnvValue("PING_TIMEOUT")?.toIntOrNull()?.coerceIn(1, 10) ?: 3
        )
    }

    suspend fun setVpnEnabled(enabled: Boolean): Boolean {
        return writeBoolValue("VPN_ENABLED", enabled)
    }

    suspend fun setVpnAutostart(enabled: Boolean): Boolean {
        return writeBoolValue("VPN_AUTOSTART", enabled)
    }

    suspend fun setKillSwitch(enabled: Boolean): Boolean {
        return writeBoolValue("KILL_SWITCH", enabled)
    }

    suspend fun setPingMethod(method: String): Boolean {
        return writeEnvValue("PING_METHOD", method.trim().ifBlank { "tcp" })
    }

    suspend fun setPingTimeout(timeout: Int): Boolean {
        return writeUnquotedEnvValue("PING_TIMEOUT", timeout.coerceIn(1, 10).toString())
    }

    suspend fun setAutoSelectFastest(enabled: Boolean): Boolean {
        return writeBoolValue("AUTO_SELECT_SERVER", enabled)
    }

    suspend fun saveSubscriptionUrl(url: String): Boolean {
        return writeEnvValue("VPN_SUBSCRIPTION_URL", url.trim())
    }

    suspend fun getSelectedServerUri(): String? {
        return readEnvValue("VPN_SELECTED_URI")?.takeIf { it.isNotBlank() }
    }

    suspend fun importSubscription(url: String): OperationResult = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            return@withContext OperationResult(false, "Enter a subscription URL")
        }

        val fetchResult = fetchUrl(normalizedUrl)
        if (!fetchResult.success || fetchResult.content.isNullOrBlank()) {
            return@withContext OperationResult(false, fetchResult.message)
        }

        val content = fetchResult.content
        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        return@withContext try {
            val parserResult = Shell.cmd(
                "sh ${shellQuote(SUBSCRIPTION_PARSER)} import-b64 ${shellQuote(encoded)}"
            ).exec()
            val imported = parserResult.isSuccess

            if (imported) {
                extractFirstSupportedUri(content)?.let { writeEnvValue("VPN_SELECTED_URI", it) }
                OperationResult(true, "Subscription imported")
            } else {
                OperationResult(false, readLastRelevantLine(SUBSCRIPTION_LOG, parserResult.err, "Subscription import failed"))
            }
        } catch (_: Exception) {
            OperationResult(false, "Subscription import crashed")
        }
    }

    suspend fun applyConfigInput(input: String): OperationResult = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return@withContext OperationResult(false, "Config is empty")
        }

        return@withContext when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                if (writeJsonConfig(trimmed)) {
                    writeEnvValue("VPN_SELECTED_URI", "")
                    maybeRestartVpnAfterConfigChange()
                } else {
                    OperationResult(false, "Failed to write JSON config")
                }
            }
            trimmed.startsWith("vless://", ignoreCase = true) ||
                trimmed.startsWith("vmess://", ignoreCase = true) ||
                trimmed.startsWith("ss://", ignoreCase = true) ||
                trimmed.startsWith("trojan://", ignoreCase = true) -> {
                val result = Shell.cmd(
                    "sh ${shellQuote(SUBSCRIPTION_PARSER)} uri ${shellQuote(trimmed)}"
                ).exec()
                if (result.isSuccess) {
                    maybeRestartVpnAfterConfigChange()
                } else {
                    OperationResult(false, readLastRelevantLine(SUBSCRIPTION_LOG, result.err, "Failed to apply URI"))
                }
            }
            else -> OperationResult(false, "Unsupported config format")
        }
    }

    suspend fun applySelectedServer(uri: String): OperationResult = withContext(Dispatchers.IO) {
        val trimmed = uri.trim()
        if (trimmed.isBlank()) {
            return@withContext OperationResult(false, "Server URI is empty")
        }

        val applied = applyConfigInput(trimmed)
        if (!applied.success) {
            return@withContext applied
        }

        if (!writeEnvValue("VPN_SELECTED_URI", trimmed)) {
            return@withContext OperationResult(false, "Server was applied, but selection was not saved")
        }

        val restarted = maybeRestartVpnAfterConfigChange(forceStartIfEnabled = true)
        val message = if (restarted.success) {
            "Server applied"
        } else if (readBoolValue("VPN_ENABLED")) {
            "Server applied, but VPN restart failed: ${restarted.message}"
        } else {
            "Server applied"
        }

        OperationResult(restarted.success || !readBoolValue("VPN_ENABLED"), message, restarted.details)
    }

    suspend fun startVpn(): OperationResult {
        return runScript(VPN_START_SCRIPT, "VPN start failed")
    }

    suspend fun stopVpn(): OperationResult {
        return runScript(VPN_STOP_SCRIPT, "VPN stop failed")
    }

    suspend fun restartVpn(): OperationResult = withContext(Dispatchers.IO) {
        val stop = runScript(VPN_STOP_SCRIPT, "VPN stop failed")
        val start = runScript(VPN_START_SCRIPT, "VPN start failed")
        if (start.success) {
            OperationResult(true, "VPN restarted", listOf(stop.details, start.details).filter { it.isNotBlank() }.joinToString("\n"))
        } else {
            start
        }
    }

    suspend fun getRuntimeStatus(): VpnRuntimeStatus = withContext(Dispatchers.IO) {
        val running = isVpnRunning()
        val vpnEnabled = readBoolValue("VPN_ENABLED")
        val selectedServer = readEnvValue("VPN_SELECTED_URI")?.takeIf { it.isNotBlank() }
        val subscriptionUrl = readEnvValue("VPN_SUBSCRIPTION_URL")?.takeIf { it.isNotBlank() }
        val configReady = fileExists(VPN_CONFIG) || fileExists(XRAY_CONFIG)
        val pid = readFile(VPN_PID)?.trim()?.takeIf { it.isNotBlank() }
        val lastLog = readLastRelevantLine(VPN_LOG, emptyList(), "")

        val summary = when {
            running && pid != null -> "Running (PID: $pid)"
            running -> "Running"
            else -> "Stopped"
        }

        val detail = when {
            running -> lastLog.ifBlank { "Xray is running" }
            !vpnEnabled -> "VPN is disabled in config"
            !configReady && selectedServer == null && subscriptionUrl.isNullOrBlank() -> "No VPN configuration provided"
            !configReady && selectedServer != null -> "Selected server is saved, but config is not generated yet"
            !configReady -> "VPN config is missing"
            lastLog.isNotBlank() -> lastLog
            else -> "Ready to start"
        }

        VpnRuntimeStatus(
            running = running,
            summary = summary,
            detail = detail,
            vpnEnabled = vpnEnabled,
            configReady = configReady,
            selectedServerUri = selectedServer,
            subscriptionUrl = subscriptionUrl
        )
    }

    suspend fun getRecentLogs(lines: Int = 40): LogSnapshot = withContext(Dispatchers.IO) {
        LogSnapshot(
            vpnLog = readLogTail(VPN_LOG, lines),
            subscriptionLog = readLogTail(SUBSCRIPTION_LOG, lines)
        )
    }

    private suspend fun writeEnvValue(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val line = "$key=\"$value\""
        val command =
            "FILE=${shellQuote(VPN_ENV)}; " +
                "TMP=\$(mktemp ${shellQuote("$ZAPRET_DIR/vpn-config.env.XXXXXX")}); " +
                "(grep -v '^${key}=' \"\$FILE\" 2>/dev/null || true) > \"\$TMP\" && " +
                "printf '%s\\n' ${shellQuote(line)} >> \"\$TMP\" && " +
                "mv \"\$TMP\" \"\$FILE\" && chmod 644 \"\$FILE\""

        Shell.cmd(command).exec().isSuccess
    }

    private suspend fun writeUnquotedEnvValue(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val command =
            "FILE=${shellQuote(VPN_ENV)}; " +
                "TMP=\$(mktemp ${shellQuote("$ZAPRET_DIR/vpn-config.env.XXXXXX")}); " +
                "(grep -v '^${key}=' \"\$FILE\" 2>/dev/null || true) > \"\$TMP\" && " +
                "printf '%s\\n' ${shellQuote("$key=$value")} >> \"\$TMP\" && " +
                "mv \"\$TMP\" \"\$FILE\" && chmod 644 \"\$FILE\""

        Shell.cmd(command).exec().isSuccess
    }

    private suspend fun readEnvValue(key: String): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("cat ${shellQuote(VPN_ENV)} 2>/dev/null").exec()
        if (!result.isSuccess) {
            return@withContext null
        }

        val content = result.out.joinToString("\n")
        val escapedKey = Regex.escape(key)
        val quoted = Regex("""^$escapedKey="(.*)"$""", RegexOption.MULTILINE)
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
        if (quoted != null) {
            return@withContext quoted
        }

        Regex("""^$escapedKey=(.*)$""", RegexOption.MULTILINE)
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
    }

    private suspend fun writeJsonConfig(json: String): Boolean = withContext(Dispatchers.IO) {
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val command =
            "printf '%s' ${shellQuote(encoded)} | base64 -d > ${shellQuote(XRAY_CONFIG)} && " +
                "cp ${shellQuote(XRAY_CONFIG)} ${shellQuote(VPN_CONFIG)} && " +
                "chmod 644 ${shellQuote(XRAY_CONFIG)} ${shellQuote(VPN_CONFIG)}"

        Shell.cmd(command).exec().isSuccess
    }

    private fun fetchUrl(urlString: String): FetchResult {
        var connection: HttpURLConnection? = null
        return try {
            var currentUrl = URL(urlString)
            var redirectCount = 0

            while (redirectCount < 5) {
                connection = currentUrl.openConnection() as HttpURLConnection
                connection!!.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Zapret2-Android/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("Accept", "*/*")
                }

                when (val responseCode = connection!!.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        return FetchResult(
                            success = true,
                            content = connection!!.inputStream.bufferedReader().use { it.readText() },
                            message = "OK"
                        )
                    }
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    307,
                    308 -> {
                        val location = connection!!.getHeaderField("Location")
                            ?: return FetchResult(false, message = "Redirect without location")
                        connection!!.disconnect()
                        currentUrl = URL(currentUrl, location)
                        redirectCount++
                    }
                    else -> {
                        connection!!.errorStream?.close()
                        return FetchResult(false, message = "HTTP error: $responseCode")
                    }
                }
            }

            FetchResult(false, message = "Too many redirects")
        } catch (e: Exception) {
            FetchResult(false, message = e.message ?: "Network request failed")
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractFirstSupportedUri(content: String): String? {
        return content
            .lineSequence()
            .map { it.trim() }
            .firstOrNull {
                it.startsWith("vless://", ignoreCase = true) ||
                    it.startsWith("vmess://", ignoreCase = true) ||
                    it.startsWith("ss://", ignoreCase = true) ||
                    it.startsWith("trojan://", ignoreCase = true)
            }
    }

    private suspend fun maybeRestartVpnAfterConfigChange(forceStartIfEnabled: Boolean = false): OperationResult = withContext(Dispatchers.IO) {
        val running = isVpnRunning()
        val enabled = readBoolValue("VPN_ENABLED")

        return@withContext when {
            running -> restartVpn()
            forceStartIfEnabled && enabled -> startVpn()
            enabled -> OperationResult(true, "Config saved. VPN will use it on next start.")
            else -> OperationResult(true, "Config saved")
        }
    }

    private suspend fun runScript(path: String, fallbackMessage: String): OperationResult = withContext(Dispatchers.IO) {
        val result = Shell.cmd("sh ${shellQuote(path)} 2>&1").exec()
        val output = (result.out + result.err).joinToString("\n").trim()
        if (result.isSuccess) {
            OperationResult(true, output.ifBlank { "OK" }, output)
        } else {
            OperationResult(false, readLastRelevantLine(VPN_LOG, result.err, fallbackMessage), output)
        }
    }

    private suspend fun isVpnRunning(): Boolean = withContext(Dispatchers.IO) {
        val pid = readFile(VPN_PID)?.trim()?.toIntOrNull() ?: return@withContext false
        Shell.cmd("kill -0 $pid 2>/dev/null").exec().isSuccess
    }

    private suspend fun readBoolValue(key: String, default: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        when (readEnvValue(key)?.trim()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> default
        }
    }

    private suspend fun writeBoolValue(key: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        writeUnquotedEnvValue(key, if (enabled) "1" else "0")
    }

    private suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("cat ${shellQuote(path)} 2>/dev/null").exec()
        if (!result.isSuccess) return@withContext null
        result.out.joinToString("\n")
    }

    private suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("[ -s ${shellQuote(path)} ]").exec().isSuccess
    }

    private suspend fun readLogTail(path: String, lines: Int): String = withContext(Dispatchers.IO) {
        val safeLines = lines.coerceIn(1, 200)
        val result = Shell.cmd("tail -n $safeLines ${shellQuote(path)} 2>/dev/null").exec()
        result.out.joinToString("\n").trim().ifBlank { "No log entries" }
    }

    private fun readLastRelevantLine(path: String, stderr: List<String>, fallback: String): String {
        val logResult = Shell.cmd("tail -n 40 ${shellQuote(path)} 2>/dev/null").exec()
        val logLine = logResult.out
            .map { it.trim() }
            .lastOrNull { it.isNotBlank() }
            ?.substringAfter("] ", missingDelimiterValue = "")
            ?.ifBlank { null }
        val stderrLine = stderr
            .map { it.trim() }
            .lastOrNull { it.isNotBlank() }

        return logLine ?: stderrLine ?: fallback
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
