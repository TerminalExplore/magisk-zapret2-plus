package com.zapret2.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zapret2.app.data.DnsFilterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DnsFilterViewModel @Inject constructor(
    private val dnsFilterManager: DnsFilterManager
) : ViewModel() {

    data class UiState(
        val apps: List<DnsFilterManager.AppDnsConfig> = emptyList(),
        val isLoading: Boolean = false,
        val loadingText: String = "",
        val defaultDns: String = "1.1.1.1"
    )

    sealed class Event {
        data class ShowSnackbar(val message: String) : Event()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Loading apps...") }
            
            try {
                val apps = dnsFilterManager.getInstalledApps()
                _uiState.update { it.copy(apps = apps, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.emit(Event.ShowSnackbar("Failed to load apps: ${e.message}"))
            }
        }
    }

    fun toggleApp(packageName: String, enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                apps = state.apps.map { app ->
                    if (app.packageName == packageName) {
                        app.copy(
                            isEnabled = enabled,
                            dnsServer = if (enabled) state.defaultDns else app.dnsServer
                        )
                    } else app
                }
            )
        }
    }

    fun setAppDns(packageName: String, dns: String?) {
        _uiState.update { state ->
            state.copy(
                apps = state.apps.map { app ->
                    if (app.packageName == packageName) {
                        app.copy(dnsServer = dns)
                    } else app
                }
            )
        }
    }

    fun setDefaultDns(dns: String) {
        _uiState.update { state ->
            state.copy(
                defaultDns = dns,
                apps = state.apps.map { app ->
                    if (app.isEnabled && app.dnsServer == state.defaultDns) {
                        app.copy(dnsServer = dns)
                    } else app
                }
            )
        }
    }

    fun applyChanges() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Applying DNS filter...") }
            
            val result = dnsFilterManager.applyDnsFilter(_uiState.value.apps)
            
            _uiState.update { it.copy(isLoading = false) }
            
            if (result.isSuccess) {
                _events.emit(Event.ShowSnackbar("DNS filter applied"))
            } else {
                _events.emit(Event.ShowSnackbar("Failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun flushFilter() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Flushing DNS filter...") }
            
            val result = dnsFilterManager.flushDnsFilter()
            
            _uiState.update { it.copy(isLoading = false) }
            
            if (result.isSuccess) {
                _events.emit(Event.ShowSnackbar("DNS filter cleared"))
            } else {
                _events.emit(Event.ShowSnackbar("Failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun checkStatus() {
        viewModelScope.launch {
            val status = dnsFilterManager.getDnsFilterStatus()
            _events.emit(Event.ShowSnackbar(status.take(100)))
        }
    }
}
