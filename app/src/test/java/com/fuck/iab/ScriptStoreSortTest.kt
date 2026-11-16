package com.fuck.iab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ordering rules of `ScriptStore.inventory`: newest `// modified-at:` first, `global.js` last. */
class ScriptStoreSortTest {

    @Test
    fun newestModificationComesFirst() {
        val sorted = sort(
            listOf(
                script("com.a", ScriptType.BUNDLED, modified = 100L),
                script("com.b", ScriptType.REMOTE, modified = 300L),
                script("com.c", ScriptType.USER, modified = 200L)
            )
        )

        assertEquals(listOf("com.b", "com.c", "com.a"), sorted.map { it.packageName })
    }

    @Test
    fun globalJsIsPinnedToTheVeryBottom() {
        // `global.js` the runtime needs first is listed last, even with the newest modification
        val sorted = sort(
            listOf(
                script("global", ScriptType.BUNDLED, modified = 999L),
                script("com.a", ScriptType.BUNDLED, modified = 1L),
                script("com.b", ScriptType.USER, modified = 0L)
            )
        )

        assertEquals(listOf("com.a", "com.b", "global"), sorted.map { it.packageName })
    }

    @Test
    fun scriptsWithoutAModificationTimeSortLastButAboveGlobal() {
        val sorted = sort(
            listOf(
                script("com.a", ScriptType.BUNDLED, modified = 0L),
                script("com.b", ScriptType.GLOBAL_REMOTE, modified = 50L),
                script("global", ScriptType.BUNDLED, modified = 0L),
                script("com.c", ScriptType.OPT_IN, modified = 0L)
            )
        )

        assertEquals(listOf("com.b", "com.a", "com.c", "global"), sorted.map { it.packageName })
    }

    @Test
    fun tiesFallBackToPackageName() {
        val sorted = sort(
            listOf(
                script("com.c", ScriptType.USER, modified = 100L),
                script("com.a", ScriptType.USER, modified = 100L),
                script("com.b", ScriptType.USER, modified = 100L)
            )
        )

        assertEquals(listOf("com.a", "com.b", "com.c"), sorted.map { it.packageName })
    }

    @Test
    fun everyKindSharesOneModificationOrder() {
        // user, bundled, remote, global-remote and opt-in scripts all sort together
        val sorted = sort(
            listOf(
                script("com.a", ScriptType.USER, modified = 100L),
                script("com.b", ScriptType.REMOTE, modified = 400L),
                script("global_remote_one", ScriptType.GLOBAL_REMOTE, modified = 250L),
                script("optin_one", ScriptType.OPT_IN, modified = 350L),
                script("com.c", ScriptType.BUNDLED, modified = 50L),
                script("global", ScriptType.BUNDLED, modified = 1000L)
            )
        )

        assertEquals(
            listOf("com.b", "optin_one", "global_remote_one", "com.a", "com.c", "global"),
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

    private fun script(packageName: String, type: ScriptType, modified: Long) =
        ScriptInfo(
            packageName = packageName,
            type = type,
            author = null,
            enabled = true,
            modified = modified
        )

    private fun sort(scripts: List<ScriptInfo>) =
        scripts.sortedWith { a, b -> ScriptStore.compareScripts(a, b) }
}
