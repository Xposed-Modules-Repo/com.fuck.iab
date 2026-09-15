package fh

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fuck.iab.R
import kotlinx.coroutines.launch

// log fragment
class f : Fragment() {

    lateinit var logcatScroll: ScrollView
    lateinit var logcatView: TextView
    lateinit var autoScrollCheckBox: CheckBox

    private val viewModel: h by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(
            R.layout.page_logcat,
            container,
            false
        )
        logcatScroll = v.findViewById(R.id.logcatScroll)
        logcatView = v.findViewById(R.id.logcatView)
        autoScrollCheckBox = v.findViewById(R.id.autoScrollCheckBox)
        autoScrollCheckBox.isChecked = true

        v.findViewById<Button>(R.id.clearLogButton)
            .setOnClickListener {
                viewModel.clearLogs()
                logcatView.text = ""
            }

        logcatView.text = viewModel.getLogs()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logs.collect { log ->
                    logcatView.append(log)

                    if (logcatView.length() > 100000) {
                        logcatView.text = logcatView.text.takeLast(50000)
                    }

                    scrollToBottom()
                }
            }
        }
        viewModel.logInit()

        return v
    }

    fun scrollToBottom() {
        if (autoScrollCheckBox.isChecked) {
            logcatScroll.post {
                logcatScroll.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (::logcatScroll.isInitialized && ::autoScrollCheckBox.isInitialized) {
            scrollToBottom()
        }
    }
}