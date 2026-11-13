package com.fuck.iab

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.content.edit
import com.fuck.iab.ScriptType.BUNDLED
import com.fuck.iab.ScriptType.USER
import io.github.libxposed.service.XposedService
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

enum class ScriptType { BUNDLED, USER }

data class ScriptInfo(
    val packageName: String,
    val type: ScriptType,
    val source: String,
    val author: String?,
    val size: Int = 0,
    val enabled: Boolean
)

data class ScriptDetails(
    val packageName: String,
    val author: String?,
//    val version: Int = 0,
    val description: String
)

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

object ScriptStore {

    fun inventory(context: Context, service: XposedService?): List<ScriptInfo> {
        val bundledScripts = bundledScripts(context)
        val disabledBundleScripts = if (service != null) disabledApps(service, "bundle") else emptySet()

        val userScripts = userScripts(service)
        val disabledUserScripts = if (service != null) disabledApps(service, "user") else emptySet()

        val ret = arrayListOf<ScriptInfo>()

        bundledScripts.forEach { (packageName, script) ->
            ret.add(
                ScriptInfo(
                    packageName = packageName,
                    type = BUNDLED,
                    source = script,
                    author = authorOf(script),
                    size = script.toByteArray(Charsets.UTF_8).size,
                    enabled = !disabledBundleScripts.contains(packageName)
                )
            )
        }

        userScripts.forEach { (packageName, script) ->
            if (script == null) return@forEach
            ret.add(
                ScriptInfo(
                    packageName = packageName,
                    type = USER,
                    source = script,
                    author = authorOf(script),
                    size = script.toByteArray(Charsets.UTF_8).size,
                    enabled = !disabledUserScripts.contains(packageName)
                )
            )
        }

        return ret
    }

    private fun userScripts(service: XposedService?): Map<String, String?> {
        return if (service == null) emptyMap()
        else try {
            RemoteFiles.list(service)
                .filter { it.endsWith(".js") }
                .associate {
                    it.removeSuffix(".js") to RemoteFiles.read(service, it)
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun bundledScripts(context: Context): Map<String, String> {
        return try {
            ZipFile(context.applicationInfo.sourceDir).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("assets/scripts/") && it.name.endsWith(".js") }
                    .associate { entry ->
                        val packageName = entry.name.removePrefix("assets/scripts/").removeSuffix(".js")
                        val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        packageName to text
                    }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun authorOf(script: String): String? {
        val firstLine = script.lineSequence().firstOrNull() ?: return null
        if (!firstLine.startsWith("// author: ")) return null
        return firstLine.removePrefix("// author: ").trim().ifEmpty { null }
    }

    fun details(context: Context, packageName: String): ScriptDetails {
        val bundled = bundledScripts(context)[packageName]!!

        val lines = bundled.lineSequence().take(3).toList()

        val author = lines.getOrNull(0)?.let {
            if (it.startsWith("// author: ")) {
                it.removePrefix("// author: ").trim().ifEmpty { null }
            } else null
        }
//        val version = lines.getOrNull(1)?.let {
//            if (it.startsWith("// version: ")) {
//                it.removePrefix("// version: ").trim().ifEmpty { null }
//            } else null
//        }?.toInt() ?: 1
        val description = lines.getOrNull(1)?.let {
            if (it.startsWith("// description: ")) {
                it.removePrefix("// description: ").trim().ifEmpty { null }
            } else null
        } ?: ""

        return ScriptDetails(
            packageName = packageName,
            author = author,
//            version = version,
            description = description
        )
    }

    fun userFile(packageName: String): String {
        return if (packageName == "global") "${user_script()}.js" else "${packageName}.js"
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

    fun disabledApps(service: XposedService, type: String): Set<String> {
        return try {
            service.getRemotePreferences(fuck_iab()).getStringSet("disabled_${type}_scripts", emptySet())
                ?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setEnabled(service: XposedService, packageName: String, type: ScriptType, enabled: Boolean) {
        val prefs = service.getRemotePreferences(fuck_iab())
        val disabled = prefs.getStringSet("disabled_${if (type == BUNDLED) "bundled" else "user"}_scripts", emptySet())?.toMutableSet()
            ?: mutableSetOf()

        if (enabled) disabled.remove(packageName) else disabled.add(packageName)

        prefs.edit(commit = true) { putStringSet("disabled_${if (type == BUNDLED) "bundled" else "user"}_scripts", disabled) }
    }

    fun scope(service: XposedService): List<String> {
        return try {
            service.scope
        } catch (e: Exception) {
            emptyList()
        }
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

    fun appLabel(context: Context, packageName: String): String {
        if (packageName == "global") return context.getString(R.string.scripts_all_apps)

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
        if (packageName == "global") return null

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
