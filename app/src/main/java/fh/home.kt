package fh

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
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
import com.google.android.material.card.MaterialCardView
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

        val statusBannerCard = v.findViewById<MaterialCardView>(R.id.statusBannerCard)
        val statusBannerIcon = v.findViewById<ImageView>(R.id.statusBannerIcon)
        val statusBannerTitle = v.findViewById<TextView>(R.id.statusBannerTitle)
        val statusBannerDesc = v.findViewById<TextView>(R.id.statusBannerDesc)

        val hookedCount = v.findViewById<TextView>(R.id.hookedCount)
        val hookedLabel = v.findViewById<TextView>(R.id.hookedLabel)
        val installedCount = v.findViewById<TextView>(R.id.installedCount)
        val enabledCount = v.findViewById<TextView>(R.id.enabledCount)
        val runningCount = v.findViewById<TextView>(R.id.runningCount)

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
        adapter.onItemClick = { app ->
            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                try {
                    startActivity(launchIntent)
                } catch (ignored: Exception) {
                }
            }
        }
        hookedList.layoutManager = LinearLayoutManager(requireContext())
        hookedList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Framework connection banner
                    if (state.isConnected) {
                        statusBannerCard.setCardBackgroundColor(
                            ContextCompat.getColor(requireContext(), R.color.status_green_bg)
                        )
                        statusBannerIcon.setImageResource(R.drawable.ic_check_circle)
                        statusBannerIcon.imageTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.status_green)
                        )
                        statusBannerTitle.setText(R.string.status_framework_active)
                        statusBannerTitle.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_green)
                        )
                        statusBannerDesc.setText(R.string.status_framework_active_desc)
                    } else {
                        statusBannerCard.setCardBackgroundColor(
                            ContextCompat.getColor(requireContext(), R.color.status_amber_bg)
                        )
                        statusBannerIcon.setImageResource(R.drawable.ic_warning)
                        statusBannerIcon.imageTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.status_amber)
                        )
                        statusBannerTitle.setText(R.string.status_framework_waiting)
                        statusBannerTitle.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_amber)
                        )
                        statusBannerDesc.setText(R.string.status_framework_waiting_desc)
                    }

                    hookedCount.text = state.scope.toString()

                    val running = state.hookedApps.count { it.isRunning }
                    val installed = state.hookedApps.count {
                        ScriptStore.isInstalled(requireContext(), it.packageName)
                    }

                    installedCount.text = installed.toString()
                    enabledCount.text = state.enabled.toString()
                    runningCount.text = state.runningProcessesCount.toString()

                    hookedLabel.text = if (state.scopeUnconfirmed) {
                        getString(R.string.home_scope_unconfirmed)
                    } else {
                        getString(R.string.home_hooked)
                    }

                    hookedNote.text = if (state.hookedApps.isNotEmpty()) {
                        getString(R.string.home_hooked_running, running, state.hookedApps.size)
                    } else {
                        ""
                    }

                    hookedEmpty.isVisible = state.hookedApps.isEmpty()
                    hookedList.isVisible = state.hookedApps.isNotEmpty()

                    adapter.submitList(state.hookedApps)
                }
            }
        }

        return v
    }

    private class AppAdapter(private val context: Context) :
        ListAdapter<HookedApp, AppViewHolder>(DiffCallback) {

        var onMenu: ((View, HookedApp) -> Unit)? = null
        var onItemClick: ((HookedApp) -> Unit)? = null

        private val icons = LruCache<String, Drawable>(64)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = getItem(position)
            holder.label.text = app.label
            holder.packageName.text = app.packageName

            val cached = icons.get(app.packageName)
            if (cached != null) {
                holder.icon.setImageDrawable(cached)
            } else {
                val icon = ScriptStore.appIcon(context, app.packageName)
                if (icon != null) {
                    icons.put(app.packageName, icon)
                    holder.icon.setImageDrawable(icon)
                } else {
                    holder.icon.setImageResource(
                        if (HookedProviders.handles(app.packageName)) R.drawable.ic_extension
                        else R.drawable.ic_description
                    )
                }
            }

            val running = app.running
            if (running.isNotEmpty()) {
                holder.running.isVisible = true
                holder.running.text = "● " + runningLabel(context, running)
            } else {
                holder.running.isVisible = false
            }

            holder.menu.setOnClickListener { onMenu?.invoke(it, app) }
            holder.itemView.setOnClickListener { onItemClick?.invoke(app) }
        }

        private fun runningLabel(context: Context, processes: List<RunningProcess>): String {
            if (processes.size == 1) {
                val process = processes.first()
                val stateText = when (process.state) {
                    RunningState.CURRENT -> context.getString(R.string.home_running)
                    RunningState.STALE -> context.getString(R.string.home_running_stale)
                    RunningState.RELOADING -> context.getString(R.string.home_running_reloading)
                    RunningState.FAILED -> context.getString(R.string.home_running_failed)
                }
                return " ()"
            }
            return context.getString(R.string.home_running_processes, processes.size)
        }

        private object DiffCallback : DiffUtil.ItemCallback<HookedApp>() {
            override fun areItemsTheSame(oldItem: HookedApp, newItem: HookedApp) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: HookedApp, newItem: HookedApp) =
                oldItem == newItem
        }
    }

    private class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
        val packageName: TextView = view.findViewById(R.id.appPackage)
        val running: TextView = view.findViewById(R.id.appRunning)
        val menu: ImageButton = view.findViewById(R.id.appMenu)
    }
}