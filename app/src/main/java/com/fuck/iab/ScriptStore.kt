package com.fuck.iab

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

/** Where the script that is currently used for a package comes from. */
enum class ScriptSource { USER, REMOTE }

data class ScriptInfo(
    val packageName: String,
    val source: ScriptSource,
    val author: String?,
    val size: Int,
    val enabled: Boolean,
    val hasUserScript: Boolean,
    /** Apps this script runs in: every app in scope for the all-apps script, one for a package. */
    val coveredApps: Int,
    /** Whether the module hooks this package at all, which is what makes the script run. */
    val inScope: Boolean,
    /** Whether the app is still on the device, so its name and icon are available. */
    val installed: Boolean
)

/** Everything the detail sheet can say about one script. */
data class ScriptDetails(
    val packageName: String,
    val source: ScriptSource,
    val size: Int,
    val author: String?,
    val sha256: String?,
    val file: String?,
    val hasUserScript: Boolean,
    val enabled: Boolean,
    val coveredApps: Int,
    val inScope: Boolean,
    val installed: Boolean
)

/** How the module code inside a running process relates to the installed module. */
enum class RunningState { CURRENT, STALE, RELOADING, FAILED }

/** One process of a hooked app in which the module is loaded right now. */
data class RunningProcess(
    val processName: String,
    val pid: Int,
    val state: RunningState
)

/** A package the module is scoped to, with the state of its script if it has one. */
data class HookedApp(
    val packageName: String,
    val label: String,
    val script: ScriptInfo?,
    val running: List<RunningProcess> = emptyList()
) {
    val isRunning: Boolean get() = running.isNotEmpty()
}

/** Files in the module's shared data directory, which is how the module and this app talk. */
object RemoteFiles {

    fun list(service: XposedService): Set<String> {
        return try {
            service.listRemoteFiles().toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun read(service: XposedService, name: String): String? {
        return try {
            val pfd = service.openRemoteFile(name)
            FileInputStream(pfd.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
    }

    /** @return true when the file was stored, so callers never claim a write that failed. */
    fun write(service: XposedService, name: String, text: String): Boolean {
        return try {
            val pfd = service.openRemoteFile(name)
            FileOutputStream(pfd.fileDescriptor).use { out ->
                out.channel.truncate(0)
                out.write(text.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun delete(service: XposedService, name: String): Boolean {
        return try {
            service.deleteRemoteFile(name)
        } catch (e: Exception) {
            false
        }
    }
}

/** App side knowledge about scripts: what exists, what is enabled and what an editor shows. */
object ScriptStore {

    /** The all-apps script, the entry the store publishes as global. */
    const val ALL_APPS = "global"

    private const val SETTINGS_GROUP = "ui"
    private const val THEME_KEY = "theme"
    private const val USER_INDEX_KEY = "user_scripts"
    private const val SCOPE_KEY = "known_scope"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    // ---- theme -----------------------------------------------------------------

    /** The theme the user picked with the toolbar button, system until then. */
    fun theme(context: Context): Int {
        return context.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE).getInt(THEME_KEY, THEME_SYSTEM)
    }

    fun setTheme(context: Context, mode: Int) {
        context.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE).edit { putInt(THEME_KEY, mode) }
        applyTheme(context)
    }

    @JvmStatic
    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme(context)) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** What the app is showing right now, whether from the system or from the pick. */
    fun isNight(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    // ---- inventory -------------------------------------------------------------

    fun inventory(
        context: Context,
        service: XposedService?,
        scope: Set<String> = emptySet(),
        scopeConfirmed: Boolean = true
    ): List<ScriptInfo> {
        val files = if (service != null) RemoteFiles.list(service) else emptySet()
        val synced = if (service != null) syncedEntries(service) else emptyMap()
        val disabled = if (service != null) disabledApps(service) else emptySet()
        val users = userEntries(context, files)

        // The all-apps script runs everywhere and is not an app of its own, so the list of
        // per-app scripts leaves it out.
        val names = sortedSetOf<String>()
        names += users.keys.filter { it != ALL_APPS }
        names += synced.keys.filter { it != ALL_APPS }

        return names.map { packageName ->
            val user = users[packageName]
            val syncedEntry = synced[packageName]
            val installed = isInstalled(context, packageName)

            val source: ScriptSource
            val size: Int
            val author: String?

            if (user != null) {
                val text = if (service != null) RemoteFiles.read(service, user) else null
                source = ScriptSource.USER
                size = text?.toByteArray(Charsets.UTF_8)?.size ?: 0
                author = text?.let { authorOf(it) }
            } else {
                source = ScriptSource.REMOTE
                size = syncedEntry?.size?.toInt() ?: 0
                author = syncedEntry?.author
            }

            // While the framework has not reported the scope, claiming an app is out of it
            // would be wrong, so nothing is flagged until the answer is known.
            val inScope = packageName == ALL_APPS || !scopeConfirmed || scope.contains(packageName)

            ScriptInfo(
                packageName = packageName,
                source = source,
                author = author,
                size = size,
                enabled = !disabled.contains(packageName),
                hasUserScript = user != null,
                coveredApps = if (packageName == ALL_APPS) scope.size else if (inScope) 1 else 0,
                inScope = inScope,
                installed = installed
            )
        }.sortedWith(
            // Apps that are still around come first, since those are the ones a script can act on.
            compareByDescending<ScriptInfo> { it.installed }
                .thenBy { appLabel(context, it.packageName).lowercase(Locale.US) }
        )
    }

    /** The scripts the store lists, keyed by package, as of the last sync. */
    private fun syncedEntries(service: XposedService): Map<String, RemoteScripts.Entry> {
        return RemoteScripts.parseIndex(RemoteFiles.read(service, RemoteScripts.INDEX_FILE))
            ?.associateBy { it.packageName } ?: emptyMap()
    }

    /** Whether [packageName] is installed, so the list can say when it is not. */
    fun isInstalled(context: Context, packageName: String): Boolean {
        if (packageName == ALL_APPS) return true
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun authorOf(script: String): String? {
        val firstLine = script.lineSequence().firstOrNull() ?: return null
        if (!firstLine.startsWith("// author: ")) return null
        return firstLine.removePrefix("// author: ").trim().ifEmpty { null }
    }

    // ---- details ---------------------------------------------------------------

    /** Reads the script that would run for [packageName] and describes where it came from. */
    fun details(context: Context, service: XposedService?, packageName: String): ScriptDetails {
        val files = if (service != null) RemoteFiles.list(service) else emptySet()
        val scoped = if (service != null) scope(service) else emptyList()

        val userFile = userFile(packageName)
        val syncedFile = RemoteScripts.fileName(packageName)

        val source: ScriptSource
        val text: String?
        var file: String? = null

        when {
            service != null && files.contains(userFile) -> {
                source = ScriptSource.USER
                file = userFile
                text = RemoteFiles.read(service, userFile)
            }

            service != null && files.contains(syncedFile) -> {
                source = ScriptSource.REMOTE
                file = syncedFile
                text = RemoteFiles.read(service, syncedFile)
            }

            else -> {
                // A script the store lists but the device never received.
                source = ScriptSource.REMOTE
                text = null
            }
        }

        val checksum = text?.let { sha256(it) }

        return ScriptDetails(
            packageName = packageName,
            source = source,
            size = text?.toByteArray(Charsets.UTF_8)?.size ?: 0,
            author = text?.let { authorOf(it) },
            sha256 = checksum,
            file = file,
            hasUserScript = files.contains(userFile),
            enabled = service == null || packageName !in disabledApps(service),
            coveredApps = if (packageName == ALL_APPS) scoped.size else 1,
            inScope = packageName == ALL_APPS || scoped.contains(packageName),
            installed = isInstalled(context, packageName)
        )
    }

    fun sha256(text: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    // ---- content ---------------------------------------------------------------

    /** The script the module would run for [packageName]: the user copy, else the remote one. */
    fun readScript(service: XposedService?, packageName: String): Pair<ScriptSource, String>? {
        if (service == null) return null

        val user = readUserScript(service, packageName)
        if (!user.isNullOrBlank()) return ScriptSource.USER to user

        val synced = RemoteFiles.read(service, RemoteScripts.fileName(packageName))
        if (!synced.isNullOrBlank()) return ScriptSource.REMOTE to synced

        return null
    }

    // ---- user scripts ----------------------------------------------------------

    /** The file that holds the user copy of [packageName] in the shared directory. */
    fun userFile(packageName: String): String {
        return if (packageName == ALL_APPS) userScriptName() else RemoteScripts.userFileName(packageName)
    }

    /** File the module reads as the all-apps user script. */
    fun userScriptName(): String = user_script_js()

    private fun readUserScript(service: XposedService, packageName: String): String? {
        val file = userFile(packageName)
        if (!RemoteFiles.list(service).contains(file)) return null
        return RemoteFiles.read(service, file)
    }

    /** @return false when the script could not be stored in the shared directory. */
    fun saveUserScript(context: Context, service: XposedService, packageName: String, text: String): Boolean {
        if (!RemoteFiles.write(service, userFile(packageName), text)) return false

        if (packageName != ALL_APPS) {
            val index = readUserIndex(context).toMutableMap()
            index[packageName] = userFile(packageName)
            writeUserIndex(context, index)
        }

        return true
    }

    fun revertUserScript(context: Context, service: XposedService, packageName: String) {
        RemoteFiles.delete(service, userFile(packageName))
        if (packageName != ALL_APPS) {
            val index = readUserIndex(context).toMutableMap()
            index.remove(packageName)
            writeUserIndex(context, index)
        }
    }

    private fun readUserIndex(context: Context): Map<String, String> {
        val text = context.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE)
            .getString(USER_INDEX_KEY, null) ?: return emptyMap()

        return try {
            val json = JSONObject(text)
            json.keys().asSequence().associateWith { json.optString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun writeUserIndex(context: Context, index: Map<String, String>) {
        val json = JSONObject()
        index.forEach { (packageName, file) -> json.put(packageName, file) }
        context.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE)
            .edit { putString(USER_INDEX_KEY, json.toString()) }
    }

    /** User scripts whose package is not in any other list, so they stay visible. */
    private fun userEntries(context: Context, files: Set<String>): Map<String, String> {
        val result = sortedMapOf<String, String>()

        for ((packageName, file) in readUserIndex(context)) {
            if (!files.contains(file)) continue
            result[packageName] = file
        }

        // The all-apps user script has a fixed name and is not part of the index.
        if (files.contains(userScriptName())) {
            result[ALL_APPS] = userScriptName()
        }

        return result
    }

    // ---- enable or disable -----------------------------------------------------

    fun disabledApps(service: XposedService): Set<String> {
        return try {
            service.getRemotePreferences(fuck_iab()).getStringSet(RemoteScripts.DISABLED_SCRIPTS_KEY, emptySet())
                ?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setEnabled(service: XposedService, packageName: String, enabled: Boolean) {
        val prefs = service.getRemotePreferences(fuck_iab())
        val disabled = prefs.getStringSet(RemoteScripts.DISABLED_SCRIPTS_KEY, emptySet())?.toMutableSet()
            ?: mutableSetOf()

        if (enabled) disabled.remove(packageName) else disabled.add(packageName)

        // Committed, because the module reads this from the hooked app's process.
        prefs.edit(commit = true) { putStringSet(RemoteScripts.DISABLED_SCRIPTS_KEY, disabled) }
    }

    // ---- hooked apps -----------------------------------------------------------

    /**
     * The scope of the last successful read. The framework can answer with an empty scope,
     * and claiming that no app is hooked when the module is demonstrably loaded would be
     * wrong, so the last known list is kept as a fallback.
     */
    fun savedScope(context: Context): List<String> {
        return context.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE)
            .getStringSet(SCOPE_KEY, emptySet())?.toList() ?: emptyList()
    }

    fun saveScope(context: Context, scope: List<String>) {
        context.getSharedPreferences(SETTINGS_GROUP, Context.MODE_PRIVATE)
            .edit { putStringSet(SCOPE_KEY, scope.toSet()) }
    }

    fun scope(service: XposedService): List<String> {
        return try {
            service.scope
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun hookedApps(
        context: Context,
        service: XposedService,
        scripts: List<ScriptInfo>,
        scope: List<String> = scope(service)
    ): List<HookedApp> {
        val byPackage = scripts.associateBy { it.packageName }
        val running = runningProcesses(service)

        return scope
            .map { packageName ->
                HookedApp(
                    packageName = packageName,
                    label = appLabel(context, packageName),
                    script = byPackage[packageName],
                    running = running[packageName].orEmpty()
                )
            }
            .sortedBy { it.label.lowercase(Locale.US) }
    }

    /**
     * Processes the framework currently has the module loaded in, keyed by package name.
     * A target only exposes its process name, so the part before ":" is the package.
     */
    fun runningProcesses(service: XposedService): Map<String, List<RunningProcess>> {
        val targets = try {
            service.runningTargets
        } catch (e: Exception) {
            return emptyMap()
        }

        val result = mutableMapOf<String, MutableList<RunningProcess>>()

        for (target in targets) {
            val processName = target.processName ?: continue
            val packageName = processName.substringBefore(':')

            result.getOrPut(packageName) { mutableListOf() }
                .add(RunningProcess(processName, target.pid, stateOf(target.state)))
        }

        // Processes with a fresher state first, then by pid, so the list is stable.
        return result.mapValues { (_, processes) -> processes.sortedBy { it.pid } }
    }

    private fun stateOf(state: io.github.libxposed.service.HookedTarget.State?): RunningState {
        return when (state) {
            io.github.libxposed.service.HookedTarget.State.STALE -> RunningState.STALE
            io.github.libxposed.service.HookedTarget.State.RELOADING -> RunningState.RELOADING
            io.github.libxposed.service.HookedTarget.State.FAILED -> RunningState.FAILED
            else -> RunningState.CURRENT
        }
    }

    // ---- app info --------------------------------------------------------------

    /** Packages with a launcher entry, so the new script dialog can offer them. */
    fun installedApps(context: Context): List<String> {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)

            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun appLabel(context: Context, packageName: String): String {
        if (packageName == ALL_APPS) return context.getString(R.string.scripts_all_apps)

        return try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    fun appIcon(context: Context, packageName: String): Drawable? {
        // The all-apps entry is not an app, so the caller keeps its own script icon.
        if (packageName == ALL_APPS) return null

        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            // A package that is not installed keeps the caller's script icon.
            null
        }
    }

    fun sizeLabel(bytes: Int): String {
        return if (bytes >= 1024) {
            String.format(Locale.US, "%.0f KB", bytes / 1024f)
        } else {
            "$bytes B"
        }
    }
}
