package fh

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.fuck.iab.Connected_to_
import com.fuck.iab.Not_connected_to_framework
import com.fuck.iab.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch

// main activity: home, scripts and logs behind a bottom navigation bar
class MainActivity : AppCompatActivity(), MainApplication.ServiceStateListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        applySystemBarIcons(this, root)
        root.post {
            val rootWindowInsets = ViewCompat.getRootWindowInsets(root) ?: return@post
            val systemBars = rootWindowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // The bottom bar sits in the navigation bar area itself, so the root keeps no
            // bottom padding and the bar's surface fills that strip instead of leaving a gap.
            root.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
        }

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        // The bar keeps its own background down to the navigation bar, using that height as
        // padding for the labels instead of leaving an empty strip below the app.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bar)
            insets
        }

        viewPager.adapter = ScreensAdapter(this)
        viewPager.offscreenPageLimit = 2
        viewPager.isUserInputEnabled = false

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
            }
        })

        titleFor(PAGE_SCRIPTS)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // The log actions belong to the logs screen, so the toolbar only offers what the
        // current page can do.
        val onLogs = viewPager.currentItem == PAGE_LOGS
        val onHome = viewPager.currentItem == PAGE_SCRIPTS

        menu.findItem(R.id.action_logs_retry)?.isVisible = onLogs
        menu.findItem(R.id.action_logs_clear)?.isVisible = onLogs

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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

    /** "version 1.6.4 (38)", taken from the installed package. */
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
        supportActionBar?.setTitle(R.string.app_name)

        // The build number belongs to the home page, not to every screen.

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    supportActionBar?.subtitle = if (state.connectionStatus == 1) {
                        "${Connected_to_()}${state.framework}"
                    } else {
                        Not_connected_to_framework()
                    }
                }
            }
        }
    }

    private fun pageOf(itemId: Int): Int {
        return when (itemId) {
            R.id.nav_logs -> PAGE_LOGS
            else -> PAGE_SCRIPTS
        }
    }

    private fun itemOf(position: Int): Int {
        return when (position) {
            PAGE_LOGS -> R.id.nav_logs
            else -> R.id.nav_scripts
        }
    }

    override fun onStart() {
        super.onStart()
        MainApplication.addServiceStateListener(this, true)
    }

    override fun onStop() {
        MainApplication.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        viewModel.attachService(service)
    }

    private class ScreensAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

        override fun getItemCount() = PAGE_COUNT

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                PAGE_LOGS -> LogFragment()
                else -> ScriptsFragment()
            }
        }
    }

    companion object {
        const val PAGE_SCRIPTS = 0
        const val PAGE_LOGS = 1

        private const val PAGE_COUNT = 2
    }
}
