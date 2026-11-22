package com.pulldock.data.extractor

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static guard tests (T140, T141): structural assertions that no remote-code-loading
 * or watermark-removal path exists in release code. These are floor tests — the code
 * review checkpoints in T170 remain authoritative.
 */
class GuardTests {

    /** Source roots scanned by both guards (resolves the repo root from the module dir). */
    private fun kotlinSources(): List<File> {
        var root = File(System.getProperty("user.dir") ?: ".")
        while (root != null && !File(root, "settings.gradle.kts").exists()) root = root.parentFile ?: return emptyList()
        val roots = listOf(
            File(root, "app/src/main"),
            File(root, "core"),
            File(root, "domain"),
            File(root, "data"),
            File(root, "engines"),
            File(root, "feature"),
        )
            // Only production sources are scanned (src/main); tests legitimately name
            // the forbidden markers to assert their absence.
        return roots.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.absolutePath.contains("/src/main/") }
                .toList()
        }
    }

    @Test
    fun `no dynamic code loading exists anywhere (T140, FR-058)`() {
        val forbidden = listOf(
            "DexClassLoader",
            "PathClassLoader",
            "InMemoryDexClassLoader",
            "loadDex",
            "System.load(", // explicit load() calls must be reviewed; none allowed here
        )
        kotlinSources().forEach { file ->
            val text = file.readText()
            forbidden.forEach { marker ->
                assertFalse(
                    "Forbidden remote/dynamic code marker '$marker' in ${file.path}",
                    text.contains(marker),
                )
            }
        }
    }

    @Test
    fun `no watermark-removal path exists (T141, FR-021)`() {
        val forbidden = listOf(
            "removewatermark",
            "watermarkremoval",
            "delogo",
            "nomosmotic",
            "watermarkremover",
        )
        kotlinSources().forEach { file ->
            val text = file.readText().lowercase()
            forbidden.forEach { marker ->
                assertFalse(
                    "Forbidden watermark-removal marker '$marker' in ${file.path}",
                    text.contains(marker),
                )
            }
        }
    }

    @Test
    fun `guard is actually scanning sources (self-check)`() {
        // The guards are only meaningful if they scanned a non-trivial tree.
        assertTrue("guard scanned too few sources — check working directory", kotlinSources().size > 50)
    }
}
