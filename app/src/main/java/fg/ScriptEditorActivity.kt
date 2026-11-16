package fg

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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.fuck.iab.AreYouSure
import com.fuck.iab.Cancel
import com.fuck.iab.CouldNotSaveTheScript
import com.fuck.iab.Delete
import com.fuck.iab.NotConnectedToTheXposedService
import com.fuck.iab.R
import com.fuck.iab.RemoteFiles
import com.fuck.iab.Script_saved
import com.fuck.iab.ScriptStore
import com.fuck.iab.UserScriptRemoved
import com.fuck.iab.YesDeleteIt
import com.fuck.iab.customScript
import com.fuck.iab.name
import com.fuck.iab.package_
import com.fuck.iab.readonly_file
import com.fuck.iab.rs_
import com.fuck.iab.summary
import com.fuck.iab.userScriptJs
import com.fuck.iab.user_script
import com.fuck.iab._js
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
    /** Set for global and opt-in scripts: shown, scrollable and copyable, but never editable. */
    private var readOnlyFile: String? = null
    private var service: XposedService? = null

    private val readOnly: Boolean get() = readOnlyFile != null

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

        readOnlyFile = intent.getStringExtra(EXTRA_READONLY_FILE)
        packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        service = MainApplication.getService()

        val toolbar = findViewById<MaterialToolbar>(R.id.editorToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = when {
            readOnly -> readOnlyFile
            packageName == user_script() -> userScriptJs()
            else -> ScriptStore.appLabel(this, packageName)
        }
        supportActionBar?.subtitle = when {
            readOnly -> null
            packageName == user_script() -> customScript()
            else -> packageName
        }
        toolbar.setNavigationOnClickListener { finish() }

        input = findViewById(R.id.editorInput)
        // TextView applies the multi-line input type at the very end of its constructor, after the
        // XML attrs were read, and that forces wrapping back on - so android:scrollHorizontally
        // can never win from XML and has to be re-applied from code.
        input.setHorizontallyScrolling(true)

        findViewById<MaterialButton>(R.id.editorSaveButton).apply {
            if (readOnly) {
                // the source is published by the repository, there is nothing to save back
                isVisible = false
            } else {
                isEnabled = service != null
                setOnClickListener { save() }
            }
        }

        if (service == null) {
            Toast.makeText(this, NotConnectedToTheXposedService(), Toast.LENGTH_SHORT).show()
        }

        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // no delete for a script the user does not own
        if (readOnly) return false
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

    private fun load() {
        val file = this@ScriptEditorActivity.readOnlyFile

        if (file != null) {
            loadReadOnly(file)
            return
        }

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

    /**
     * Published scripts (global and opt-in) are view-only: the source is shown so it can be
     * read, scrolled and copied, but there is no way to change what the repository ships.
     */
    private fun loadReadOnly(file: String) {
        val service = this@ScriptEditorActivity.service ?: return

        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                RemoteFiles.read(service, file)
            }

            if (content == null) {
                finish()
                return@launch
            }

            // `// name:` over the file name, `// summary:` over the bare script name
            val scriptName = file.removeSuffix(_js())
            supportActionBar?.title = ScriptStore.headerOf(content, name()) ?: file
            supportActionBar?.subtitle =
                ScriptStore.headerOf(content, summary())
                    ?: scriptName.removePrefix(rs_())

            // still scrollable and copyable, just impossible to type into
            input.keyListener = null
            input.isCursorVisible = false
            input.setTextIsSelectable(true)
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
                Toast.makeText(this@ScriptEditorActivity, CouldNotSaveTheScript(), Toast.LENGTH_SHORT).show()
                return@launch
            }

            Toast.makeText(this@ScriptEditorActivity, Script_saved(), Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun confirmRemove() {
        MaterialAlertDialogBuilder(this)
            .setTitle(Delete())
            .setMessage(AreYouSure())
            .setNegativeButton(Cancel(), null)
            .setPositiveButton(YesDeleteIt()) { _, _ -> remove() }
            .show()
    }

    private fun remove() {
        val current = service ?: return

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ScriptStore.removeUserScript(current, packageName)
            }

            Toast.makeText(this@ScriptEditorActivity, UserScriptRemoved(), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private val EXTRA_PACKAGE = package_()
        private val EXTRA_READONLY_FILE = readonly_file()

        fun intent(context: Context, packageName: String): Intent {
            return Intent(context, ScriptEditorActivity::class.java).putExtra(EXTRA_PACKAGE, packageName)
        }

        /** Opens a published `rs_*.js` script read-only: view, scroll and copy, never edit. */
        fun viewIntent(context: Context, file: String): Intent {
            return Intent(context, ScriptEditorActivity::class.java).putExtra(EXTRA_READONLY_FILE, file)
        }
    }
}
