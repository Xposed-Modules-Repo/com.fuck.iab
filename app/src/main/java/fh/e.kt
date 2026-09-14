package fh

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.fuck.iab.R

// script fragment
class e(var onInitialized: (() -> Unit)? = null) : Fragment() {

    lateinit var scriptInput: EditText
    lateinit var saveButton: Button
    lateinit var onSaveButtonClicked: () -> Unit

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
            onSaveButtonClicked()
        }
        onInitialized?.invoke()
        return v
    }
}