package com.fuck.iab

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.core.content.edit
import com.fuck.iab.ScriptType.BUNDLED
import com.fuck.iab.ScriptType.GLOBAL_REMOTE
import com.fuck.iab.ScriptType.OPT_IN
import com.fuck.iab.ScriptType.REMOTE
import com.fuck.iab.ScriptType.USER
import io.github.libxposed.service.XposedService
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile

enum class ScriptType { BUNDLED, USER, REMOTE, GLOBAL_REMOTE, OPT_IN }

data class ScriptInfo(
    val packageName: String,
    val type: ScriptType,
    val source: String,
    val author: String? = null,
    val size: Int = 0,
    val enabled: Boolean,
    val name: String? = null,
    val url: String? = null,
    val version: Int = 1,
    val summary: String? = null,
    /** Last modification as an epoch millis in UTC, `0` when the script carries no `// modified-at:`. */
    val modified: Long = 0L
)

data class ScriptDetails(
    val packageName: String,
    val type: ScriptType,
    val author: String?,
    val version: Int,
    val description: String,
    val modifiedAt: String,
    val enabled: Boolean,
    val size: Int
)

object RemoteFiles {

    fun list(service: XposedService): Set<String> = list { service.listRemoteFiles() }

    /** The module side exposes the same call on `XposedModule`, which is a different type. */
    fun list(source: () -> Array<String>): Set<String> = try {
        source().toSet()
    } catch (e: Exception) {
        emptySet()
    }

    fun read(service: XposedService, name: String): String? = read({ service.openRemoteFile(it) }, name)

    fun read(open: (String) -> ParcelFileDescriptor, name: String): String? {
        return try {
            FileInputStream(open(name).fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
    }

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

    /** The file's last modification time in epoch millis, or `0` when it is unknown. */
    fun lastModifiedAt(service: XposedService, name: String): Long =
        lastModifiedAt({ service.openRemoteFile(it) }, name)

    fun lastModifiedAt(open: (String) -> ParcelFileDescriptor, name: String): Long {
        return try {
            open(name).use { pfd ->
                // the framework hands over the descriptor of the real file, so its inode
                // time is readable even though the path itself lives in another process
                val stat = Os.fstat(pfd.fileDescriptor)
                if (OsConstants.S_ISREG(stat.st_mode)) {
                    stat.st_mtime * 1000L + stat.st_mtim.tv_nsec / 1_000_000L
                } else {
                    0L
                }
            }
        } catch (e: Exception) {
            0L
        }
    }
}

object ScriptStore {

    /** Accepted `// modified-at:` shapes, all of them read as UTC. */
    private val UTC_PATTERNS = arrayOf(
        "d/M/yyyy H:mm",
        "d/M/yyyy H:mm:ss",
        "d/M/yyyy H:m",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    /** Where a downloaded copy of `name` is stored in the Xposed file store. */
    fun remoteFile(name: String): String = rs_() + name + _js()

    /**
     * The name of a `rs_` file that comes from `scripts/global` and runs on every app. Those
     * names never contain a dot, package names always do, so that is what tells them apart.
     */
    fun isGlobalRemoteName(name: String): Boolean = !name.contains('.')

    /** The shared pref key holding the scripts that are switched off. */
    fun disabledKey(type: ScriptType): String =
        if (type == USER) disabled_() + user() + _scripts() else disabled_scripts()

    /** Opt-in scripts are off unless listed here, so they use their own allow-list key. */
    fun optInKey(): String = enabled_optin_scripts()

    /** `// opt-in: true` marks a script from `scripts/global` that only runs when enabled. */
    fun isOptIn(script: String): Boolean =
        headerOf(script, opt_in())?.toBoolean()?.equals(true) == true

    /**
     * Epoch millis of `// modified-at:`, read as UTC so the device timezone never takes part.
     * `0` when the tag is missing or unreadable, which sorts the script to the bottom.
     */
    fun modifiedOf(script: String): Long {
        val raw = headerOf(script, modified_at()) ?: return 0L

        for (pattern in UTC_PATTERNS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                format.parse(raw)?.let { return it.time }
            } catch (e: Exception) {
                // try the next pattern
            }
        }
        return 0L
    }

    /** A modification time rendered the way the tag is written, always in UTC. */
    fun formatUtc(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        val format = SimpleDateFormat("d/M/yyyy H:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date(epochMillis))
    }

    fun inventory(context: Context, service: XposedService?): List<ScriptInfo> {
        val bundledScripts = bundledScripts(context)
        val remoteScripts = remoteScripts(service)

        val off = if (service != null) disabledScripts(service) else emptySet()
        val offUser = if (service != null) disabledUserScripts(service) else emptySet()
        val onOptIn = if (service != null) enabledOptInScripts(service) else emptySet()

        val ret = arrayListOf<ScriptInfo>()

        userScripts(service).forEach { (packageName, script) ->
            if (script == null) return@forEach
            ret.add(
                ScriptInfo(
                    packageName = packageName,
                    type = USER,
                    source = script,
                    author = authorOf(script),
                    size = script.toByteArray(Charsets.UTF_8).size,
                    enabled = !offUser.contains(packageName),
                    name = headerOf(script, name()),
                    url = headerOf(script, url()),
                    // an absolute instant, so it already means the same thing in every timezone
                    modified = if (service != null) RemoteFiles.lastModifiedAt(service, packageName + _js()) else 0L
                )
            )
        }

        // a package is listed once: as its remote copy when that one is newer, bundled otherwise
        val winningRemote = hashMapOf<String, String>()
        remoteScripts.forEach { (name, script) ->
            if (isGlobalRemoteName(name)) return@forEach
            if (name == global() || name == user_script()) return@forEach

            val picked = pickScript(bundledScripts[name], script)
            if (picked != null && picked.first == REMOTE) winningRemote[name] = picked.second
        }

        bundledScripts.forEach { (packageName, script) ->
            if (winningRemote.containsKey(packageName)) return@forEach

            ret.add(
                ScriptInfo(
                    packageName = packageName,
                    type = BUNDLED,
                    source = script,
                    author = authorOf(script),
                    size = script.toByteArray(Charsets.UTF_8).size,
                    enabled = !off.contains(packageName),
                    name = headerOf(script, name()),
                    url = headerOf(script, url()),
                    version = versionOf(script),
                    summary = headerOf(script, summary()),
                    modified = modifiedOf(script)
                )
            )
        }

        winningRemote.forEach { (packageName, script) ->
            ret.add(
                ScriptInfo(
                    packageName = packageName,
                    type = REMOTE,
                    source = script,
                    author = authorOf(script),
                    size = script.toByteArray(Charsets.UTF_8).size,
                    enabled = !off.contains(packageName),
                    name = headerOf(script, name()),
                    url = headerOf(script, url()),
                    version = versionOf(script),
                    summary = headerOf(script, summary()),
                    modified = modifiedOf(script)
                )
            )
        }

        remoteScripts.forEach { (name, script) ->
            if (!isGlobalRemoteName(name)) return@forEach
            if (name == global() || name == user_script()) return@forEach

            val optIn = isOptIn(script)
            ret.add(
                ScriptInfo(
                    packageName = name,
                    type = if (optIn) OPT_IN else GLOBAL_REMOTE,
                    source = script,
                    author = authorOf(script),
                    size = script.toByteArray(Charsets.UTF_8).size,
                    enabled = if (optIn) onOptIn.contains(name) else !off.contains(name),
                    name = headerOf(script, name()),
                    url = headerOf(script, url()),
                    version = versionOf(script),
                    summary = headerOf(script, summary()),
                    modified = modifiedOf(script)
                )
            )
        }

        ret.sortWith { info, info1 -> compareScripts(info, info1) }

        return ret
    }

    /**
     * The copy that actually runs for a package: the downloaded one only while it is strictly
     * newer than the bundled one, a tie always goes to the bundle. `null` when there is nothing
     * usable, which is also the case for a half written download, so it gets fetched again.
     */
    internal fun pickScript(bundled: String?, remote: String?): Pair<ScriptType, String>? {
        val remoteVersion = remote?.let { rawVersionOf(it) }

        if (bundled == null) {
            return if (remoteVersion == null) null else REMOTE to remote!!
        }

        return if (remoteVersion != null && remoteVersion > versionOf(bundled)) {
            REMOTE to remote!!
        } else {
            BUNDLED to bundled
        }
    }

    /**
     * One rule for every script: newest `// modified-at:` (UTC) first, a tie broken by name.
     * The two exceptions are `global.js`, which the runtime needs first and therefore pins to
     * the bottom of the list, and scripts without a modification time, which sort last.
     */
    internal fun compareScripts(info: ScriptInfo, other: ScriptInfo): Int {
        val thisIsGlobal = info.type == BUNDLED && info.packageName == global()
        val otherIsGlobal = other.type == BUNDLED && other.packageName == global()
        if (thisIsGlobal != otherIsGlobal) return if (thisIsGlobal) 1 else -1

        val byModified = other.modified.compareTo(info.modified)
        if (byModified != 0) return byModified

        return info.packageName.compareTo(other.packageName)
    }

    private fun userScripts(service: XposedService?): Map<String, String?> {
        return if (service == null) emptyMap()
        else try {
            RemoteFiles.list(service)
                // the downloaded copies live in the same file store and are not user scripts
                .filter { it.endsWith(_js()) && !it.startsWith(rs_()) }
                .associate {
                    it.removeSuffix(_js()) to RemoteFiles.read(service, it)
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Downloaded copies, keyed by the name between `rs_` and `.js`. */
    internal fun remoteScripts(service: XposedService?): Map<String, String> {
        if (service == null) return emptyMap()

        val scripts = hashMapOf<String, String>()
        RemoteFiles.list(service).forEach { file ->
            if (!file.startsWith(rs_()) || !file.endsWith(_js())) return@forEach

            val name = file.removePrefix(rs_()).removeSuffix(_js())
            if (name.isEmpty()) return@forEach

            RemoteFiles.read(service, file)?.let { scripts[name] = it }
        }
        return scripts
    }

    internal fun bundledScripts(context: Context): Map<String, String> {
        return try {
            ZipFile(context.applicationInfo.sourceDir).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith(assets_scripts_()) && it.name.endsWith(_js()) }
                    .associate { entry ->
                        val packageName = entry.name.removePrefix(assets_scripts_()).removeSuffix(_js())
                        val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        packageName to text
                    }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun authorOf(script: String): String? = headerOf(script, author())

    // reads `// key: value` from the top comment block; header order (name/url/author/description) is free
    fun headerOf(script: String, key: String): String? {
        val prefix = scf64b8() + key + se26549()
        return script.lineSequence().take(10).firstNotNullOfOrNull { line ->
            if (line.startsWith(prefix)) line.removePrefix(prefix).trim().ifEmpty { null } else null
        }
    }

    /** The `// version:` of a script, or `null` when the tag is missing or not a number. */
    fun rawVersionOf(script: String): Int? = headerOf(script, version())?.trim()?.toIntOrNull()

    /** The version compared and shown; scripts bundled by an older APK count as 1. */
    fun versionOf(script: String): Int = rawVersionOf(script) ?: 1

    fun details(info: ScriptInfo): ScriptDetails = ScriptDetails(
        packageName = info.packageName,
        type = info.type,
        author = info.author,
        version = info.version,
        // global scripts carry no description of their own, the summary stands in for it
        description = headerOf(info.source, description()) ?: info.summary.orEmpty(),
        modifiedAt = formatUtc(info.modified),
        enabled = info.enabled,
        size = info.size
    )

    fun userFile(packageName: String): String {
        return if (packageName == global()) user_script() + _js() else packageName + _js()
    }

    fun readUserScript(service: XposedService, packageName: String): String? {
        val file = userFile(packageName)
        if (!RemoteFiles.list(service).contains(file)) return null
        return RemoteFiles.read(service, file)
    }

    fun saveUserScript(service: XposedService, packageName: String, text: String): Boolean {
        return RemoteFiles.write(service, userFile(packageName), text)
    }

    fun removeUserScript(service: XposedService, packageName: String) {
        RemoteFiles.delete(service, userFile(packageName))
    }

    fun disabledScripts(service: XposedService): Set<String> =
        readStringSet(service, disabled_scripts())

    fun disabledUserScripts(service: XposedService): Set<String> =
        readStringSet(service, disabled_() + user() + _scripts())

    /** Opt-in scripts are off unless their name is listed here. */
    fun enabledOptInScripts(service: XposedService): Set<String> =
        readStringSet(service, enabled_optin_scripts())

    private fun readStringSet(service: XposedService, key: String): Set<String> {
        return try {
            service.getRemotePreferences(fuck_iab()).getStringSet(key, emptySet())?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setEnabled(service: XposedService, packageName: String, type: ScriptType, enabled: Boolean) {
        val prefs = service.getRemotePreferences(fuck_iab())
        // opt-ins keep an allow-list, everything else a block-list
        val key = if (type == OPT_IN) optInKey() else disabledKey(type)
        val names = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()

        val listed = enabled == (type == OPT_IN)
        if (listed) names.add(packageName) else names.remove(packageName)

        prefs.edit(commit = true) { putStringSet(key, names) }
    }

    fun installedApps(context: Context): List<String> {
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)

            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun appLabel(context: Context, packageName: String, name: String? = null): String {
        if (packageName == global()) return globalJs()
        if (packageName == user_script()) return userScriptJs()

        return try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            // app not installed: fall back to the script's `// name:` header
            name ?: packageName
        }
    }

    /** Row label: the `// name:` tag for global and opt-in scripts, the app name for the rest. */
    fun scriptLabel(context: Context, info: ScriptInfo): String =
        if (info.type == GLOBAL_REMOTE || info.type == OPT_IN) info.name ?: info.packageName
        else appLabel(context, info.packageName, info.name)

    fun appIcon(context: Context, packageName: String): Drawable? {
        // The all-apps entry is not an app, so the caller keeps its own script icon.
        if (packageName == global()) return null

        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            // A package that is not installed keeps the caller's script icon.
            null
        }
    }

    /** Global and opt-in scripts are not apps, so they keep the caller's script icon. */
    fun scriptIcon(context: Context, info: ScriptInfo): Drawable? =
        if (info.type == GLOBAL_REMOTE || info.type == OPT_IN) null else appIcon(context, info.packageName)

    fun sizeLabel(bytes: Int): String {
        return if (bytes >= 1024) {
            String.format(Locale.US, _0fKb(), bytes / 1024f)
        } else {
            bytes.toString() + b()
        }
    }
}
