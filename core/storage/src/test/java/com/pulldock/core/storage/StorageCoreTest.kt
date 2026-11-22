package com.pulldock.core.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Storage security core (T161 subset): traversal rejection, sanitization, collision
 * strategy, and the finalizer's no-partial-publish guarantee.
 */
class StorageCoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- PathValidator (T153) ----

    @Test
    fun `traversal and absolute paths are rejected`() {
        assertEquals(PathValidator.Rejection.TRAVERSAL, PathValidator.validateRelativePath("../escape.bin"))
        assertEquals(PathValidator.Rejection.TRAVERSAL, PathValidator.validateRelativePath("a/../../escape.bin"))
        assertEquals(PathValidator.Rejection.ABSOLUTE_PATH, PathValidator.validateRelativePath("/etc/passwd"))
        assertEquals(PathValidator.Rejection.ILLEGAL_CHARACTERS, PathValidator.validateRelativePath("C:\\temp\\file"))
        assertNull(PathValidator.validateRelativePath("show/season 1/file.mkv"))
    }

    @Test
    fun `confine keeps targets inside the root and rejects escapes`() {
        val root = tmp.newFolder("root")
        assertNull(PathValidator.confine(root, "../outside.bin"))
        val inside = PathValidator.confine(root, "a/b/file.bin")
        assertNotNull(inside)
        assertTrue(inside!!.path.startsWith(root.canonicalFile.path))
    }

    // ---- FilenameSanitizer (T152) ----

    @Test
    fun `untrusted names are stripped to safe basenames`() {
        val traversal = FilenameSanitizer.sanitize("../../file.mkv")
        assertFalse(traversal.contains('/'))
        assertFalse(traversal.contains('\\'))
        assertFalse(traversal.contains(".."))
        assertTrue(traversal.contains("file.mkv"))
        val mixed = FilenameSanitizer.sanitize("bad/name\\with:chars?.txt")
        assertFalse(mixed.contains(Regex("[/\\\\:*?\"<>|]")))
        assertTrue(mixed.endsWith(".txt"))
        assertEquals("download", FilenameSanitizer.sanitize("   "))
        assertFalse(FilenameSanitizer.sanitize("name\u202Eexe.jpg").contains('\u202E'))
    }

    @Test
    fun `windows reserved names are renamed`() {
        assertTrue(FilenameSanitizer.sanitize("CON").startsWith("_"))
        assertTrue(FilenameSanitizer.sanitize("com1.mp3").startsWith("_"))
    }

    @Test
    fun `collision strategy produces name (2)_ext then (3) (FR-040)`() {
        val existing = setOf("video.mp4", "video (2).mp4")
        val resolved = FilenameSanitizer.resolveCollision("video.mp4") { it in existing }
        assertEquals("video (3).mp4", resolved)
        val first = FilenameSanitizer.resolveCollision("video.mp4") { false }
        assertEquals("video.mp4", first)
    }

    // ---- FileFinalizer (T151) ----

    @Test
    fun `verified temp file publishes atomically and the temp file disappears`() {
        val root = tmp.newFolder("publish")
        val finalizer = FileFinalizer(root)
        val temp = tmp.newFile("task.part").apply { writeText("complete-content") }

        val result = finalizer.publish(temp, "output.bin")

        assertTrue(result is FileFinalizer.Result.Published)
        val published = File((result as FileFinalizer.Result.Published).path)
        assertEquals("complete-content", published.readText())
        assertFalse(temp.exists())
    }

    @Test
    fun `failed verification never publishes anything (FR-039)`() {
        val root = tmp.newFolder("verify")
        val finalizer = FileFinalizer(root, verifier = { it.length() > 0 })
        val temp = tmp.newFile("empty.part") // 0 bytes: fails verification

        val result = finalizer.publish(temp, "output.bin")

        assertTrue(result is FileFinalizer.Result.Rejected)
        assertFalse(File(root, "output.bin").exists())
        assertTrue(temp.exists()) // kept for recovery
    }

    @Test
    fun `traversal-shaped filenames never publish outside the root (FR-047)`() {
        val root = tmp.newFolder("confine")
        val finalizer = FileFinalizer(root)
        val temp = tmp.newFile("evil.part").apply { writeText("x") }

        val result = finalizer.publish(temp, "../evil.bin")

        assertTrue(result is FileFinalizer.Result.Rejected)
        assertFalse(File(root.parentFile, "evil.bin").exists())
    }

    @Test
    fun `existing targets are renamed with the collision strategy, never overwritten`() {
        val root = tmp.newFolder("collision")
        File(root, "out.bin").writeText("original")
        val finalizer = FileFinalizer(root)
        val temp = tmp.newFile("new.part").apply { writeText("new-content") }

        val result = finalizer.publish(temp, "out.bin")

        assertTrue(result is FileFinalizer.Result.Published)
        assertEquals("original", File(root, "out.bin").readText())
        assertEquals("new-content", File(root, "out (2).bin").readText())
    }
}
