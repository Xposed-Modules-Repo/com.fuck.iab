package fg

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fuck.iab.Error__
import com.fuck.iab.R
import com.fuck.iab.ScriptInfo
import com.fuck.iab.ScriptStore
import com.fuck.iab.ScriptType
import com.fuck.iab.__A_Z__
import com.fuck.iab._c
import com.fuck.iab._s_
import com.fuck.iab.logcat__v_tag__s_FKIAB_V_FRIDA_V
import com.fuck.iab.su
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val connectionStatus: Int = 0, // 0 not sure, 1 connected, 2 not connected
    val framework: String = "",
    val scripts: List<ScriptInfo> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val logsArray = ArrayDeque<String>()
    private val _logs = Channel<String>(100)
    val logs = _logs.receiveAsFlow()

    fun addLog(text: String) {
        synchronized(logsArray) {
            logsArray.addLast(text)

            while (logsArray.size > 5000) {
                logsArray.removeFirst()
            }
        }

        _logs.trySend(text)
    }

    fun getLogs(): String {
        return synchronized(logsArray) {
            logsArray.joinToString("")
        }
    }

    fun clearLogs() {
        synchronized(logsArray) {
            logsArray.clear()
        }
    }

    var autoScroll = true

    private val _cleared = Channel<Unit>(Channel.BUFFERED)

    val cleared = _cleared.receiveAsFlow()

    fun clearLogView() {
        clearLogs()
        _cleared.trySend(Unit)
    }

    private var logcatJob: Job? = null

//    fun startLogcatStreaming() {
//        if (logcatJob != null) {
//            return
//        }
//
//        logcatJob = viewModelScope.launch(Dispatchers.IO) {
//            try {
//                val process = Runtime.getRuntime().exec(
//                    arrayOf(
//                        su(),
//                        _c(),
//                        logcat__v_tag__s_FKIAB_V_FRIDA_V()
//                    )
//                )
//                val reader = process.inputStream.bufferedReader()
//                while (isActive) {
//                    val line = reader.readLine() ?: break
//
//                    val index = line.indexOf(": ")
//
//                    val clean = if (index != -1) {
//                        line.substring(0, index)
//                            .replace(Regex(__A_Z__()), "")
//                            .trim() +
//                                _s_() +
//                                line.substring(index + 2)
//                    } else {
//                        line
//                    }
//
//                    withContext(Dispatchers.Main) {
//                        addLog(clean + "\n")
//                    }
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    addLog("${Error__()}${e.message}\n")
//                }
//            }
//        }
//    }

    fun startLogcatStreaming() {
        if (logcatJob != null) {
            return
        }

        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()

            val process = try {
                Runtime.getRuntime().exec(
                    arrayOf(
                        su(),
                        _c(),
                        logcat__v_tag__s_FKIAB_V_FRIDA_V()
                    )
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { addLog(app.getString(R.string.logs_no_su) + "\n") }
                return@launch
            }

            try {
                val reader = process.inputStream.bufferedReader()
                var line = reader.readLine()

                if (line == null) {
                    val code = try {
                        process.waitFor()
                    } catch (e: Exception) {
                        -1
                    }

                    withContext(Dispatchers.Main) {
                        addLog(
                            app.getString(
                                if (code != 0) R.string.logs_root_denied else R.string.logs_no_output
                            ) + "\n"
                        )
                    }

                    return@launch
                }

                while (isActive) {
                    val index = line.indexOf(": ")

                    val clean = if (index != -1) {
                        line.substring(0, index)
                            .replace(Regex(__A_Z__()), "")
                            .trim() +
                                _s_() +
                                line.substring(index + 2)
                    } else {
                        line
                    }

                    withContext(Dispatchers.Main) {
                        addLog(clean + "\n")
                    }

                    line = reader.readLine() ?: break
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog(app.getString(R.string.logs_stream_failed, e.message ?: e.javaClass.simpleName) + "\n")
                }
            }
        }
    }

    fun retryLogcatStreaming() {
        logcatJob?.cancel()
        logcatJob = null
        clearLogView()
        startLogcatStreaming()
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var currentService: XposedService? = null

    fun attachService(service: XposedService?) {
        currentService = service

        _state.update {
            it.copy(
                connectionStatus = if(service != null) 1 else 2,
                framework = service?.let { s -> "${s.frameworkName} ${s.frameworkVersionCode}" } ?: ""
            )
        }

        if (service == null) {
            return
        }

        refresh()
    }

    fun refresh() {
        val service = currentService ?: return

        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { load(service) }

            _state.update {
                it.copy(
                    scripts = loaded.scripts,
//                    hookedApps = loaded.apps
                )
            }
        }
    }

    private class Loaded(
        val scripts: List<ScriptInfo>
    )

    private fun load(service: XposedService?): Loaded {
        val context = getApplication<Application>()

        val scripts = ScriptStore.inventory(context, service)

        return Loaded(
            scripts = scripts
        )
    }

    fun setEnabled(packageName: String, type: ScriptType, enabled: Boolean) {
        val service = currentService ?: return

        viewModelScope.launch {
            withContext(Dispatchers.IO) { ScriptStore.setEnabled(service, packageName, type, enabled) }
//            refresh()
        }
    }

    override fun onCleared() {
        logcatJob?.cancel()
        super.onCleared()
    }

}
