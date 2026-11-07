package fh

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.fuck.iab.R
import com.fuck.iab.ScriptStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// editor for one script: saves a user copy that wins over the remote one
class editor : AppCompatActivity() {

    private lateinit var input: TextInputEditText
    private lateinit var source: TextView
    private var packageName: String = ScriptStore.ALL_APPS
    private var service: XposedService? = null

    /** The script on screen is a user copy, so it can be deleted again. */
    private var hasUserCopy = false
    private var originalContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        val root = findViewById<View>(R.id.editorRoot)
        applySystemBarIcons(this, root)

        // Keep the save button above the keyboard instead of behind it.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: ScriptStore.ALL_APPS
        service = g.getService()

        val toolbar = findViewById<MaterialToolbar>(R.id.editorToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = if (packageName == ScriptStore.ALL_APPS) {
            getString(R.string.scripts_all_apps)
        } else {
            ScriptStore.appLabel(this, packageName)
        }
        supportActionBar?.subtitle = packageName
        toolbar.setNavigationOnClickListener { handleBackPress() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        input = findViewById(R.id.editorInput)
        source = findViewById(R.id.editorSource)

        val wrap = findViewById<MaterialSwitch>(R.id.editorWrap)
        wrap.setOnCheckedChangeListener { _, checked -> applyWrap(checked) }
        applyWrap(wrap.isChecked)

        // Setup Frida Quick Snippet Buttons
        setupSnippet(R.id.snipJavaUse, "const Target = Java.use(\"\");")
        setupSnippet(R.id.snipIl2Cpp, "Il2Cpp.perform(() => {\n    \n});")
        setupSnippet(R.id.snipConsoleLog, "console.log(\"\");")
        setupSnippet(R.id.snipArrow, "() => {\n    \n}")
        setupSnippet(R.id.snipReturnTrue, "return true;")
        setupSnippet(R.id.snipSemicolon, ";")

        findViewById<MaterialButton>(R.id.editorSaveButton).apply {
            isEnabled = service != null
            setOnClickListener { save() }
        }

        if (service == null) {
            Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show()
        }

        load()
    }

    private fun setupSnippet(buttonId: Int, snippet: String) {
        findViewById<MaterialButton?>(buttonId)?.setOnClickListener {
            insertSnippetAtCursor(snippet)
        }
    }

    private fun insertSnippetAtCursor(text: String) {
        val start = input.selectionStart.coerceAtLeast(0)
        val end = input.selectionEnd.coerceAtLeast(0)
        input.text?.replace(Math.min(start, end), Math.max(start, end), text)
        input.setSelection(start + text.length)
        input.requestFocus()
    }

    private fun handleBackPress() {
        val currentText = input.text?.toString().orEmpty()
        if (currentText != originalContent) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.editor_unsaved_title)
                .setMessage(R.string.editor_unsaved_message)
                .setNegativeButton(R.string.editor_keep_editing, null)
                .setPositiveButton(R.string.editor_discard) { _, _ -> finish() }
                .show()
        } else {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_revert)?.isVisible = service != null && hasUserCopy
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_revert -> {
                confirmRevert()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyWrap(enabled: Boolean) {
        input.setHorizontallyScrolling(!enabled)
        input.requestLayout()
    }

    private fun load() {
        val current = service ?: return

        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                ScriptStore.readScript(current, packageName)
            }

            val details = withContext(Dispatchers.IO) {
                ScriptStore.details(this@editor, current, packageName)
            }

            hasUserCopy = details.hasUserScript
            invalidateOptionsMenu()

            if (content == null) {
                source.text = ""
                originalContent = ""
                return@launch
            }

            val (from, text) = content
            originalContent = text
            input.setText(text)
            input.setSelection(0)

            val author = ScriptStore.authorOf(text)
            source.text = if (author == null) {
                getString(R.string.editor_source, sourceLabel(this@editor, from))
            } else {
                getString(R.string.editor_source, sourceLabel(this@editor, from)) +
                        " • " + getString(R.string.editor_author, author)
            }
        }
    }

    private fun save() {
        val current = service ?: return
        val text = input.text?.toString().orEmpty()

        if (text.isBlank()) {
            Toast.makeText(this, R.string.toast_package_required, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                ScriptStore.saveUserScript(this@editor, current, packageName, text)
            }

            if (!saved) {
                Toast.makeText(this@editor, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            originalContent = text
            Toast.makeText(this@editor, R.string.toast_saved, Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun confirmRevert() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_revert)
            .setMessage(R.string.editor_revert_note)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_revert, DialogInterface.OnClickListener { _, _ -> revert() })
            .show()
    }

    private fun revert() {
        val current = service ?: return

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ScriptStore.revertUserScript(this@editor, current, packageName)
            }

            Toast.makeText(this@editor, R.string.toast_reverted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun intent(context: Context, packageName: String): Intent {
            return Intent(context, editor::class.java).putExtra(EXTRA_PACKAGE, packageName)
        }
    }
}