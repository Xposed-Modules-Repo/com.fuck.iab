package fh

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fuck.iab.R
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

// log fragment: the output only, the actions live in the toolbar
class f : Fragment() {

    lateinit var logcatScroll: ScrollView
    lateinit var logcatView: TextView
    lateinit var autoScrollSwitch: MaterialSwitch

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

        autoScrollSwitch = v.findViewById(R.id.autoScrollSwitch)
        autoScrollSwitch.isChecked = viewModel.autoScroll
        autoScrollSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.autoScroll = checked
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

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cleared.collect {
                    logcatView.text = ""
                }
            }
        }

        return v
    }

    fun scrollToBottom() {
        if (viewModel.autoScroll) {
            logcatScroll.post {
                logcatScroll.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (::logcatScroll.isInitialized && ::logcatView.isInitialized) {
            scrollToBottom()
        }

        // This page is a tab now, so the stream starts when it becomes visible rather than
        // when the app opens. The view model ignores a second start while one is running.
        viewModel.startLogcatStreaming()
    }
}
