package com.zapret2.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

    init {
        viewModelScope.launch {
            loadHosts()
        }
    }

    fun loadHosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, actionsEnabled = false, error = null) }
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val r = Shell.cmd("cat '$moduleHostsPath' 2>/dev/null").exec()
                    if (r.isSuccess && r.out.isNotEmpty()) {
                        Result.success(r.out.joinToString("\n"))
                    } else {
                        val sysResult = Shell.cmd("cat '$systemHostsPath' 2>/dev/null").exec()
                        if (sysResult.isSuccess && sysResult.out.isNotEmpty()) {
                            Result.success(sysResult.out.joinToString("\n"))
                        } else {
                            Result.success("# Default hosts\n127.0.0.1 localhost\n::1 localhost\n")
                        }
                    }
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
                    
                    val result = Shell.cmd("echo '$content' > '$moduleHostsPath'").exec()
                    
                    if (!result.isSuccess) {
                        val tmpFile = java.io.File.createTempFile("hosts_", ".tmp", context.cacheDir)
                        try {
                            tmpFile.writeText(content, Charsets.UTF_8)
                            Shell.cmd("cp '${tmpFile.absolutePath}' '$moduleHostsPath'").exec()
                        } finally {
                            tmpFile.delete()
                        }
                    }
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }
            
            _uiState.update { it.copy(isLoading = false, actionsEnabled = true) }
            _snackbar.emit(if (saved) "Hosts file saved" else "Failed to save hosts file")
        }
    }
}
