package fh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuck.iab.Error__
import com.fuck.iab.FKIAB_V
import com.fuck.iab.FRIDA_V
import com.fuck.iab.__A_Z__
import com.fuck.iab._c
import com.fuck.iab._s
import com.fuck.iab._s_
import com.fuck.iab._v
import com.fuck.iab.logcat
import com.fuck.iab.su
import com.fuck.iab.tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class h : ViewModel() {

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
        return synchronized(logsArray) {
            logsArray.clear()
        }
    }

    var scriptLoaded = false
    private val _scriptText = MutableStateFlow("")
    val scriptText = _scriptText.asStateFlow()
    fun setScriptText(text: String) {
        _scriptText.value = text
    }

    private val _saveRequest = Channel<String>(Channel.BUFFERED)
    val saveRequest = _saveRequest.receiveAsFlow()
    fun requestSave(text: String) {
        _saveRequest.trySend(text)
    }

    private val _log_init = Channel<Unit>(Channel.BUFFERED)
    val logInit = _log_init.receiveAsFlow()
    fun logInit() {
        _log_init.trySend(Unit)
    }


    private var logcatJob: Job? = null

    fun startLogcatStreaming() {
        if (logcatJob != null) {
            return
        }

        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        su(),
                        _c(),
                        logcat(),
                        _v(),
                        tag(),
                        _s(),
                        FKIAB_V(),
                        FRIDA_V()
                    )
                )
                val reader = process.inputStream.bufferedReader()
                while (isActive) {
                    val line = reader.readLine() ?: break

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
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog("${Error__()}${e.message}\n")
                }
            }
        }
    }


    override fun onCleared() {
        logcatJob?.cancel()
        super.onCleared()
    }
}