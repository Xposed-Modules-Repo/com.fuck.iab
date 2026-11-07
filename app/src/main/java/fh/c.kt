package fh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.fuck.iab.R
import com.fuck.iab.RemoteScripts
import com.fuck.iab.ScriptStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch

// main activity: home, scripts and logs behind a bottom navigation bar
class c : AppCompatActivity(), g.ServiceStateListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar

    private val viewModel: h by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Material You dynamic color where available
        DynamicColors.applyIfAvailable(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        applySystemBarIcons(this, root)
        root.post {
            val rootWindowInsets = ViewCompat.getRootWindowInsets(root) ?: return@post
            val systemBars = rootWindowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
        }

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bar)
            insets
        }

        viewPager.adapter = ScreensAdapter(this)
        viewPager.offscreenPageLimit = PAGE_COUNT

        bottomNav.setOnItemSelectedListener { item ->
            viewPager.setCurrentItem(pageOf(item.itemId), true)
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = itemOf(position)
                if (bottomNav.selectedItemId != itemId) {
                    bottomNav.selectedItemId = itemId
                }

                titleFor(position)
                invalidateOptionsMenu()
                viewModel.setHomeVisible(position == PAGE_HOME)
            }
        })

        titleFor(PAGE_HOME)
        viewModel.setHomeVisible(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val onLogs = viewPager.currentItem == PAGE_LOGS
        val onHome = viewPager.currentItem == PAGE_HOME
        val onScripts = viewPager.currentItem == PAGE_SCRIPTS

        menu.findItem(R.id.action_logs_copy)?.isVisible = onLogs
        menu.findItem(R.id.action_logs_retry)?.isVisible = onLogs
        menu.findItem(R.id.action_logs_clear)?.isVisible = onLogs

        menu.findItem(R.id.action_sync_scripts)?.apply {
            isVisible = onScripts
            isEnabled = !syncing
        }

        menu.findItem(R.id.action_theme)?.apply {
            val night = ScriptStore.isNight(this@c)
            isVisible = onHome
            setIcon(if (night) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
            setTitle(if (night) R.string.action_theme_light else R.string.action_theme_dark)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme -> {
                val night = ScriptStore.isNight(this)
                ScriptStore.setTheme(
                    this,
                    if (night) ScriptStore.THEME_LIGHT else ScriptStore.THEME_DARK
                )
                true
            }

            R.id.action_sync_scripts -> {
                updateScripts()
                true
            }

            R.id.action_logs_copy -> {
                copyLogsToClipboard()
                true
            }

            R.id.action_logs_retry -> {
                viewModel.retryLogcatStreaming()
                true
            }

            R.id.action_logs_clear -> {
                viewModel.clearLogView()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyLogsToClipboard() {
        val logs = viewModel.getLogs()
        if (logs.isNotBlank()) {
            val clip = ClipData.newPlainText("logs", logs)
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            Toast.makeText(this, R.string.toast_logs_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private var syncing = false

    private fun updateScripts() {
        if (syncing) return

        if (!RemoteScripts.configured) {
            Toast.makeText(this, R.string.toast_scripts_no_store, Toast.LENGTH_LONG).show()
            return
        }

        syncing = true
        invalidateOptionsMenu()

        lifecycleScope.launch {
            val text = try {
                val result = viewModel.syncRemoteScripts()

                if (result.downloaded == 0) {
                    getString(R.string.toast_scripts_current)
                } else {
                    getString(R.string.toast_scripts_updated, result.downloaded)
                }
            } catch (e: Exception) {
                getString(R.string.toast_scripts_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                syncing = false
                invalidateOptionsMenu()
            }

            Toast.makeText(this@c, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun versionLabel(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            getString(
                R.string.app_version,
                info.versionName ?: "?",
                PackageInfoCompat.getLongVersionCode(info)
            )
        } catch (e: Exception) {
            ""
        }
    }

    private fun titleFor(position: Int) {
        supportActionBar?.setTitle(
            when (position) {
                PAGE_SCRIPTS -> R.string.nav_scripts
                PAGE_LOGS -> R.string.nav_logs
                else -> R.string.app_name
            }
        )

        supportActionBar?.subtitle = if (position == PAGE_HOME) versionLabel() else null
    }

    private fun pageOf(itemId: Int): Int {
        return when (itemId) {
            R.id.nav_scripts -> PAGE_SCRIPTS
            R.id.nav_logs -> PAGE_LOGS
            else -> PAGE_HOME
        }
    }

    private fun itemOf(position: Int): Int {
        return when (position) {
            PAGE_SCRIPTS -> R.id.nav_scripts
            PAGE_LOGS -> R.id.nav_logs
            else -> R.id.nav_home
        }
    }

    override fun onStart() {
        super.onStart()
        g.addServiceStateListener(this, true)
    }

    override fun onStop() {
        g.removeServiceStateListener(this)
        viewModel.setHomeVisible(false)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        viewModel.attachService(service)
    }

    private class ScreensAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

        override fun getItemCount() = PAGE_COUNT

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                PAGE_SCRIPTS -> scripts()
                PAGE_LOGS -> f()
                else -> home()
            }
        }
    }

    companion object {
        const val PAGE_HOME = 0
        const val PAGE_SCRIPTS = 1
        const val PAGE_LOGS = 2

        private const val PAGE_COUNT = 3
    }
}