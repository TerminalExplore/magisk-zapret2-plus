package com.zapret2.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.zapret2.app.data.RuntimeConfigStore
import com.zapret2.app.data.ServiceEventBus
import com.zapret2.app.data.StrategyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CategoryUiModel(
    val key: String,
    val title: String,
    val subtitle: String,
    val type: String,           // "tcp", "udp", "voice"
    val iconRes: Int,
    val iconTint: Long,         // Color as ARGB long
    val strategyName: String,
    val strategyDisplayName: String,
    val filterMode: String,
    val canSwitchFilter: Boolean
)

data class StrategiesUiState(
    val categories: List<CategoryUiModel> = emptyList(),
    val pktCount: String = "5",
    val debugMode: String = "none",
    val isLoading: Boolean = false,
    val loadingText: String = "",
    val activePreset: String? = null
)

@HiltViewModel
class StrategiesViewModel @Inject constructor(
    private val serviceEventBus: ServiceEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(StrategiesUiState())
    val uiState: StateFlow<StrategiesUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private val moduleDir = "/data/adb/modules/zapret2"
    private val restartScript = "$moduleDir/zapret2/scripts/zapret-restart.sh"

    // Mutable maps for tracking user selections
    private val selections = mutableMapOf<String, String>()
    private val filterModes = mutableMapOf<String, String>()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            val categories = StrategyRepository.readCategories()
            val runtimeCore = withContext(Dispatchers.IO) { RuntimeConfigStore.readCore() }
            val activePreset = withContext(Dispatchers.IO) { StrategyRepository.getCurrentPreset() }

            selections.clear()
            filterModes.clear()

            val uiModels = categories.map { (key, config) ->
                val strategyName = config.strategyName.ifEmpty { "disabled" }
                selections[key] = strategyName
                filterModes[key] = config.filterMode

                CategoryUiModel(
                    key = key,
                    title = formatCategoryTitle(key, config.protocol),
                    subtitle = "${protocolLabel(config.protocol)} - ${filterTarget(config)}",
                    type = pickerType(config.protocol),
                    iconRes = 0,
                    iconTint = 0L,
                    strategyName = strategyName,
                    strategyDisplayName = formatStrategyDisplay(strategyName),
                    filterMode = config.filterMode,
                    canSwitchFilter = config.canSwitchFilterMode
                )
            }

            val pktValue = runtimeCore["pkt_out"] ?: runtimeCore["pkt_count"] ?: "5"
            val logMode = runtimeCore["log_mode"] ?: "none"
            selections["pkt_count"] = pktValue
            selections["debug"] = logMode

            _uiState.update { it.copy(
                categories = uiModels,
                pktCount = pktValue,
                debugMode = logMode,
                activePreset = activePreset
            )}
        }
    }

    fun applyPreset(presetName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Applying preset...") }

            val success = withContext(Dispatchers.IO) {
                StrategyRepository.applyPreset(presetName)
            }

            if (success) {
                val restartResult = Shell.cmd("sh $restartScript").exec()
                if (restartResult.isSuccess) {
                    _snackbar.emit("Preset '$presetName' applied")
                    serviceEventBus.notifyServiceRestarted()
                } else {
                    _snackbar.emit("Preset applied, restart failed")
                }
            } else {
                _snackbar.emit("Failed to apply preset")
            }

            loadConfig()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectStrategy(categoryKey: String, strategyId: String, newFilterMode: String? = null) {
        selections[categoryKey] = strategyId
        newFilterMode?.let { filterModes[categoryKey] = it }

        _uiState.update { state ->
            state.copy(categories = state.categories.map { cat ->
                if (cat.key == categoryKey) {
                    cat.copy(
                        strategyName = strategyId,
                        strategyDisplayName = formatStrategyDisplay(strategyId),
                        filterMode = newFilterMode ?: cat.filterMode
                    )
                } else cat
            })
        }
        saveConfigAndRestart()
    }

    fun setPktCount(value: String) {
        selections["pkt_count"] = value
        _uiState.update { it.copy(pktCount = value) }
        saveConfigAndRestart()
    }

    fun setDebugMode(value: String) {
        selections["debug"] = value
        _uiState.update { it.copy(debugMode = value) }
        saveConfigAndRestart()
    }

    fun autoSelectStrategies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Auto-selecting strategies...") }

            val result = withContext(Dispatchers.IO) {
                performAutoSelection()
            }

            when (result) {
                is AutoSelectResult.Success -> {
                    _snackbar.emit("✓ Best: ${result.strategyName} (${result.passed}/${result.total} sites)")
                    serviceEventBus.notifyServiceRestarted()
                }
                is AutoSelectResult.Partial -> {
                    _snackbar.emit("~ Partial: ${result.strategyName} (${result.passed}/${result.total} sites)")
                    serviceEventBus.notifyServiceRestarted()
                }
                is AutoSelectResult.Failed -> {
                    _snackbar.emit("✗ Auto-select: ${result.reason}")
                }
            }

            loadConfig()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun performAutoSelection(): AutoSelectResult {
        val tcpCategories = _uiState.value.categories.filter { it.type == "tcp" }
        if (tcpCategories.isEmpty()) {
            return AutoSelectResult.Failed("No TCP categories available")
        }

        val originalStrategies = tcpCategories.associate { category ->
            category.key to (selections[category.key] ?: category.strategyName)
        }
        val enabledTcpKeys = originalStrategies
            .filterValues { it.isNotEmpty() && it != "disabled" }
            .keys
            .toList()

        if (enabledTcpKeys.isEmpty()) {
            return AutoSelectResult.Failed("Enable at least one TCP category first")
        }

        // Test targets mapped to category keys they belong to
        val testTargets = listOf(
            ConnectivityTarget("https://www.youtube.com",  "YouTube",   setOf("youtube", "googlevideo_tcp")),
            ConnectivityTarget("https://discord.com",      "Discord",   setOf("discord_tcp")),
            ConnectivityTarget("https://rutracker.org",    "Rutracker", setOf("rutracker_tcp"))
        )
        val activeTargets = testTargets.filter { t -> t.categoryKeys.any(enabledTcpKeys::contains) }
        if (activeTargets.isEmpty()) {
            return AutoSelectResult.Failed("No supported TCP categories enabled")
        }

        val strategyCandidates = buildTcpAutoCandidates()
        if (strategyCandidates.isEmpty()) {
            return AutoSelectResult.Failed("No TCP strategies available")
        }

        // Apply current config and wait for zapret2 to be ready
        if (!applyCategoriesModeAndRestart()) {
            return AutoSelectResult.Failed("Failed to restart service")
        }
        waitForZapret2Ready()

        val baselineScore = testConnections(activeTargets)
        if (baselineScore.passed == baselineScore.total) {
            return AutoSelectResult.Success("Current selection", baselineScore.passed, baselineScore.total)
        }

        var bestCandidate: StrategyRepository.StrategyInfo? = null
        var bestScore = baselineScore

        for ((index, candidate) in strategyCandidates.withIndex()) {
            _uiState.update {
                it.copy(loadingText = "Testing ${candidate.displayName} (${index + 1}/${strategyCandidates.size})...")
            }

            val candidateUpdates = enabledTcpKeys.associateWith { candidate.id }
            if (!applyCategoriesModeAndRestart(candidateUpdates)) continue

            waitForZapret2Ready()
            val score = testConnections(activeTargets)

            if (score.passed > bestScore.passed) {
                bestScore = score
                bestCandidate = candidate
            }

            if (score.passed == score.total) {
                // Perfect score — keep this strategy applied and return
                return AutoSelectResult.Success(candidate.displayName, score.passed, score.total)
            }
        }

        val originalUpdates = enabledTcpKeys.associateWith { key -> originalStrategies[key] ?: "disabled" }

        return if (bestCandidate != null && bestScore.passed > baselineScore.passed) {
            val bestUpdates = enabledTcpKeys.associateWith { bestCandidate.id }
            if (applyCategoriesModeAndRestart(bestUpdates)) {
                AutoSelectResult.Partial(bestCandidate.displayName, bestScore.passed, bestScore.total)
            } else {
                applyCategoriesModeAndRestart(originalUpdates)
                AutoSelectResult.Failed("Failed to apply best strategy")
            }
        } else {
            applyCategoriesModeAndRestart(originalUpdates)
            AutoSelectResult.Failed("No better strategy found (baseline: ${baselineScore.passed}/${baselineScore.total})")
        }
    }

    // Wait until nfqws2 is actually running and iptables rules are in place
    private suspend fun waitForZapret2Ready() {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            val running = withContext(Dispatchers.IO) {
                Shell.cmd("pgrep -f nfqws2 >/dev/null 2>&1").exec().isSuccess
            }
            val hasRules = withContext(Dispatchers.IO) {
                Shell.cmd("iptables -t mangle -L OUTPUT -n 2>/dev/null | grep -q NFQUEUE").exec().isSuccess
            }
            if (running && hasRules) return
            delay(500)
        }
        // Give extra time for rules to settle even if checks passed
        delay(1000)
    }

    private suspend fun buildTcpAutoCandidates(): List<StrategyRepository.StrategyInfo> {
        val available = StrategyRepository.getTcpStrategies().filter { it.id != "disabled" }
        if (available.isEmpty()) return emptyList()

        val preferredIds = listOf(
            "hostfakesplit_multi_google_tcp_ts",
            "default",
            "syndata_multisplit_tls_google_700",
            "syndata_multidisorder_tls_google_700",
            "syndata_multisplit_tls_google_1000",
            "syndata_multidisorder_tls_google_1000",
            "syndata_3_tls_google",
            "syndata_7_tls_google_multisplit_midsld",
            "censorliber_google_syndata",
            "censorliber_google_syndata_v2",
            "general_alt11_191_syndata",
            "general_alt11_191",
            "general_simplefake_alt2_191",
            "general_simplefake_alt2_191_v2",
            "alt9"
        )

        val availableById = available.associateBy { it.id }
        val ordered = mutableListOf<StrategyRepository.StrategyInfo>()
        val seen = mutableSetOf<String>()

        preferredIds.forEach { strategyId ->
            availableById[strategyId]?.let { strategy ->
                if (seen.add(strategy.id)) {
                    ordered += strategy
                }
            }
        }

        available.forEach { strategy ->
            if (seen.add(strategy.id) && ordered.size < 18) {
                ordered += strategy
            }
        }

        return ordered
    }

    private suspend fun applyCategoriesModeAndRestart(
        strategyUpdates: Map<String, String>? = null
    ): Boolean {
        val categorySuccess = strategyUpdates?.let {
            StrategyRepository.updateAllCategoryStrategies(it)
        } ?: true
        if (!categorySuccess) {
            return false
        }

        val pktCount = selections["pkt_count"] ?: _uiState.value.pktCount
        val debugMode = selections["debug"] ?: _uiState.value.debugMode

        val configSuccess = RuntimeConfigStore.updateCoreSettings(
            RuntimeConfigStore.CoreSettingsUpdate(
                presetMode = "categories",
                logMode = debugMode,
                pktOut = pktCount.toIntOrNull()
            ),
            removeKeys = setOf("pkt_count")
        )
        if (!configSuccess) {
            return false
        }

        return Shell.cmd("sh $restartScript").exec().isSuccess
    }

    private suspend fun testConnections(targets: List<ConnectivityTarget>): ConnectivityScore {
        var passed = 0
        targets.forEach { target ->
            // Test twice, count as passed if at least one succeeds
            val ok = testConnection(target.url) || testConnection(target.url)
            if (ok) passed++
        }
        return ConnectivityScore(passed = passed, total = targets.size)
    }

    // Tests via Kotlin HttpURLConnection — traffic goes through iptables/zapret2 automatically
    private suspend fun testConnection(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14)")
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }

    private data class ConnectivityTarget(
        val url: String,
        val label: String,
        val categoryKeys: Set<String>
    )

    private data class ConnectivityScore(
        val passed: Int,
        val total: Int
    )

    sealed class AutoSelectResult {
        data class Success(
            val strategyName: String,
            val passed: Int,
            val total: Int
        ) : AutoSelectResult()
        data class Partial(
            val strategyName: String,
            val passed: Int,
            val total: Int
        ) : AutoSelectResult()
        data class Failed(val reason: String) : AutoSelectResult()
    }

    private fun saveConfigAndRestart() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Restarting service...") }

            val categoryKeys = _uiState.value.categories.map { it.key }
            val categoryUpdates = categoryKeys.associateWith { selections[it] ?: "disabled" }
            val filterModeUpdates = categoryKeys.mapNotNull { key ->
                filterModes[key]?.let { key to it }
            }.toMap()

            val allSuccess = StrategyRepository.updateAllCategoryStrategies(
                categoryUpdates,
                filterModeUpdates.ifEmpty { null }
            )

            val pktCount = selections["pkt_count"] ?: "5"
            val debugMode = selections["debug"] ?: "none"

            val (configSuccess, restartSuccess) = withContext(Dispatchers.IO) {
                val updated = RuntimeConfigStore.updateCoreSettings(
                    RuntimeConfigStore.CoreSettingsUpdate(
                        presetMode = "categories",
                        logMode = debugMode,
                        pktOut = pktCount.toIntOrNull()
                    ),
                    removeKeys = setOf("pkt_count")
                )
                if (!updated) return@withContext Pair(false, false)
                val restart = Shell.cmd("sh $restartScript").exec()
                Pair(true, restart.isSuccess)
            }

            _uiState.update { it.copy(isLoading = false) }

            if (allSuccess && configSuccess && restartSuccess) {
                _snackbar.emit("Applied successfully")
                serviceEventBus.notifyServiceRestarted()
            } else if (allSuccess && configSuccess) {
                _snackbar.emit("Saved, restart failed")
            } else {
                _snackbar.emit("Save failed")
            }
        }
    }

    private fun formatCategoryTitle(key: String, protocol: String): String {
        val protocolToken = when (protocol.lowercase()) { "udp" -> "udp"; "stun" -> "stun"; else -> "tcp" }
        val tokens = key.split("_").toMutableList()
        if (tokens.size > 1 && tokens.last().lowercase() == protocolToken) tokens.removeAt(tokens.lastIndex)
        return tokens.joinToString(" ") { token ->
            when (token.lowercase()) {
                "youtube" -> "YouTube"; "googlevideo" -> "GoogleVideo"; "whatsapp" -> "WhatsApp"
                "github" -> "GitHub"; "anydesk" -> "AnyDesk"; "cloudflare" -> "Cloudflare"
                "warp" -> "WARP"; "claude" -> "Claude"; "chatgpt" -> "ChatGPT"
                "tcp" -> "TCP"; "udp" -> "UDP"; "stun" -> "STUN"
                else -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }

    private fun protocolLabel(protocol: String) = when (protocol.lowercase()) { "udp" -> "UDP"; "stun" -> "STUN"; else -> "TCP" }
    private fun pickerType(protocol: String) = when (protocol.lowercase()) { "udp" -> "udp"; "stun" -> "voice"; else -> "tcp" }

    private fun filterTarget(config: StrategyRepository.CategoryConfig): String = when (config.filterMode.lowercase()) {
        "ipset" -> config.ipsetFile.ifEmpty { "ipset" }
        "hostlist" -> config.hostlistFile.ifEmpty { "hostlist" }
        "hostlist-domains" -> config.hostlistDomains.ifEmpty { "hostlist-domains" }
        else -> "none"
    }

    private fun formatStrategyDisplay(name: String): String {
        if (name == "disabled") return "Disabled"
        val display = name.split("_").joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
        return if (display.length > 25) display.take(22) + "..." else display
    }
}
