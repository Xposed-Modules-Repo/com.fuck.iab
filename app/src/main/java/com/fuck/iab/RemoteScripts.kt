package com.fuck.iab

import android.content.Context
import io.github.libxposed.service.XposedService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** How a pull-to-refresh ended, so the caller can report it. */
enum class RefreshOutcome { DONE, NO_SERVICE, FAILED }

/**
 * Pull to refresh: reads `scripts/manifest.json` from the repository and keeps the downloaded
 * copies in the Xposed file store in sync with it. Nothing is written until a download has
 * been read back and checked, so a failed write can never leave a broken script behind.
 */
object RemoteScripts {

    /** Versions published in the repository. `globals` are the ones from `scripts/global`. */
    private class Manifest(
        val packages: Map<String, Int>,
        val globals: Map<String, Int>
    )

    fun refresh(context: Context, service: XposedService?): RefreshOutcome {
        if (service == null) {
            return RefreshOutcome.NO_SERVICE
        }

        val manifest = fetchManifest() ?: return RefreshOutcome.FAILED

        val bundled = ScriptStore.bundledScripts(context)
        var failed = false

        manifest.packages.forEach { (name, version) ->
            // `global` is the table of global scripts, the bundled global.js is never downloaded
            if (name == global()) return@forEach

            val bundledVersion = bundled[name]?.let { ScriptStore.versionOf(it) }
            if (bundledVersion != null && bundledVersion >= version) return@forEach

            val local = RemoteFiles.read(service, ScriptStore.remoteFile(name))
            val localVersion = local?.let { ScriptStore.rawVersionOf(it) }
            if (localVersion != null && localVersion >= version) return@forEach

            val source = fetch(scriptsRawUrl() + src() + name + _js() + "${rand__()}${System.currentTimeMillis()}")
            if (source == null || !store(service, ScriptStore.remoteFile(name), source)) {
                failed = true
            }
        }

        manifest.globals.forEach { (name, version) ->
            val local = RemoteFiles.read(service, ScriptStore.remoteFile(name))
            val localVersion = local?.let { ScriptStore.rawVersionOf(it) }
            if (localVersion != null && localVersion >= version) return@forEach

            val source = fetch(scriptsRawUrl() + global() + '/' + name + _js() + "${rand__()}${System.currentTimeMillis()}")
            if (source == null || !store(service, ScriptStore.remoteFile(name), source)) {
                failed = true
            }
        }

        forgetStale(service, isKnown = { name ->
            name in manifest.packages || name in manifest.globals
        })

        return if (failed) RefreshOutcome.FAILED else RefreshOutcome.DONE
    }

    /** Drops every downloaded copy that is no longer listed in manifest.json. */
    private fun forgetStale(service: XposedService, isKnown: (String) -> Boolean) {
        RemoteFiles.list(service).forEach { file ->
            if (!file.startsWith(rs_()) || !file.endsWith(_js())) return@forEach

            val name = file.removePrefix(rs_()).removeSuffix(_js())
            if (name.isEmpty() || !isKnown(name)) {
                RemoteFiles.delete(service, file)
            }
        }
    }

    private fun store(service: XposedService, file: String, text: String): Boolean {
        RemoteFiles.write(service, file, text)
        if (RemoteFiles.read(service, file) != text) {
            RemoteFiles.delete(service, file)
            return false
        }
        return true
    }

    private fun fetchManifest(): Manifest? {
        val raw = fetch(scriptsRawUrl() + manifestJson() + "${rand__()}${System.currentTimeMillis()}") ?: return null

        return try {
            val json = JSONObject(raw)
            val packages = hashMapOf<String, Int>()
            val globals = hashMapOf<String, Int>()

            for (key in json.keys()) {
                if (key == global()) {
                    val table = json.optJSONObject(key) ?: continue
                    for (name in table.keys()) {
                        globals[name] = table.optInt(name, 1)
                    }
                } else {
                    packages[key] = json.optInt(key, 1)
                }
            }

            Manifest(packages, globals)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetch(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.instanceFollowRedirects = true

            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    null
                } else {
                    connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
