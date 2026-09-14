package fh

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.fuck.iab.Error__
import com.fuck.iab.FKIAB_V
import com.fuck.iab.FRIDA_V
import com.fuck.iab.Log
import com.fuck.iab.Not_connected_to_framework_yet
import com.fuck.iab.R
import com.fuck.iab.Script
import com.fuck.iab.Script_saved
import com.fuck.iab.__A_Z__
import com.fuck.iab._c
import com.fuck.iab._s
import com.fuck.iab._s_
import com.fuck.iab._v
import com.fuck.iab.logcat
import com.fuck.iab.su
import com.fuck.iab.tag
import com.fuck.iab.user_script_js
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import fh.g.removeServiceStateListener
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream

// main activity
class c : AppCompatActivity(), g.ServiceStateListener {

    private var xposedService: XposedService? = null
    private var logcatJob: Job? = null
    private lateinit var viewPager: ViewPager2

    private lateinit var dummyStBg: View

    private var scriptFragment = e({ loadUserScript() }, { saveUserScript() })
    private var logFragment = f({ startLogcatStreaming() })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dummyStBg = findViewById(R.id.dummy_st_bg)

        val root = findViewById<View?>(R.id.root)!!
        root.post {
            val rootWindowInsets = ViewCompat.getRootWindowInsets(root) ?: return@post
            val systemBars = rootWindowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(
                systemBars.left, systemBars.top,
                systemBars.right, systemBars.bottom
            )
            val lp: ViewGroup.LayoutParams = dummyStBg.layoutParams
            lp.height = systemBars.top
            dummyStBg.setLayoutParams(lp)
        }

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = object : FragmentStateAdapter(this) {

            override fun getItemCount() = 2

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> {
                        scriptFragment = e({ loadUserScript() }, { saveUserScript() })
                        scriptFragment
                    }

                    else -> {
                        logFragment = f({ startLogcatStreaming() })
                        logFragment
                    }
                }
            }
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addTab(tabLayout.newTab().setText(Script()))
        tabLayout.addTab(tabLayout.newTab().setText(Log()))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        TabLayoutMediator(
            tabLayout,
            viewPager
        ) { tab, position ->
            tab.text = if (position == 0) Script() else Log()
        }.attach()
    }

    override fun onDestroy() {
        super.onDestroy()
        logcatJob?.cancel()
    }

    private fun loadUserScript() {
        val service = xposedService ?: return
        try {
            val pfd = service.openRemoteFile(user_script_js())
            val content = FileInputStream(pfd.fileDescriptor).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            scriptFragment.scriptInput.setText(content)
        } catch (e: Exception) {
        }
    }

    private fun saveUserScript() {
        val service = xposedService
        if (service == null) {
            Toast.makeText(this, Not_connected_to_framework_yet(), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val bytes = scriptFragment.scriptInput.getText().toString().toByteArray(Charsets.UTF_8)
            val pfd = service.openRemoteFile(user_script_js())
            FileOutputStream(pfd.fileDescriptor).use { fos ->
                fos.channel.truncate(0)
                fos.write(bytes)
            }
            Toast.makeText(this, Script_saved(), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "${Error__()}${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLogcatStreaming() {
        logcatJob?.cancel()

        logFragment.logcatView?.text = ""

        logcatJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        su(),
                        _c(),
                        logcat(),
                        _v(),
                        tag(),
                        _s(),
                        FKIAB_V(),
                        FRIDA_V()
                    )
                )
                val reader = process.inputStream.bufferedReader()
                while (isActive) {
                    val line = reader.readLine() ?: break

                    val index = line.indexOf(": ")

                    val clean = if (index != -1) {
                        line.substring(0, index)
                            .replace(Regex(__A_Z__()), "")
                            .trim() +
                                _s_() +
                                line.substring(index + 2)
                    } else {
                        line
                    }

                    withContext(Dispatchers.Main) {
                        logFragment.appendText(clean + "\n")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logFragment.appendText("${Error__()}${e.message}\n")
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        g.addServiceStateListener(this, true)
    }

    override fun onStop() {
        removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        if (service != null) {
            xposedService = service
            runOnUiThread { loadUserScript() }
        } else {
            xposedService = null
        }
    }
}