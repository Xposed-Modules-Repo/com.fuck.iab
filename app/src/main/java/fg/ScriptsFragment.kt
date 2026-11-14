package fg

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import com.fuck.iab.Cancel
import com.fuck.iab.NewScript
import com.fuck.iab.NotConnectedToYourXposedFrameworkTryForceStoppingFkiabAndReLaf289e
import com.fuck.iab.NothingMatchesThatSearch
import com.fuck.iab.R
import com.fuck.iab.ScriptDetails
import com.fuck.iab.ScriptInfo
import com.fuck.iab.ScriptType
import com.fuck.iab.ScriptStore
import com.fuck.iab.ScriptStore.sizeLabel
import com.fuck.iab.ViewAppPage
import com.fuck.iab.ViewOnAmazonAppstore
import com.fuck.iab.ViewOnAppgallery
import com.fuck.iab.ViewOnAppleAppStore
import com.fuck.iab.ViewOnApkpure
import com.fuck.iab.ViewOnAptoide
import com.fuck.iab.ViewOnCafeBazaar
import com.fuck.iab.ViewOnFDroid
import com.fuck.iab.ViewOnGalaxyStore
import com.fuck.iab.ViewOnGithub
import com.fuck.iab.ViewOnGitlab
import com.fuck.iab.ViewOnGooglePlay
import com.fuck.iab.ViewOnMicrosoftStore
import com.fuck.iab.ViewOnMyket
import com.fuck.iab.ViewOnTelegram
import com.fuck.iab.amazonCom
import com.fuck.iab.apkpureCom
import com.fuck.iab.aptoideCom
import com.fuck.iab.appsAppleCom
import com.fuck.iab.appgalleryHuaweiCom
import com.fuck.iab.bundled
import com.fuck.iab.cafebazaarIr
import com.fuck.iab.cmAptoidePt
import com.fuck.iab.comApkpureAegon
import com.fuck.iab.com_android_vending
import com.fuck.iab.com_farsitel_bazaar
import com.fuck.iab.customScript
import com.fuck.iab.customScriptRunningOnEveryApp
import com.fuck.iab.fDroidOrg
import com.fuck.iab.galaxyStore
import com.fuck.iab.githubCom
import com.fuck.iab.gitlabCom
import com.fuck.iab.global
import com.fuck.iab.globalJs
import com.fuck.iab.ir_mservices_market
import com.fuck.iab.itunesAppleCom
import com.fuck.iab.microsoftCom
import com.fuck.iab.myketIr
import com.fuck.iab.orgFdroidFdroid
import com.fuck.iab.playGoogleCom
import com.fuck.iab.s4a9f2e
import com.fuck.iab.sharedHelpersAndEnvironmentSetup
import com.fuck.iab.tMe
import com.fuck.iab.user
import com.fuck.iab.userScriptJs
import com.fuck.iab.user_script
import com.fuck.iab.www
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
class ScriptsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ScriptAdapter
    private var pickerDialog: Dialog? = null
    private var sheet: BottomSheetDialog? = null

    private var query: String = ""
    private var filter: ScriptType? = null
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

        adapter = ScriptAdapter(requireContext()) { script ->
            if (script.type == ScriptType.BUNDLED) {
                showDetails(script)
            } else {
                openEditor(script.packageName)
            }
        }
        adapter.onEnabledChanged = { packageName, type, enabled -> viewModel.setEnabled(packageName, type, enabled) }

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
                    R.id.filterStore -> ScriptType.BUNDLED
                    R.id.filterUser -> ScriptType.USER
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
            val matchesFilter = filter == null || script.type == filter
            val label = ScriptStore.appLabel(requireContext(), script.packageName, script.name)
            val matchesQuery = needle.isEmpty() ||
                    script.packageName.contains(needle, true) ||
                    label.contains(needle, true) ||
                    script.name?.contains(needle, true) == true

            matchesFilter && matchesQuery
        }

        adapter.submitList(visible)

        if (viewModel.state.value.connectionStatus == 0) { // connecting...
            empty.isVisible = false
            empty.text = ""
        } else if (viewModel.state.value.connectionStatus == 1) { // connected
            if (!all.isEmpty() && visible.isEmpty()) {
                empty.isVisible = true
                empty.text = NothingMatchesThatSearch()
            } else {
                empty.isVisible = false
                empty.text = ""
            }
        } else if (viewModel.state.value.connectionStatus == 2) { // failed to connect
            if (all.isEmpty()) {
                empty.isVisible = true
                empty.text = NotConnectedToYourXposedFrameworkTryForceStoppingFkiabAndReLaf289e()
            } else {
                empty.isVisible = false
                empty.text = ""
            }
        }
    }

    private fun openEditor(packageName: String) {
        startActivity(ScriptEditorActivity.intent(requireContext(), packageName))
    }

    private fun showDetails(script: ScriptInfo) {
        val view = layoutInflater.inflate(R.layout.sheet_script_details, null)
        val context = requireContext()

        view.findViewById<ImageView>(R.id.detailIcon).setImageDrawable(
            ScriptStore.appIcon(context, script.packageName) ?: context.getDrawable(R.drawable.ic_description)
        )
        view.findViewById<TextView>(R.id.detailLabel).text =
            ScriptStore.appLabel(context, script.packageName, script.name)
        view.findViewById<TextView>(R.id.detailPackage).text = when (script.packageName) {
            global() -> sharedHelpersAndEnvironmentSetup()
            else -> script.packageName
        }

        val sheetDialog = BottomSheetDialog(context)
        sheetDialog.setContentView(view)
        sheet = sheetDialog

        val linkButton = view.findViewById<MaterialButton>(R.id.detailViewSource)
        val url = script.url
        if (url.isNullOrBlank()) {
            linkButton.isVisible = false
        } else {
            linkButton.isVisible = true
            linkButton.text = linkLabel(url)
            linkButton.setOnClickListener {
                openLink(url)
                sheetDialog.dismiss()
            }
        }

        sheetDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val details = withContext(Dispatchers.IO) {
                ScriptStore.details(context, script.packageName)
            }

            bindDetails(view, details)
        }
    }

    /** Button text derived from the `// url:` header: market links name the market, plain sites say "View app page". */
    private fun linkLabel(url: String): String {
        val host = Uri.parse(url).host?.removePrefix(www())?.lowercase() ?: return ViewAppPage()
        return when {
            host == playGoogleCom() -> ViewOnGooglePlay()
            host.endsWith(cafebazaarIr()) -> ViewOnCafeBazaar()
            host.endsWith(myketIr()) -> ViewOnMyket()
            host.endsWith(galaxyStore()) -> ViewOnGalaxyStore()
            host.endsWith(appgalleryHuaweiCom()) -> ViewOnAppgallery()
            host.endsWith(fDroidOrg()) -> ViewOnFDroid()
            host.endsWith(apkpureCom()) -> ViewOnApkpure()
            host.endsWith(aptoideCom()) -> ViewOnAptoide()
            host.endsWith(amazonCom()) -> ViewOnAmazonAppstore()
            host.endsWith(appsAppleCom()) || host.endsWith(itunesAppleCom()) -> ViewOnAppleAppStore()
            host.endsWith(microsoftCom()) -> ViewOnMicrosoftStore()
            host.endsWith(githubCom()) -> ViewOnGithub()
            host.endsWith(gitlabCom()) -> ViewOnGitlab()
            host == tMe() -> ViewOnTelegram()
            else -> ViewAppPage()
        }
    }

    // Opens the market app when installed (via its deep link), otherwise the plain web link in a browser.
    private fun openLink(url: String) {
        val uri = Uri.parse(url)
        val host = uri.host?.removePrefix(www())?.lowercase()

        val marketPkg = when {
            host == playGoogleCom() -> com_android_vending()
            host?.endsWith(fDroidOrg()) == true -> orgFdroidFdroid()
            host?.endsWith(apkpureCom()) == true -> comApkpureAegon()
            host?.endsWith(aptoideCom()) == true -> cmAptoidePt()
            host?.endsWith(myketIr()) == true -> ir_mservices_market()
            host?.endsWith(cafebazaarIr()) == true -> com_farsitel_bazaar()
            else -> null
        }
        if (marketPkg != null) {
            val marketIntent = Intent(Intent.ACTION_VIEW, uri).setPackage(marketPkg)
            if (marketIntent.resolveActivity(requireContext().packageManager) != null) {
                try {
                    startActivity(marketIntent)
                    return
                } catch (e: Exception) {
                    // fall through to the plain web link
                }
            }
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            // no browser installed; nothing else to try
        }
    }

    private fun bindDetails(view: View, details: ScriptDetails) {
        view.findViewById<TextView>(R.id.author).text = details.author ?: ""

        view.findViewById<TextView>(R.id.author).isVisible = details.author != null

        view.findViewById<TextView>(R.id.detailDescription).text = details.description

    }

    // ---- new script picker -----------------------------------------------------

    /** Asks which app the new script is for, instead of making the user type a package name. */
    private fun pickPackage() {
        val view = layoutInflater.inflate(R.layout.dialog_new_script, null)
        val input = view.findViewById<TextInputEditText>(R.id.searchInput)
        val list = view.findViewById<RecyclerView>(R.id.choiceList)
        val empty = view.findViewById<TextView>(R.id.choiceEmpty)

        val choices = ChoiceAdapter(requireContext()) { packageName ->
            if (packageName == user_script()) {
                pickerDialog?.dismiss()
                openEditor(user_script())
            } else {
                pickerDialog?.dismiss()
                openEditor(packageName)
            }
        }

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = choices

        pickerDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(NewScript())
            .setView(view)
            .setNegativeButton(Cancel(), null)
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

        state.scripts.forEach { packages += it.packageName }
        packages += ScriptStore.installedApps(context)
        packages -= global()

        val installedChoices = packages
            .map { AppChoice(ScriptStore.appLabel(context, it), it) }
            .sortedBy { it.label.lowercase() }

        // Prepend user script choice that opens the editor
        val userScriptChoice = AppChoice(
            userScriptJs(),
            user_script()
        )

        return listOf(userScriptChoice) + installedChoices
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
        holder.packageName.text = when (item.packageName) {
            user_script() -> customScript()
            else -> item.packageName
        }
        holder.icon.setImageDrawable(
            when (item.packageName) {
                user_script() -> context.getDrawable(R.drawable.ic_description)
                else -> ScriptStore.appIcon(context, item.packageName) ?: context.getDrawable(R.drawable.ic_extension)
            }
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

    var onEnabledChanged: ((String, ScriptType, Boolean) -> Unit)? = null

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.scriptIcon)
        val label: TextView = view.findViewById(R.id.scriptLabel)
        val packageName: TextView = view.findViewById(R.id.scriptPackage)
        val meta: TextView = view.findViewById(R.id.scriptMeta)
        val switch: MaterialSwitch = view.findViewById(R.id.scriptSwitch)
        var isBinding = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false))
        holder.switch.setOnCheckedChangeListener { button, isChecked ->
            if (holder.isBinding) return@setOnCheckedChangeListener
            val item = button.tag as? ScriptInfo ?: return@setOnCheckedChangeListener
            onEnabledChanged?.invoke(item.packageName, item.type, isChecked)
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)

        holder.label.text = if (item.packageName == global()) {
            globalJs()
        } else {
            ScriptStore.appLabel(context, item.packageName, item.name)
        }

        holder.packageName.text = when (item.packageName) {
            global() -> sharedHelpersAndEnvironmentSetup()
            user_script() -> customScriptRunningOnEveryApp()
            else -> item.packageName
        }

        val scriptTypeLabel = when (item.type) {
            ScriptType.USER -> user()
            ScriptType.BUNDLED -> bundled()
        }

        val parts = mutableListOf(scriptTypeLabel)

        parts += sizeLabel(item.size)

        item.author?.let { parts += it }

        holder.meta.text = parts.joinToString(s4a9f2e())

        holder.icon.setImageDrawable(
            ScriptStore.appIcon(context, item.packageName) ?: context.getDrawable(R.drawable.ic_description)
        )

        holder.switch.tag = item

        holder.isBinding = true
        try {
            holder.switch.isChecked = item.enabled
        } finally {
            holder.isBinding = false
        }

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
