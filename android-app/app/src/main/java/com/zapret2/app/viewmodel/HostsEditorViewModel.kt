package com.zapret2.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HostsEditorUiState(
    val content: String = "",
    val isLoading: Boolean = false,
    val actionsEnabled: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HostsEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HostsEditorUiState())
    val uiState: StateFlow<HostsEditorUiState> = _uiState.asStateFlow()
    
    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()
    
    private val moduleHostsPath = "/data/adb/modules/zapret2/system/etc/hosts"
    private val systemHostsPath = "/system/etc/hosts"

    fun loadHosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, actionsEnabled = false, error = null) }
            
            val result = withContext(Dispatchers.IO) {
                try {
                    // Try module path first
                    var r = Shell.cmd("cat '$moduleHostsPath' 2>/dev/null").exec()
                    if (r.isSuccess && r.out.isNotEmpty()) {
                        return@withContext Result.success(r.out.joinToString("\n"))
                    }
                    
                    // Try system path
                    r = Shell.cmd("cat '$systemHostsPath' 2>/dev/null").exec()
                    if (r.isSuccess && r.out.isNotEmpty()) {
                        return@withContext Result.success(r.out.joinToString("\n"))
                    }
                    
                    // Default
                    Result.success("# Default hosts\n127.0.0.1 localhost\n::1 localhost\n")
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            
            result.onSuccess { content ->
                _uiState.update { it.copy(content = content, isLoading = false, actionsEnabled = true) }
            }.onFailure { error ->
                _uiState.update { 
                    it.copy(
                        content = "# Default hosts\n127.0.0.1 localhost\n::1 localhost\n",
                        isLoading = false, 
                        actionsEnabled = true,
                        error = error.message
                    ) 
                }
            }
        }
    }

    fun updateContent(text: String) {
        _uiState.update { it.copy(content = text) }
    }

    fun saveHosts() {
        viewModelScope.launch {
            val content = _uiState.value.content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trimEnd('\n') + "\n"
            
            if (content.isBlank()) { 
                _snackbar.emit("Hosts file is empty")
                return@launch 
            }
            
            _uiState.update { it.copy(isLoading = true, actionsEnabled = false) }
            
            val saved = withContext(Dispatchers.IO) {
                try {
                    val parentDir = moduleHostsPath.substringBeforeLast('/')
                    Shell.cmd("mkdir -p '$parentDir'").exec()
                    
                    // Write using temp file to avoid shell escaping issues
                    val tmpFile = java.io.File.createTempFile("hosts_", ".tmp", context.cacheDir)
                    try {
                        tmpFile.writeText(content, Charsets.UTF_8)
                        val r = Shell.cmd("cp '${tmpFile.absolutePath}' '$moduleHostsPath' && chmod 644 '$moduleHostsPath'").exec()
                        r.isSuccess
                    } finally {
                        tmpFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            
            _uiState.update { it.copy(isLoading = false, actionsEnabled = true) }
            
            if (saved) {
                _snackbar.emit("Hosts file saved")
            } else {
                _snackbar.emit("Failed to save hosts file")
            }
        }
    }
}
