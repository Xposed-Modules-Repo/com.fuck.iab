package fh

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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
import com.fuck.iab.ScriptDetails
import com.fuck.iab.ScriptInfo
import com.fuck.iab.ScriptSource
import com.fuck.iab.ScriptStore
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
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

    private lateinit var filterAllChip: Chip
    private lateinit var filterSyncedChip: Chip
    private lateinit var filterUserChip: Chip

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

        val filterGroup = v.findViewById<ChipGroup>(R.id.filterGroup)
        filterAllChip = v.findViewById(R.id.filterAll)
        filterSyncedChip = v.findViewById(R.id.filterSynced)
        filterUserChip = v.findViewById(R.id.filterUser)

        adapter = ScriptAdapter(requireContext()) { script -> showDetails(script) }
        adapter.onEnabledChanged = { packageName, enabled -> viewModel.setEnabled(packageName, enabled) }
        adapter.onMenu = { anchor, script ->
            showAppMenu(
                requireContext(), anchor, script.packageName, script.installed,
                viewLifecycleOwner.lifecycleScope
            )
        }

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        newScript.setOnClickListener { showChoiceDialog() }

        search.doAfterTextChanged { text ->
            query = text?.toString().orEmpty().trim()
            applyFilter()
        }

        filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.filterSynced -> ScriptSource.REMOTE
                R.id.filterUser -> ScriptSource.USER
                else -> null
            }
            applyFilter()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    all = state.scripts
                    viewModel.syncIfEmpty(all.size)
                    updateFilterChipLabels()
                    applyFilter()
                }
            }
        }

        return v
    }

    private fun updateFilterChipLabels() {
        val allCount = all.size
        val syncedCount = all.count { it.source == ScriptSource.REMOTE }
        val userCount = all.count { it.source == ScriptSource.USER }

        filterAllChip.text = getString(R.string.filter_all, allCount)
        filterSyncedChip.text = getString(R.string.filter_synced, syncedCount)
        filterUserChip.text = getString(R.string.filter_user, userCount)
    }

    private fun applyFilter() {
        val view = view ?: return
        val empty = view.findViewById<TextView>(R.id.scriptsEmpty)

        val visible = all
            .filter { script ->
                filter == null || script.source == filter
            }
            .filter { script ->
                if (query.isEmpty()) return@filter true

                val label = script.name ?: ScriptStore.appLabel(requireContext(), script.packageName)
                label.contains(query, ignoreCase = true) ||
                        script.packageName.contains(query, ignoreCase = true)
            }

        adapter.submitList(visible)

        if (visible.isEmpty()) {
            empty.isVisible = true
            empty.text = if (query.isNotEmpty()) {
                getString(R.string.scripts_nothing_matches, query)
            } else {
                getString(R.string.scripts_empty)
            }
        } else {
            empty.isVisible = false
        }
    }

    private fun showDetails(info: ScriptInfo) {
        val service = fh.g.getService() ?: return
        val currentSheet = BottomSheetDialog(requireContext())
        sheet = currentSheet

        val v = layoutInflater.inflate(R.layout.sheet_script_details, null)
        currentSheet.setContentView(v)

        val icon = v.findViewById<ImageView>(R.id.detailIcon)
        val label = v.findViewById<TextView>(R.id.detailLabel)
        val packageName = v.findViewById<TextView>(R.id.detailPackage)
        val summary = v.findViewById<TextView>(R.id.detailSummary)
        val status = v.findViewById<TextView>(R.id.detailStatus)
        val covers = v.findViewById<TextView>(R.id.detailCovers)
        val coversRow = v.findViewById<View>(R.id.detailCoversRow)
        val file = v.findViewById<TextView>(R.id.detailFile)
        val fileRow = v.findViewById<View>(R.id.detailFileRow)
        val checksum = v.findViewById<TextView>(R.id.detailChecksum)
        val copyChecksum = v.findViewById<ImageButton>(R.id.detailCopyChecksum)
        val edit = v.findViewById<MaterialButton>(R.id.detailEdit)

        label.text = info.name ?: ScriptStore.appLabel(requireContext(), info.packageName)
        packageName.text = info.packageName

        val appIcon = ScriptStore.appIcon(requireContext(), info.packageName)
        if (appIcon != null) {
            icon.setImageDrawable(appIcon)
        } else {
            icon.setImageResource(
                if (HookedProviders.handles(info.packageName)) R.drawable.ic_extension
                else R.drawable.ic_description
            )
        }

        edit.setOnClickListener {
            currentSheet.dismiss()
            startActivity(editor.intent(requireContext(), info.packageName))
        }

        copyChecksum.setOnClickListener {
            val cs = checksum.text.toString()
            if (cs.isNotBlank()) {
                val clip = ClipData.newPlainText("checksum", cs)
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.toast_checksum_copied, Toast.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val details = withContext(Dispatchers.IO) {
                ScriptStore.details(requireContext(), service, info.packageName)
            }

            status.text = getString(
                if (details.enabled) R.string.status_enabled else R.string.status_disabled
            )

            val sourceText = sourceLabel(requireContext(), details.source)
            val sizeText = ScriptStore.sizeLabel(details.size)
            summary.text = if (details.author == null) {
                " • "
            } else {
                " •  • " + getString(R.string.editor_author, details.author)
            }

            if (details.coveredApps > 1) {
                coversRow.isVisible = true
                covers.text = getString(R.string.scripts_covers_many, details.coveredApps)
            } else {
                coversRow.isVisible = false
            }

            if (details.file != null) {
                fileRow.isVisible = true
                file.text = details.file
            } else {
                fileRow.isVisible = false
            }

            checksum.text = details.sha256 ?: ""
        }

        currentSheet.show()
    }

    private fun showChoiceDialog() {
        val context = requireContext()
        val dialog = Dialog(context)
        pickerDialog = dialog

        val v = layoutInflater.inflate(R.layout.dialog_new_script, null)
        dialog.setContentView(v)

        val search = v.findViewById<TextInputEditText>(R.id.searchInput)
        val list = v.findViewById<RecyclerView>(R.id.choiceList)
        val empty = v.findViewById<TextView>(R.id.choiceEmpty)

        val choiceAdapter = ChoiceAdapter(context) { choice ->
            dialog.dismiss()
            startActivity(editor.intent(context, choice.packageName))
        }

        list.layoutManager = LinearLayoutManager(context)
        list.adapter = choiceAdapter

        var fullList: List<AppChoice> = emptyList()

        viewLifecycleOwner.lifecycleScope.launch {
            fullList = withContext(Dispatchers.IO) {
                ScriptStore.installedApps(context)
                    .map { AppChoice(ScriptStore.appLabel(context, it), it) }
                    .sortedBy { it.label.lowercase() }
            }
            choiceAdapter.submitList(fullList)
        }

        search.doAfterTextChanged { text ->
            val q = text?.toString().orEmpty().trim()
            val filtered = if (q.isEmpty()) {
                fullList
            } else {
                fullList.filter {
                    it.label.contains(q, ignoreCase = true) ||
                            it.packageName.contains(q, ignoreCase = true)
                }
            }
            choiceAdapter.submitList(filtered)
            empty.isVisible = filtered.isEmpty()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        sheet?.dismiss()
        pickerDialog?.dismiss()
        super.onDestroyView()
    }

    private class ScriptAdapter(
        private val context: Context,
        private val onClick: (ScriptInfo) -> Unit
    ) : ListAdapter<ScriptInfo, ScriptViewHolder>(DiffCallback) {

        var onEnabledChanged: ((String, Boolean) -> Unit)? = null
        var onMenu: ((View, ScriptInfo) -> Unit)? = null

        private val icons = LruCache<String, Drawable>(64)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScriptViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
            return ScriptViewHolder(view)
        }

        override fun onBindViewHolder(holder: ScriptViewHolder, position: Int) {
            val script = getItem(position)

            holder.label.text = script.name ?: ScriptStore.appLabel(context, script.packageName)
            holder.packageName.text = script.packageName

            val cached = icons.get(script.packageName)
            if (cached != null) {
                holder.icon.setImageDrawable(cached)
            } else {
                val icon = ScriptStore.appIcon(context, script.packageName)
                if (icon != null) {
                    icons.put(script.packageName, icon)
                    holder.icon.setImageDrawable(icon)
                } else {
                    holder.icon.setImageResource(
                        if (HookedProviders.handles(script.packageName)) R.drawable.ic_extension
                        else R.drawable.ic_description
                    )
                }
            }

            val sourceText = sourceLabel(context, script.source)
            val sizeText = ScriptStore.sizeLabel(script.size)
            holder.meta.text = if (script.author != null) {
                " •  • "
            } else {
                " • "
            }

            if (!script.installed) {
                holder.scope.isVisible = true
                holder.scope.setText(R.string.scripts_not_installed)
            } else if (!script.inScope) {
                holder.scope.isVisible = true
                holder.scope.setText(R.string.scripts_not_in_scope)
            } else {
                holder.scope.isVisible = false
            }

            holder.enabledSwitch.setOnCheckedChangeListener(null)
            holder.enabledSwitch.isChecked = script.enabled
            holder.enabledSwitch.setOnCheckedChangeListener { _, checked ->
                onEnabledChanged?.invoke(script.packageName, checked)
            }

            holder.menu.setOnClickListener { onMenu?.invoke(it, script) }
            holder.itemView.setOnClickListener { onClick(script) }
        }

        private object DiffCallback : DiffUtil.ItemCallback<ScriptInfo>() {
            override fun areItemsTheSame(oldItem: ScriptInfo, newItem: ScriptInfo) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: ScriptInfo, newItem: ScriptInfo) =
                oldItem == newItem
        }
    }

    private class ScriptViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.scriptIcon)
        val label: TextView = view.findViewById(R.id.scriptLabel)
        val packageName: TextView = view.findViewById(R.id.scriptPackage)
        val meta: TextView = view.findViewById(R.id.scriptMeta)
        val scope: TextView = view.findViewById(R.id.scriptScope)
        val enabledSwitch: MaterialSwitch = view.findViewById(R.id.scriptSwitch)
        val menu: ImageButton = view.findViewById(R.id.scriptMenu)
    }

    private class ChoiceAdapter(
        private val context: Context,
        private val onSelect: (AppChoice) -> Unit
    ) : ListAdapter<AppChoice, ChoiceViewHolder>(DiffCallback) {

        private val icons = LruCache<String, Drawable>(64)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChoiceViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_choice, parent, false)
            return ChoiceViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChoiceViewHolder, position: Int) {
            val choice = getItem(position)
            holder.label.text = choice.label
            holder.packageName.text = choice.packageName

            val cached = icons.get(choice.packageName)
            if (cached != null) {
                holder.icon.setImageDrawable(cached)
            } else {
                val icon = ScriptStore.appIcon(context, choice.packageName)
                if (icon != null) {
                    icons.put(choice.packageName, icon)
                    holder.icon.setImageDrawable(icon)
                } else {
                    holder.icon.setImageResource(R.drawable.ic_extension)
                }
            }

            holder.itemView.setOnClickListener { onSelect(choice) }
        }

        private object DiffCallback : DiffUtil.ItemCallback<AppChoice>() {
            override fun areItemsTheSame(oldItem: AppChoice, newItem: AppChoice) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppChoice, newItem: AppChoice) =
                oldItem == newItem
        }
    }

    private class ChoiceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.choiceIcon)
        val label: TextView = view.findViewById(R.id.choiceLabel)
        val packageName: TextView = view.findViewById(R.id.choicePackage)
    }
}

fun sourceLabel(context: Context, source: ScriptSource): String {
    return context.getString(
        if (source == ScriptSource.USER) R.string.source_user else R.string.source_remote
    )
}