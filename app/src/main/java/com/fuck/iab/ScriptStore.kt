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
    val author: String? = null,
    val size: Int = 0,
    var enabled: Boolean,
    val name: String? = null,
    val url: String? = null,
    val version: Int = 1,
    val summary: String? = null,
    /** The `// description:` tag, `null` when the script carries none. */
    val description: String? = null,
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

    /** The `sync` row remembering which install the bundled rows were read from. */
    private const val APK_KEY = "bundled_apk"

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
    fun formatModified(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        val format = SimpleDateFormat("d/M/yyyy", Locale.US).apply {
//            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date(epochMillis))
    }

    fun inventory(context: Context, service: XposedService?): List<ScriptInfo> {
        // only the file names are listed here; a script is opened once, the first time the cache
        // meets it, and every listing after that is answered by the cache alone
        val bundled = bundledMeta(context)
        val store = storeMeta(context, service)

        val off = if (service != null) disabledScripts(service) else emptySet()
        val offUser = if (service != null) disabledUserScripts(service) else emptySet()
        val onOptIn = if (service != null) enabledOptInScripts(service) else emptySet()

        val ret = arrayListOf<ScriptInfo>()

        store.user.forEach { (packageName, meta) ->
            ret.add(meta.info(packageName, USER, enabled = !offUser.contains(packageName)))
        }

        // a package is listed once: as its remote copy when that one is newer, bundled otherwise
        val winningRemote = hashMapOf<String, ScriptMeta>()
        store.remote.forEach { (name, meta) ->
            if (isGlobalRemoteName(name)) return@forEach
            if (name == global() || name == user_script()) return@forEach

            val bundledVersion = bundled[name]?.let { it.version ?: 1 }
            if (pickType(bundledVersion, meta.version) == REMOTE) winningRemote[name] = meta
        }

        bundled.forEach { (packageName, meta) ->
            if (winningRemote.containsKey(packageName)) return@forEach

            ret.add(meta.info(packageName, BUNDLED, enabled = !off.contains(packageName)))
        }

        winningRemote.forEach { (packageName, meta) ->
            ret.add(meta.info(packageName, REMOTE, enabled = !off.contains(packageName)))
        }

        store.remote.forEach { (name, meta) ->
            if (!isGlobalRemoteName(name)) return@forEach
            if (name == global() || name == user_script()) return@forEach

            ret.add(
                meta.info(
                    name,
                    if (meta.optIn) OPT_IN else GLOBAL_REMOTE,
                    enabled = if (meta.optIn) onOptIn.contains(name) else !off.contains(name)
                )
            )
        }

        ret.sortWith { info, info1 -> compareScripts(info, info1) }

        return ret
    }

    private fun ScriptMeta.info(packageName: String, type: ScriptType, enabled: Boolean) =
        ScriptInfo(
            packageName = packageName,
            type = type,
            author = author,
            size = size,
            enabled = enabled,
            name = name,
            url = url,
            version = version ?: 1,
            summary = summary,
            description = description,
            modified = modified
        )

    /**
     * The copy that actually runs for a package: the downloaded one only while it is strictly
     * newer than the bundled one, a tie always goes to the bundle. `null` when there is nothing
     * usable, which is also the case for a download without a version.
     */
    internal fun pickType(bundledVersion: Int?, remoteVersion: Int?): ScriptType? {
        if (bundledVersion == null) return if (remoteVersion == null) null else REMOTE

        return if (remoteVersion != null && remoteVersion > bundledVersion) REMOTE else BUNDLED
    }

    /** [pickType] over the script texts, kept for the tests of the rule itself. */
    internal fun pickScript(bundled: String?, remote: String?): Pair<ScriptType, String>? {
        return when (pickType(bundled?.let { versionOf(it) }, remote?.let { rawVersionOf(it) })) {
            REMOTE -> REMOTE to remote!!
            BUNDLED -> BUNDLED to bundled!!
            else -> null
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

    /**
     * Held while the cache is filled. A listing and a pull to refresh can start together, and
     * without this both would find the cache empty and open every file before either wrote a row.
     */
    private val cacheLock = Any()

    /**
     * Metadata of the scripts bundled in the APK, keyed by package name. Only the entry names are
     * listed; an entry is opened the first time the cache meets it and never again.
     */
    internal fun bundledMeta(context: Context): Map<String, ScriptMeta> =
        synchronized(cacheLock) { buildBundledMeta(context) }

    private fun buildBundledMeta(context: Context): Map<String, ScriptMeta> {
        // an update can change every bundled file, so its rows are dropped once per install
        dropBundledRowsIfUpdated(context)

        val cached = ScriptDatabase.readAll(context, ScriptOrigin.BUNDLED)
        val found = linkedMapOf<String, ScriptMeta>()

        try {
            ZipFile(context.applicationInfo.sourceDir).use { zip ->
                val files = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith(assets_scripts_()) && it.name.endsWith(_js()) }
                    .map { it.name.removePrefix(assets_scripts_()) }
                    .toList()

                val added = arrayListOf<ScriptMeta>()
                files.forEach { file ->
                    val meta = cached[file] ?: readBundled(zip, file)?.also { added += it }
                        ?: return@forEach
                    found[file.removeSuffix(_js())] = meta
                }

                ScriptDatabase.save(context, added)
                // nothing the APK no longer ships stays behind in the cache
                ScriptDatabase.retain(context, ScriptOrigin.BUNDLED, files.toSet())
            }
        } catch (e: Exception) {
            // an unreadable APK is answered from the cache alone
            cached.forEach { (file, meta) -> found[file.removeSuffix(_js())] = meta }
        }

        return found
    }

    private fun readBundled(zip: ZipFile, file: String): ScriptMeta? {
        return try {
            val entry = zip.getEntry(assets_scripts_() + file) ?: return null
            val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            metaOf(file, ScriptOrigin.BUNDLED, text, modifiedOf(text))
        } catch (e: Exception) {
            null
        }
    }

    /** What the Xposed store holds, split the way `inventory` lists it. */
    internal class StoreMeta(
        /** User scripts, keyed by package name. */
        val user: Map<String, ScriptMeta>,
        /** Downloaded copies, keyed by the name between `rs_` and `.js`. */
        val remote: Map<String, ScriptMeta>
    )

    internal fun storeMeta(context: Context, service: XposedService?): StoreMeta =
        synchronized(cacheLock) { buildStoreMeta(context, service) }

    private fun buildStoreMeta(context: Context, service: XposedService?): StoreMeta {
        if (service == null) return StoreMeta(emptyMap(), emptyMap())

        val files = RemoteFiles.list(service)
        val cached = ScriptDatabase.readAll(context, ScriptOrigin.STORE)
        val user = linkedMapOf<String, ScriptMeta>()
        val remote = linkedMapOf<String, ScriptMeta>()
        val added = arrayListOf<ScriptMeta>()

        files.forEach { file ->
            if (!file.endsWith(_js())) return@forEach

            // downloaded copies live in the same file store and are not user scripts
            val isRemote = file.startsWith(rs_())
            val name = if (isRemote) file.removePrefix(rs_()).removeSuffix(_js()) else file.removeSuffix(_js())
            if (name.isEmpty()) return@forEach

            val meta = cached[file] ?: run {
                val text = RemoteFiles.read(service, file) ?: return@forEach
                // a user script is dated by the file itself, a published one by its header tag
                val modified = if (isRemote) modifiedOf(text) else RemoteFiles.lastModifiedAt(service, file)
                metaOf(file, ScriptOrigin.STORE, text, modified).also { added += it }
            }

            if (isRemote) remote[name] = meta else user[name] = meta
        }

        ScriptDatabase.save(context, added)
        // a deleted or unpublished script drops out of the cache with its file
        ScriptDatabase.retain(context, ScriptOrigin.STORE, files)

        return StoreMeta(user, remote)
    }

    /** The cache row of one script file, read out of the header block of its text. */
    internal fun metaOf(file: String, origin: ScriptOrigin, text: String, modified: Long) = ScriptMeta(
        file = file,
        origin = origin,
        author = authorOf(text),
        name = headerOf(text, name()),
        url = headerOf(text, url()),
        summary = headerOf(text, summary()),
        description = headerOf(text, description()),
        version = rawVersionOf(text),
        optIn = isOptIn(text),
        size = text.toByteArray(Charsets.UTF_8).size,
        modified = modified
    )

    /** Rows written for an older APK no longer describe the files it ships now. */
    private fun dropBundledRowsIfUpdated(context: Context) {
        val stamp = try {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        } catch (e: Exception) {
            return
        }

        if (ScriptDatabase.sync(context, APK_KEY) == stamp) return

        ScriptDatabase.deleteAll(context, ScriptOrigin.BUNDLED)
        ScriptDatabase.setSync(context, APK_KEY, stamp)
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
        description = info.description ?: info.summary.orEmpty(),
        modifiedAt = formatModified(info.modified),
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

    fun saveUserScript(context: Context, service: XposedService, packageName: String, text: String): Boolean {
        val file = userFile(packageName)
        if (!RemoteFiles.write(service, file, text)) return false

        // the row has to describe the text that was just written, not the one it replaced
        val modified = RemoteFiles.lastModifiedAt(service, file)
        ScriptDatabase.save(context, metaOf(file, ScriptOrigin.STORE, text, modified))
        return true
    }

    fun removeUserScript(context: Context, service: XposedService, packageName: String) {
        val file = userFile(packageName)
        if (RemoteFiles.delete(service, file)) {
            ScriptDatabase.delete(context, ScriptOrigin.STORE, file)
        }
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
