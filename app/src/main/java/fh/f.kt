package fh

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fuck.iab.R
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

// log fragment: the output only, the actions live in the toolbar
class f : Fragment() {

    lateinit var logcatScroll: ScrollView
    lateinit var logcatView: TextView
    lateinit var autoScrollSwitch: MaterialSwitch
    lateinit var pauseResumeButton: ImageButton
    lateinit var logFilterInput: TextInputEditText

    private val viewModel: h by activityViewModels()
    private var filterQuery: String = ""

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
        logFilterInput = v.findViewById(R.id.logFilterInput)
        pauseResumeButton = v.findViewById(R.id.pauseResumeButton)

        autoScrollSwitch = v.findViewById(R.id.autoScrollSwitch)
        autoScrollSwitch.isChecked = viewModel.autoScroll
        autoScrollSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.autoScroll = checked
        }

        updatePauseButtonState()
        pauseResumeButton.setOnClickListener {
            viewModel.isStreamingPaused = !viewModel.isStreamingPaused
            updatePauseButtonState()
            Toast.makeText(
                requireContext(),
                if (viewModel.isStreamingPaused) R.string.logs_paused else R.string.logs_resumed,
                Toast.LENGTH_SHORT
            ).show()
        }

        logFilterInput.doAfterTextChanged { text ->
            filterQuery = text?.toString().orEmpty().trim()
            logcatView.text = viewModel.getFilteredLogs(filterQuery)
            scrollToBottom()
        }

        logcatView.text = viewModel.getFilteredLogs(filterQuery)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logs.collect { log ->
                    if (filterQuery.isEmpty() || log.contains(filterQuery, ignoreCase = true)) {
                        logcatView.append(log)

                        if (logcatView.length() > 100000) {
                            logcatView.text = logcatView.text.takeLast(50000)
                        }

                        scrollToBottom()
                    }
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

    private fun updatePauseButtonState() {
        if (::pauseResumeButton.isInitialized) {
            pauseResumeButton.setImageResource(
                if (viewModel.isStreamingPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause
            )
        }
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

        viewModel.startLogcatStreaming()
    }
}