package fh

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fuck.iab.R
import kotlinx.coroutines.launch

// script fragment
class e : Fragment() {

    lateinit var scriptInput: EditText
    lateinit var saveButton: Button

    private val viewModel: h by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(
            R.layout.page_script,
            container,
            false
        )

        scriptInput = v.findViewById(R.id.scriptInput)
        scriptInput.setHorizontallyScrolling(true)
        saveButton = v.findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            viewModel.requestSave(scriptInput.text.toString())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scriptText.collect { text ->
                    if (scriptInput.text.toString() != text) {
                        scriptInput.setText(text)
                        scriptInput.setSelection(text.length)
                    }
                }
            }
        }

        scriptInput.addTextChangedListener {
            viewModel.setScriptText(it.toString())
        }

        return v
    }
}