package fh

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuck.iab.HookedApp
import com.fuck.iab.HookedProviders
import com.fuck.iab.R
import com.fuck.iab.RunningProcess
import com.fuck.iab.RunningState
import com.fuck.iab.ScriptSource
import com.fuck.iab.ScriptStore
import kotlinx.coroutines.launch

// home screen: module state, counters and the apps in scope
class home : Fragment() {

    private val viewModel: h by activityViewModels()
    private lateinit var adapter: AppAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.page_home, container, false)

        val hookedCount = v.findViewById<TextView>(R.id.hookedCount)
        val hookedLabel = v.findViewById<TextView>(R.id.hookedLabel)
        val installedCount = v.findViewById<TextView>(R.id.installedCount)
        val enabledCount = v.findViewById<TextView>(R.id.enabledCount)
        val hookedEmpty = v.findViewById<TextView>(R.id.hookedEmpty)
        val hookedNote = v.findViewById<TextView>(R.id.hookedNote)
        val hookedList = v.findViewById<RecyclerView>(R.id.hookedList)

        adapter = AppAdapter(requireContext())
        adapter.onMenu = { anchor, app ->
            showAppMenu(
                requireContext(), anchor, app.packageName,
                ScriptStore.isInstalled(requireContext(), app.packageName),
                viewLifecycleOwner.lifecycleScope
            )
        }
        hookedList.layoutManager = LinearLayoutManager(requireContext())
        hookedList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    hookedCount.text = state.scope.toString()

                    val running = state.hookedApps.count { it.isRunning }
                    hookedLabel.text = if (running > 0) {
                        getString(R.string.home_hooked_running, running, state.scope)
                    } else {
                        getString(R.string.home_hooked)
                    }

                    installedCount.text = state.scripts.size.toString()
                    enabledCount.text = state.enabled.toString()
                    // Never claim there are no apps in scope while the framework has not
                    // actually reported the scope.
                    hookedEmpty.isVisible = state.hookedApps.isEmpty()
                    hookedEmpty.setText(
                        if (state.scopeUnconfirmed) R.string.home_scope_unknown else R.string.home_hooked_empty
                    )
                    hookedNote.isVisible = state.scopeUnconfirmed && state.hookedApps.isNotEmpty()
                    adapter.submitList(state.hookedApps)
                }
            }
        }

        return v
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

private class AppAdapter(private val context: Context) : ListAdapter<HookedApp, AppAdapter.Holder>(DIFF) {

    private val icons = LruCache<String, Drawable>(64)

    var onMenu: ((View, HookedApp) -> Unit)? = null

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
        val packageName: TextView = view.findViewById(R.id.appPackage)
        val running: TextView = view.findViewById(R.id.appRunning)
        val menu: ImageButton = view.findViewById(R.id.appMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = getItem(position)

        holder.label.text = app.label
        holder.packageName.text = app.packageName

        val running = runningLabel(app.running)
        holder.running.text = running
        holder.running.isVisible = running != null

        holder.icon.setImageDrawable(iconFor(app.packageName))

        holder.itemView.setOnClickListener { open(app.packageName) }
        holder.menu.setOnClickListener { onMenu?.invoke(holder.menu, app) }
    }

    /** Tapping a row opens the app itself, which is what the list is about. */
    private fun open(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    /** "running · pid 1234", or null when the module is not loaded in that app. */
    private fun runningLabel(processes: List<RunningProcess>): String? {
        val first = processes.firstOrNull() ?: return null

        val state = when (first.state) {
            RunningState.CURRENT -> R.string.home_running
            RunningState.STALE -> R.string.home_running_stale
            RunningState.RELOADING -> R.string.home_running_reloading
            RunningState.FAILED -> R.string.home_running_failed
        }

        val detail = if (processes.size == 1) {
            context.getString(R.string.home_running_pid, first.pid)
        } else {
            context.getString(R.string.home_running_processes, processes.size)
        }

        return context.getString(state) + " · " + detail
    }

    private fun iconFor(packageName: String): Drawable? {
        icons.get(packageName)?.let { return it }

        val drawable = ScriptStore.appIcon(context, packageName) ?: return null
        icons.put(packageName, drawable)
        return drawable
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HookedApp>() {
            override fun areItemsTheSame(oldItem: HookedApp, newItem: HookedApp) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: HookedApp, newItem: HookedApp) = oldItem == newItem
        }
    }
}

/**
 * What a script covers: a store handled by the module's own hook runs no script at all, an app
 * outside the scope never runs one either, and otherwise it is one app per package.
 */
/**
 * What the script covers, or null when there is nothing worth saying. A script that covers one
 * app says nothing the row does not already show, so that case stays out.
 */
fun coverageLabel(
    context: Context,
    packageName: String,
    coveredApps: Int,
    inScope: Boolean,
    installed: Boolean
): String? {
    if (HookedProviders.handles(packageName)) return context.getString(R.string.scripts_hook_handled)
    if (!installed) return context.getString(R.string.scripts_not_installed)
    if (!inScope) return context.getString(R.string.scripts_not_in_scope)
    if (coveredApps <= 1) return null
    return context.getString(R.string.scripts_covers_many, coveredApps)
}

fun sourceLabel(context: Context, source: ScriptSource): String {
    return when (source) {
        ScriptSource.USER -> context.getString(R.string.source_user)
        ScriptSource.REMOTE -> context.getString(R.string.source_remote)
    }
}
