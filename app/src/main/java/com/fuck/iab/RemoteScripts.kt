package com.fuck.iab

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The published script store, plus the names of the files the module keeps in its shared
 * data directory.
 *
 * `scripts/build.js --dist` compiles the store and `scripts/publish.js` writes it to a
 * branch of the project repository. The manager downloads it into the module's directory,
 * so a new script for an app shows up without building and installing a new APK.
 *
 * Only per-app scripts are published. The global script carries the bridge and is compiled
 * into the APK, so it is never fetched or replaced from the network.
 *
 * Names inside the shared directory must not contain path separators or dots, which is why
 * a package name is turned into a flat file name by [fileName].
 */
object RemoteScripts {

    /** The store this build updates from, taken from `scriptsBaseUrl` in gradle.properties. */
    const val BASE_URL: String = BuildConfig.SCRIPTS_BASE_URL

    /** Empty when this build was made without a store configured. */
    val configured: Boolean get() = BASE_URL.isNotBlank()

    val MANIFEST_URL: String get() = "${BASE_URL}manifest.json"

    /** Cached copy of the manifest that was downloaded last. Never treated as a script. */
    const val INDEX_FILE = "rs_index"

    /** Package names whose scripts must not run, stored in the module preferences. */
    const val DISABLED_SCRIPTS_KEY = "disabled_scripts"

    /** The global script, which is part of the APK rather than of the store. */
    const val GLOBAL = "global"

    private const val FILE_PREFIX = "rs_"
    private const val USER_PREFIX = "us_"
    private const val SCRIPTS_PATH = "scripts/"
    private const val FORMAT = 1

    /** One script in the store. */
    data class Entry(
        val packageName: String,
        val file: String,
        val sha256: String,
        val size: Long,
        val author: String?
    ) {
        val url: String get() = "$BASE_URL$SCRIPTS_PATH$file"
    }

    /** The manifest that was downloaded last, with when it was fetched. */
    data class Index(
        val fetchedAt: Long,
        val publishedAt: Long,
        val entries: List<Entry>
    )

    /**
     * Reads the entries of a published manifest.
     *
     * A global entry is skipped on purpose: an older store published one, and it must never
     * take the place of the script compiled into the APK.
     */
    fun parseManifest(text: String): List<Entry> {
        val json = JSONObject(text)

        val format = json.optInt("format", 0)
        if (format != FORMAT) {
            throw IllegalStateException("unsupported manifest format: $format")
        }

        val scripts: JSONArray = json.optJSONArray("scripts") ?: JSONArray()
        val entries = ArrayList<Entry>(scripts.length())

        for (i in 0 until scripts.length()) {
            val script = scripts.optJSONObject(i) ?: continue

            val packageName = script.optString("package")
            val file = script.optString("file")
            val sha256 = script.optString("sha256")

            if (packageName.isEmpty() || file.isEmpty() || sha256.isEmpty()) continue
            if (packageName == GLOBAL) continue

            entries += Entry(
                packageName = packageName,
                file = file,
                sha256 = sha256.lowercase(Locale.US),
                size = script.optLong("size"),
                // A script without an author line is published as a json null, which optString
                // would turn into the text "null".
                author = script.opt("author")
                    ?.toString()
                    ?.takeIf { it.isNotBlank() && it != "null" }
            )
        }

        return entries
    }

    /** The cached index, holding the manifest text so the store survives a restart. */
    fun indexJson(fetchedAt: Long, manifestText: String): String {
        return JSONObject().apply {
            put("fetchedAt", fetchedAt)
            put("manifest", JSONObject(manifestText))
        }.toString()
    }

    fun parseIndex(text: String?): Index? {
        if (text.isNullOrBlank()) return null

        return try {
            val json = JSONObject(text)
            val manifest = json.optJSONObject("manifest") ?: return null

            Index(
                fetchedAt = json.optLong("fetchedAt"),
                publishedAt = publishedAt(manifest.optString("generatedAt")),
                entries = parseManifest(manifest.toString())
            )
        } catch (e: Exception) {
            null
        }
    }

    /** The manifest stamps its build time as an ISO-8601 string. */
    private fun publishedAt(generatedAt: String): Long {
        if (generatedAt.isEmpty()) return 0

        return try {
            java.time.Instant.parse(generatedAt).toEpochMilli()
        } catch (e: Exception) {
            0
        }
    }

    /** Flat file name of the synced copy of [packageName] in the shared data directory. */
    fun fileName(packageName: String): String = flatName(FILE_PREFIX, packageName)

    /** Flat file name of the user copy of [packageName], which overrides the synced one. */
    fun userFileName(packageName: String): String = flatName(USER_PREFIX, packageName)

    fun isScriptFile(name: String): Boolean {
        return name.startsWith(FILE_PREFIX) && name != INDEX_FILE
    }

    private fun flatName(prefix: String, packageName: String): String {
        val safe = buildString(packageName.length) {
            packageName.forEach { append(if (it.isLetterOrDigit()) it else '_') }
        }

        return "$prefix${safe}_${String.format(Locale.US, "%08x", packageName.hashCode())}"
    }
}
