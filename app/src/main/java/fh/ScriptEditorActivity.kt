package fh

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.fuck.iab.R
import com.fuck.iab.ScriptStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScriptEditorActivity : AppCompatActivity() {

    private lateinit var input: TextInputEditText
    private var packageName: String = ""
    private var service: XposedService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        val root = findViewById<View>(R.id.editorRoot)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        // Keep the save button above the keyboard instead of behind it.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        packageName = intent.getStringExtra(EXTRA_PACKAGE)!!
        service = MainApplication.getService()

        val toolbar = findViewById<MaterialToolbar>(R.id.editorToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = if (packageName == "user_script") {
            getString(R.string.title_user_script)
        } else {
            ScriptStore.appLabel(this, packageName)
        }
        supportActionBar?.subtitle = when (packageName) {
            "user_script" -> "custom script"
            else -> packageName
        }
        toolbar.setNavigationOnClickListener { finish() }

        input = findViewById(R.id.editorInput)

        findViewById<MaterialButton>(R.id.editorSaveButton).apply {
            isEnabled = service != null
            setOnClickListener { save() }
        }

        if (service == null) {
            Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show()
        }

        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val connected = service != null
        menu.findItem(R.id.action_revert)?.isVisible = connected
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_revert -> {
                confirmRemove()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Code scrolls sideways unless wrapping is asked for, so long lines stay on one line. */
    private fun applyWrap(enabled: Boolean) {
        input.setHorizontallyScrolling(!enabled)
        input.requestLayout()
    }

    private fun load() {
        val service = this@ScriptEditorActivity.service ?: return

        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                ScriptStore.readUserScript(service, packageName)
            }

            invalidateOptionsMenu()

            if (content == null) {
                return@launch
            }

            input.setText(content)
            input.setSelection(0)
        }
    }

    private fun save() {
        val current = service ?: return
        val text = input.text?.toString().orEmpty()

//        if (text.isBlank()) {
//            Toast.makeText(this, R.string.toast_package_required, Toast.LENGTH_SHORT).show()
//            return
//        }

        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                ScriptStore.saveUserScript(current, packageName, text)
            }

            if (!saved) {
                Toast.makeText(this@ScriptEditorActivity, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            Toast.makeText(this@ScriptEditorActivity, R.string.toast_saved, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun confirmRemove() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.editor_delete_title)
            .setMessage(R.string.editor_delete_note)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> remove() }
            .show()
    }

    private fun remove() {
        val current = service ?: return

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ScriptStore.removeUserScript(current, packageName)
            }

            Toast.makeText(this@ScriptEditorActivity, R.string.toast_reverted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun intent(context: Context, packageName: String): Intent {
            return Intent(context, ScriptEditorActivity::class.java).putExtra(EXTRA_PACKAGE, packageName)
        }
    }
}
