package fh

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuck.iab.HookedProviders
import com.fuck.iab.R
import com.fuck.iab._c
import com.fuck.iab.su
import com.fuck.iab.ScriptDetails
import com.fuck.iab.ScriptInfo
import com.fuck.iab.ScriptSource
import com.fuck.iab.ScriptStore
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An app the new script dialog can offer. */
data class AppChoice(val label: String, val packageName: String)

// scripts screen: every known script, with a switch to enable or disable it per app
class scripts : Fragment() {

    private val viewModel: h by activityViewModels()
    private lateinit var adapter: ScriptAdapter
    private var pickerDialog: Dialog? = null
    private var sheet: BottomSheetDialog? = null

    private var query: String = ""
    private var filter: ScriptSource? = null
    private var all: List<ScriptInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.page_scripts, container, false)

        val list = v.findViewById<RecyclerView>(R.id.scriptsList)
        val empty = v.findViewById<TextView>(R.id.scriptsEmpty)
        val newScript = v.findViewById<ExtendedFloatingActionButton>(R.id.newScriptButton)
        val search = v.findViewById<TextInputEditText>(R.id.searchInput)

        adapter = ScriptAdapter(requireContext()) { script -> showDetails(script) }
        adapter.onEnabledChanged = { packageName, enabled -> viewModel.setEnabled(packageName, enabled) }
        adapter.onMenu = { anchor, script -> showItemMenu(anchor, script) }

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        newScript.setOnClickListener { pickPackage() }

        search.doAfterTextChanged {
            query = it?.toString().orEmpty()
            applyFilters(empty)
        }

        v.findViewById<ChipGroup>(R.id.filterGroup)
            .setOnCheckedStateChangeListener { _, checkedIds ->
                filter = when (checkedIds.firstOrNull()) {
                    R.id.filterStore -> ScriptSource.BUNDLED
                    R.id.filterUser -> ScriptSource.USER
                    else -> null
                }

                applyFilters(empty)
            }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    all = state.scripts
                    applyFilters(empty)
                }
            }
        }

        return v
    }

    override fun onResume() {
        super.onResume()
        // Picks up scripts saved in the editor.
        viewModel.refresh()
    }

    override fun onDestroyView() {
        pickerDialog?.dismiss()
        pickerDialog = null
        sheet?.dismiss()
        sheet = null
        super.onDestroyView()
    }

    private fun applyFilters(empty: TextView) {
        val needle = query.trim()

        val visible = all.filter { script ->
            val matchesFilter = filter == null || script.source == filter
            val matchesQuery = needle.isEmpty() ||
                    script.packageName.contains(needle, true) ||
                    ScriptStore.appLabel(requireContext(), script.packageName).contains(needle, true)

            matchesFilter && matchesQuery
        }

        adapter.submitList(visible)
        empty.setText(if (all.isEmpty()) R.string.scripts_empty else R.string.scripts_nothing_matches)
        empty.isVisible = visible.isEmpty()
    }

    private fun openEditor(packageName: String) {
        startActivity(editor.intent(requireContext(), packageName))
    }

    // ---- per-app menu ----------------------------------------------------------

    private fun showItemMenu(anchor: View, script: ScriptInfo) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_script_item, popup.menu)

        // Nothing to inspect or stop for an app that is no longer on the device, but the
        // store page still exists.
        popup.menu.findItem(R.id.action_app_info).isEnabled = script.installed
        popup.menu.findItem(R.id.action_force_stop).isEnabled = script.installed

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_app_info -> {
                    openAppInfo(script.packageName)
                    true
                }

                R.id.action_force_stop -> {
                    stopApp(script.packageName)
                    true
                }

                R.id.action_play_store -> {
                    openPlayStore(script.packageName)
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            startActivity(intent)
        } catch (e: Exception) {
        }
    }

    /** Force stopping another app needs root, so the failure is reported instead of ignored. */
    private fun stopApp(packageName: String) {
        val context = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch {
            val stopped = withContext(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec(
                        arrayOf(su(), _c(), "am force-stop $packageName")
                    )
                    process.waitFor() == 0
                } catch (e: Exception) {
                    false
                }
            }

            if (!stopped) {
                Toast.makeText(context, R.string.menu_force_stop_no_su, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openPlayStore(packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .setPackage(HookedProviders.PLAY_STORE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            startActivity(market)
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            } catch (e: Exception) {
            }
        }
    }

    // ---- detail sheet ----------------------------------------------------------

    private fun showDetails(script: ScriptInfo) {
        val view = layoutInflater.inflate(R.layout.sheet_script_details, null)
        val context = requireContext()

        view.findViewById<ImageView>(R.id.detailIcon).setImageDrawable(
            ScriptStore.appIcon(context, script.packageName) ?: context.getDrawable(R.drawable.ic_description)
        )
        view.findViewById<TextView>(R.id.detailLabel).text = ScriptStore.appLabel(context, script.packageName)
        view.findViewById<TextView>(R.id.detailPackage).text = script.packageName

        val sheetDialog = BottomSheetDialog(context)
        sheetDialog.setContentView(view)
        sheet = sheetDialog

        view.findViewById<MaterialButton>(R.id.detailEdit).setOnClickListener {
            sheetDialog.dismiss()
            openEditor(script.packageName)
        }

        sheetDialog.show()

        val service = g.getService()

        viewLifecycleOwner.lifecycleScope.launch {
            val details = withContext(Dispatchers.IO) {
                ScriptStore.details(context, service, script.packageName)
            }

            bindDetails(view, details)
        }
    }

    private fun bindDetails(view: View, details: ScriptDetails) {
        val context = requireContext()

        // Source, size and author, once each, on one line.
        val summary = buildList {
            add(sourceLabel(context, details.source))
            add(ScriptStore.sizeLabel(details.size))
            details.author?.let { add(it) }
            if (details.hasUserScript) add(getString(R.string.source_user))
        }
        view.findViewById<TextView>(R.id.detailSummary).text = summary.joinToString(" · ")

        val covers = coverageLabel(
            context, details.packageName, details.coveredApps, details.inScope, details.installed
        )
        view.findViewById<TextView>(R.id.detailCovers).text = covers ?: ""
        view.findViewById<View>(R.id.detailCoversRow).isVisible = covers != null

        view.findViewById<TextView>(R.id.detailStatus).text =
            getString(if (details.enabled) R.string.status_enabled else R.string.status_disabled)

        view.findViewById<TextView>(R.id.detailFile).text = details.file ?: "-"
        view.findViewById<View>(R.id.detailFileRow).isVisible = details.file != null

        view.findViewById<TextView>(R.id.detailChecksum).text = details.sha256 ?: "-"
    }

    // ---- new script picker -----------------------------------------------------

    /** Asks which app the new script is for, instead of making the user type a package name. */
    private fun pickPackage() {
        val view = layoutInflater.inflate(R.layout.dialog_new_script, null)
        val input = view.findViewById<TextInputEditText>(R.id.searchInput)
        val list = view.findViewById<RecyclerView>(R.id.choiceList)
        val empty = view.findViewById<TextView>(R.id.choiceEmpty)

        val choices = ChoiceAdapter(requireContext()) { packageName ->
            pickerDialog?.dismiss()
            openEditor(packageName)
        }

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = choices

        pickerDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.scripts_new_title)
            .setView(view)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        val context = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch {
            val candidates = withContext(Dispatchers.IO) { candidates(context) }

            fun show(needle: String) {
                val trimmed = needle.trim()

                val filtered = if (trimmed.isEmpty()) {
                    candidates
                } else {
                    candidates.filter {
                        it.label.contains(trimmed, true) || it.packageName.contains(trimmed, true)
                    }
                }

                choices.submitList(filtered)
                empty.isVisible = filtered.isEmpty()
            }

            show("")
            input.doAfterTextChanged { show(it?.toString().orEmpty()) }
        }

        pickerDialog?.show()
    }

    private fun candidates(context: Context): List<AppChoice> {
        val state = viewModel.state.value
        val packages = LinkedHashSet<String>()

        // Apps in scope and apps that already have a script come first.
        state.hookedApps.forEach { packages += it.packageName }
        state.scripts.forEach { packages += it.packageName }
        packages += ScriptStore.installedApps(context)
        packages -= ScriptStore.ALL_APPS

        return packages
            .map { AppChoice(ScriptStore.appLabel(context, it), it) }
            .sortedBy { it.label.lowercase() }
    }
}

private class ChoiceAdapter(
    private val context: Context,
    private val onClick: (String) -> Unit
) : ListAdapter<AppChoice, ChoiceAdapter.Holder>(DIFF) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.choiceIcon)
        val label: TextView = view.findViewById(R.id.choiceLabel)
        val packageName: TextView = view.findViewById(R.id.choicePackage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_choice, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)

        holder.label.text = item.label
        holder.packageName.text = item.packageName
        holder.icon.setImageDrawable(
            ScriptStore.appIcon(context, item.packageName) ?: context.getDrawable(R.drawable.ic_extension)
        )
        holder.itemView.setOnClickListener { onClick(item.packageName) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppChoice>() {
            override fun areItemsTheSame(oldItem: AppChoice, newItem: AppChoice) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppChoice, newItem: AppChoice) = oldItem == newItem
        }
    }
}

private class ScriptAdapter(
    private val context: Context,
    private val onClick: (ScriptInfo) -> Unit
) : ListAdapter<ScriptInfo, ScriptAdapter.Holder>(DIFF) {

    var onEnabledChanged: ((String, Boolean) -> Unit)? = null
    var onMenu: ((View, ScriptInfo) -> Unit)? = null

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.scriptIcon)
        val label: TextView = view.findViewById(R.id.scriptLabel)
        val packageName: TextView = view.findViewById(R.id.scriptPackage)
        val meta: TextView = view.findViewById(R.id.scriptMeta)
        val covers: TextView = view.findViewById(R.id.scriptCovers)
        val menu: ImageButton = view.findViewById(R.id.scriptMenu)
        val switch: MaterialSwitch = view.findViewById(R.id.scriptSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false))
        holder.switch.setOnCheckedChangeListener { button, isChecked ->
            val item = button.tag as? ScriptInfo ?: return@setOnCheckedChangeListener
            if (item.enabled != isChecked) onEnabledChanged?.invoke(item.packageName, isChecked)
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)

        holder.menu.setOnClickListener { onMenu?.invoke(holder.menu, item) }

        val isAllApps = item.packageName == ScriptStore.ALL_APPS

        holder.label.text = when {
            isAllApps -> context.getString(R.string.scripts_all_apps)
            item.installed -> ScriptStore.appLabel(context, item.packageName)
            // Nothing but the package name is known for an app that is gone.
            else -> item.packageName
        }

        // The package name would only repeat the label for an app that is not installed.
        holder.packageName.text = item.packageName
        holder.packageName.isVisible = isAllApps || item.installed

        val parts = mutableListOf(sourceLabel(context, item.source))

        if (item.size > 0) parts += ScriptStore.sizeLabel(item.size)
        item.author?.let { parts += it }

        holder.meta.text = parts.joinToString(" · ")
        val covers = coverageLabel(
            context, item.packageName, item.coveredApps, item.inScope, item.installed
        )
        holder.covers.text = covers ?: ""
        holder.covers.isVisible = covers != null
        holder.icon.setImageDrawable(
            ScriptStore.appIcon(context, item.packageName) ?: context.getDrawable(R.drawable.ic_description)
        )

        holder.switch.tag = item
        holder.switch.isChecked = item.enabled

        holder.itemView.setOnClickListener { onClick(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScriptInfo>() {
            override fun areItemsTheSame(oldItem: ScriptInfo, newItem: ScriptInfo) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: ScriptInfo, newItem: ScriptInfo) = oldItem == newItem
        }
    }
}
