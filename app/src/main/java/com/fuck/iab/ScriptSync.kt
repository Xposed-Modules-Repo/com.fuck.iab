package com.fuck.iab

import io.github.libxposed.service.XposedService
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Downloads the published scripts into the module's shared data directory, where the module
 * reads them in the hooked apps.
 *
 * Every call blocks, so it belongs on a background dispatcher, and every call needs the
 * service connection because the directory belongs to the module.
 */
object ScriptSync {

    private const val TIMEOUT_MS = 20000

    /**
     * @param downloaded scripts that were written because they were new or had changed
     * @param removed scripts that the store no longer lists
     * @param total scripts the manifest lists
     */
    data class Result(val downloaded: Int, val removed: Int, val total: Int)

    fun sync(service: XposedService): Result {
        if (!RemoteScripts.configured) {
            throw IllegalStateException("no script store is configured for this build")
        }

        val manifestText = String(download(RemoteScripts.MANIFEST_URL), Charsets.UTF_8)
        val manifest = RemoteScripts.parseManifest(manifestText)

        if (manifest.isEmpty()) {
            throw IllegalStateException("the published manifest lists no scripts")
        }

        val present = RemoteFiles.list(service)
        val wanted = HashSet<String>(manifest.size)
        var downloaded = 0

        for (entry in manifest) {
            val name = RemoteScripts.fileName(entry.packageName)
            wanted += name

            val cached = if (present.contains(name)) RemoteFiles.read(service, name) else null
            if (!cached.isNullOrEmpty() && sha256(cached) == entry.sha256) continue

            val bytes = download(entry.url)

            if (sha256(bytes) != entry.sha256) {
                throw IOException("checksum mismatch for ${entry.packageName}")
            }

            if (!RemoteFiles.write(service, name, String(bytes, Charsets.UTF_8))) {
                throw IOException("could not store ${entry.packageName}")
            }

            downloaded++
        }

        // A script dropped from the store should stop running rather than stay behind.
        var removed = 0
        for (name in present) {
            if (!RemoteScripts.isScriptFile(name) || wanted.contains(name)) continue
            if (RemoteFiles.delete(service, name)) removed++
        }

        val stored = RemoteFiles.write(
            service,
            RemoteScripts.INDEX_FILE,
            RemoteScripts.indexJson(System.currentTimeMillis(), manifestText)
        )

        if (!stored) {
            throw IOException("could not store the manifest index")
        }

        return Result(downloaded, removed, manifest.size)
    }

    /** Time of the last successful sync, or 0 when nothing has been synced yet. */
    fun lastSync(service: XposedService): Long {
        return try {
            RemoteScripts.parseIndex(RemoteFiles.read(service, RemoteScripts.INDEX_FILE))?.fetchedAt ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun download(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FKIAB")
            setRequestProperty("Cache-Control", "no-cache")
        }

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code for $url")
            }

            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}
