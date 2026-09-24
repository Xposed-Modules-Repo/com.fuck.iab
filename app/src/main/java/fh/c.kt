package fh

import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.fuck.iab.Connected_to_Xposed_service__
import com.fuck.iab.Error__
import com.fuck.iab.Log
import com.fuck.iab.Not_connected_to_Xposed_service_
import com.fuck.iab.Not_connected_to_framework
import com.fuck.iab.R
import com.fuck.iab.Script
import com.fuck.iab.Script_saved
import com.fuck.iab.user_script_js
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import fh.g.removeServiceStateListener
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream


// main activity
class c : AppCompatActivity(), g.ServiceStateListener {

    private var xposedService: XposedService? = null
    private lateinit var viewPager: ViewPager2
    private lateinit var status: TextView

    private lateinit var dummyStBg: View

    private val viewModel: h by viewModels()

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

        status = findViewById(R.id.scriptInputStatus)
        status.text = Not_connected_to_Xposed_service_()

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = object : FragmentStateAdapter(this) {

            override fun getItemCount() = 2

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> e()
                    else -> f()
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logInit.collect {
                    viewModel.startLogcatStreaming()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveRequest.collect { text ->
                    saveUserScript(text)
                }
            }
        }
    }

    private fun loadUserScript() {
        if (viewModel.scriptLoaded) return
        val service = xposedService ?: return
        try {
            val pfd = service.openRemoteFile(user_script_js())
//            val path = Os.readlink("/proc/self/fd/" + pfd.fd)
            val content = FileInputStream(pfd.fileDescriptor).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
//            val content = service.getRemotePreferences(fkiab()).getString(user_script_js(), "")!!
//            viewModel.setScriptTextStatus("loaded from: $path")
            viewModel.scriptLoaded = true
            viewModel.setScriptText(content)
        } catch (e: Exception) {
//            viewModel.setScriptTextStatus("Error while loading script: ${e.message ?: ""}")
        }
    }

    private fun saveUserScript(text: String) {
        val service = xposedService
        if (service == null) {
            Toast.makeText(this, Not_connected_to_framework(), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val pfd = service.openRemoteFile(user_script_js())
//            val path = Os.readlink("/proc/self/fd/" + pfd.fd)
//            viewModel.setScriptTextStatus("Saved to: $path")
            FileOutputStream(pfd.fileDescriptor).use { fos ->
                fos.channel.truncate(0)
                fos.write(bytes)
            }
//            service.getRemotePreferences(fkiab()).edit(commit = true) {
//                putString(user_script_js(), text)
//            }
            Toast.makeText(this, Script_saved(), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "${Error__()}${e.message}", Toast.LENGTH_SHORT).show()
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
            runOnUiThread {
                status.text = "${Connected_to_Xposed_service__()}${service.frameworkName} ${service.frameworkVersionCode}"
                loadUserScript()
            }
        } else {
            xposedService = null
            runOnUiThread {
                status.text = Not_connected_to_Xposed_service_()
            }
        }
    }
}