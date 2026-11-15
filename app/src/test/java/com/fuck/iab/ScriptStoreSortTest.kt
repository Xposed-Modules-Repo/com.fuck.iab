package com.fuck.iab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ordering rules of `ScriptStore.inventory`, highest priority first. */
class ScriptStoreSortTest {

    @Test
    fun userScriptGoesFirstAndGlobalGoesLast() {
        val sorted = sort(
            listOf(
                script("com.b", ScriptType.USER),
                script("global", ScriptType.BUNDLED),
                script("com.a", ScriptType.BUNDLED),
                script("user_script", ScriptType.USER),
                script("com.c", ScriptType.USER)
            )
        )

        assertEquals(
            listOf("user_script", "com.a", "com.b", "com.c", "global"),
            sorted.map { it.packageName }
        )
    }

    @Test
    fun bundledScriptsGoAboveUserScripts() {
        val sorted = sort(
            listOf(
                script("com.a", ScriptType.USER),
                script("com.b", ScriptType.BUNDLED),
                script("com.c", ScriptType.USER)
            )
        )

        assertEquals(listOf("com.b", "com.a", "com.c"), sorted.map { it.packageName })
    }

    @Test
    fun bundledScriptsAreSortedByAddedIn() {
        val sorted = sort(
            listOf(
                script("com.a", ScriptType.BUNDLED),
                script("com.b", ScriptType.BUNDLED),
                script("com.c", ScriptType.BUNDLED),
                script("com.d", ScriptType.BUNDLED),
                script("global", ScriptType.BUNDLED)
            ),
            addedIn = mapOf(
                "com.a" to "1.6.12",
                "com.b" to "1.7.1",
                "com.c" to "1.6.2",
                "com.d" to "1.7"
            )
        )

        assertEquals(
            listOf("com.b", "com.d", "com.a", "com.c", "global"),
            sorted.map { it.packageName }
        )
    }

    @Test
    fun userScriptsAreSortedByModifiedAt() {
        val sorted = sort(
            listOf(
                script("com.a", ScriptType.USER),
                script("com.b", ScriptType.USER),
                script("com.c", ScriptType.USER)
            ),
            modifiedAt = mapOf(
                "com.a" to 100L,
                "com.b" to 300L,
                "com.c" to 200L
            )
        )

        assertEquals(listOf("com.b", "com.c", "com.a"), sorted.map { it.packageName })
    }

    @Test
    fun tiesFallBackToPackageName() {
        val sorted = sort(
            listOf(
                script("com.c", ScriptType.USER),
                script("com.a", ScriptType.USER),
                script("com.b", ScriptType.USER)
            )
        )

        assertEquals(listOf("com.a", "com.b", "com.c"), sorted.map { it.packageName })
    }

    @Test
    fun globalRemoteScriptsGoAboveBundledAndRemoteOnes() {
        val sorted = sort(
            listOf(
                script("com.a", ScriptType.USER),
                script("com.b", ScriptType.REMOTE),
                script("test", ScriptType.GLOBAL_REMOTE),
                script("com.c", ScriptType.BUNDLED),
                script("test2", ScriptType.GLOBAL_REMOTE),
                script("user_script", ScriptType.USER)
            ),
            addedIn = mapOf("com.b" to "1.7.0", "com.c" to "1.6.0"),
            modifiedAt = mapOf("test" to 100L, "test2" to 300L)
        )

        assertEquals(
            listOf("user_script", "test2", "test", "com.b", "com.c", "com.a"),
            sorted.map { it.packageName }
        )
    }

    @Test
    fun bundledAndRemoteScriptsShareOneAddedInOrder() {
        val sorted = sort(
            listOf(
                script("com.a", ScriptType.BUNDLED),
                script("com.b", ScriptType.REMOTE),
                script("com.c", ScriptType.BUNDLED),
                script("com.d", ScriptType.REMOTE)
            ),
            addedIn = mapOf(
                "com.a" to "1.7.1",
                "com.b" to "1.7.9",
                "com.c" to "1.6.2",
                "com.d" to "1.7"
            )
        )

        assertEquals(
            listOf("com.b", "com.a", "com.d", "com.c"),
            sorted.map { it.packageName }
        )
    }

    @Test
    fun aRemoteCopyWinsOnlyWhileItIsNewer() {
        val bundled = "// version: 3\nconsole.log(1)"

        assertEquals(
            ScriptType.REMOTE,
            pick(bundled, "// version: 4\nconsole.log(2)")?.first
        )
        assertEquals(
            ScriptType.BUNDLED,
            pick(bundled, "// version: 3\nconsole.log(2)")?.first
        )
        assertEquals(
            ScriptType.BUNDLED,
            pick(bundled, "// version: 2\nconsole.log(2)")?.first
        )
    }

    @Test
    fun aRemoteCopyWithoutABundleIsUsed() {
        assertEquals(
            ScriptType.REMOTE,
            pick(null, "// version: 1\nconsole.log(2)")?.first
        )
    }

    @Test
    fun aDownloadWithoutAVersionIsNeverUsed() {
        assertNull(pick(null, "console.log(2)"))
        assertEquals(ScriptType.BUNDLED, pick("// version: 1\nconsole.log(1)", "console.log(2)")?.first)
    }

    private fun pick(bundled: String?, remote: String?) = ScriptStore.pickScript(bundled, remote)

    private fun script(packageName: String, type: ScriptType) =
        ScriptInfo(
            packageName = packageName,
            type = type,
            source = "",
            author = null,
            enabled = true
        )

    private fun sort(
        scripts: List<ScriptInfo>,
        addedIn: Map<String, String> = emptyMap(),
        modifiedAt: Map<String, Long> = emptyMap()
    ) = scripts.sortedWith { a, b -> ScriptStore.compareScripts(a, b, addedIn, modifiedAt) }
}
