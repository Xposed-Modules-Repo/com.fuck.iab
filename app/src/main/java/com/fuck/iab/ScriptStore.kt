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
import com.fuck.iab.ScriptType.REMOTE
import com.fuck.iab.ScriptType.USER
import io.github.libxposed.service.XposedService
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

enum class ScriptType { BUNDLED, USER, REMOTE, GLOBAL_REMOTE }

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
    val summary: String? = null
)

data class ScriptDetails(
    val packageName: String,
    val type: ScriptType,
    val author: String?,
    val version: Int,
    val description: String,
    val addedIn: String,
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

    fun inventory(context: Context, service: XposedService?): List<ScriptInfo> {
        val bundledScripts = bundledScripts(context)
        val remoteScripts = remoteScripts(service)

        val off = if (service != null) disabledScripts(service) else emptySet()
        val offUser = if (service != null) disabledUserScripts(service) else emptySet()

        val ret = arrayListOf<ScriptInfo>()
        val modifiedAt = hashMapOf<String, Long>()
        val addedIn = hashMapOf<String, String>()

        userScripts(service).forEach { (packageName, script) ->
            if (script == null) return@forEach
            modifiedAt[packageName] =
                if (service != null) RemoteFiles.lastModifiedAt(service, packageName + _js()) else 0L
            ret.add(
                ScriptInfo(
                    packageName = packageName,
                    type = USER,
                    source = script,
                    author = authorOf(script),
                    size = script.toByteArray(Charsets.UTF_8).size,
                    enabled = !offUser.contains(packageName),
                    name = headerOf(script, name()),
                    url = headerOf(script, url())
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

            headerOf(script, added_in())?.let { addedIn[packageName] = it }
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
                    summary = headerOf(script, summary())
                )
            )
        }

        winningRemote.forEach { (packageName, script) ->
            headerOf(script, added_in())?.let { addedIn[packageName] = it }
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
                    version = versionOf(script)
                )
            )
        }

        remoteScripts.forEach { (name, script) ->
            if (!isGlobalRemoteName(name)) return@forEach
            if (name == global() || name == user_script()) return@forEach

            modifiedAt[name] =
                if (service != null) RemoteFiles.lastModifiedAt(service, remoteFile(name)) else 0L
            ret.add(
                ScriptInfo(
                    packageName = name,
                    type = GLOBAL_REMOTE,
                    source = script,
                    author = authorOf(script),
                    size = script.toByteArray(Charsets.UTF_8).size,
                    enabled = !off.contains(name),
                    name = headerOf(script, name()),
                    url = headerOf(script, url()),
                    version = versionOf(script),
                    summary = headerOf(script, summary())
                )
            )
        }

        ret.sortWith { info, info1 -> compareScripts(info, info1, addedIn, modifiedAt) }

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
     * Priority, highest first: `user_script` is always on top, global remote scripts come next,
     * bundled and remote scripts share one list ordered by `added-in` (highest first) and user
     * ones by the modified date of their file (latest first). `global` is always at the bottom.
     */
    internal fun compareScripts(
        info: ScriptInfo,
        other: ScriptInfo,
        addedIn: Map<String, String>,
        modifiedAt: Map<String, Long>
    ): Int {
        if (info.packageName == user_script()) return if (other.packageName == user_script()) 0 else -1
        if (other.packageName == user_script()) return 1
        if (info.packageName == global()) return if (other.packageName == global()) 0 else 1
        if (other.packageName == global()) return -1

        val byRank = scriptRank(info).compareTo(scriptRank(other))
        if (byRank != 0) return byRank

        val byName = info.packageName.compareTo(other.packageName)

        if (info.type == BUNDLED || info.type == REMOTE) {
            val byAddedIn = compareVersions(addedIn[info.packageName], addedIn[other.packageName])
            return if (byAddedIn != 0) byAddedIn else byName
        }

        val byModified =
            (modifiedAt[other.packageName] ?: 0L).compareTo(modifiedAt[info.packageName] ?: 0L)
        return if (byModified != 0) byModified else byName
    }

    private fun scriptRank(info: ScriptInfo): Int = when (info.type) {
        GLOBAL_REMOTE -> 0
        BUNDLED, REMOTE -> 1
        USER -> 2
    }

    /** Numeric `x.y.z` compare where the highest version wins, e.g. `1.7.1` beats `1.6.12`. */
    private fun compareVersions(first: String?, second: String?): Int {
        val a = versionParts(first)
        val b = versionParts(second)

        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return right.compareTo(left)
        }

        return 0
    }

    private fun versionParts(version: String?): List<Int> =
        version?.split('.')?.map { it.trim().toIntOrNull() ?: 0 } ?: emptyList()

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
        addedIn = if (info.type == GLOBAL_REMOTE) "" else headerOf(info.source, added_in()) ?: oneZeroZero(),
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

    private fun readStringSet(service: XposedService, key: String): Set<String> {
        return try {
            service.getRemotePreferences(fuck_iab()).getStringSet(key, emptySet())?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setEnabled(service: XposedService, packageName: String, type: ScriptType, enabled: Boolean) {
        val prefs = service.getRemotePreferences(fuck_iab())
        val key = disabledKey(type)
        val disabled = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (enabled) disabled.remove(packageName) else disabled.add(packageName)

        prefs.edit(commit = true) { putStringSet(key, disabled) }
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

    /** Row label: the `// name:` tag for global scripts, the app name for everything else. */
    fun scriptLabel(context: Context, info: ScriptInfo): String =
        if (info.type == GLOBAL_REMOTE) info.name ?: info.packageName
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

    /** Global scripts are not apps, so they always keep the caller's script icon. */
    fun scriptIcon(context: Context, info: ScriptInfo): Drawable? =
        if (info.type == GLOBAL_REMOTE) null else appIcon(context, info.packageName)

    fun sizeLabel(bytes: Int): String {
        return if (bytes >= 1024) {
            String.format(Locale.US, _0fKb(), bytes / 1024f)
        } else {
            bytes.toString() + b()
        }
    }
}
