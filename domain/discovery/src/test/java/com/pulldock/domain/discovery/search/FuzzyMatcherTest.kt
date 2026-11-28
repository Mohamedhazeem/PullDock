package com.pulldock.domain.discovery.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    @Test
    fun `exact match scores one`() {
        assertEquals(1.0, FuzzyMatcher.score("The Dark Knight", "The Dark Knight"), 0.0)
    }

    @Test
    fun `case and surrounding whitespace are ignored`() {
        assertEquals(1.0, FuzzyMatcher.score("  the   dark  knight ", "The Dark Knight"), 0.0)
    }

    @Test
    fun `single typo scores higher than a far string`() {
        val oneTypo = FuzzyMatcher.score("samsng", "samsung")
        val far = FuzzyMatcher.score("samsng", "microsoft windows")
        assertTrue("oneTypo=$oneTypo far=$far", oneTypo > far)
    }

    @Test
    fun `adjacent transposition is treated as one edit`() {
        val transposed = FuzzyMatcher.score("teh", "the")
        val unrelated = FuzzyMatcher.score("teh", "zzz")
        assertTrue("transposed=$transposed unrelated=$unrelated", transposed > unrelated)
        assertTrue(transposed > 0.6)
    }

    @Test
    fun `insertion deletion substitution each beat a far string`() {
        val far = FuzzyMatcher.score("kitten", "elephant")
        assertTrue(FuzzyMatcher.score("kitten", "sitten") > far) // substitution
        assertTrue(FuzzyMatcher.score("kitten", "kiten") > far) // deletion
        assertTrue(FuzzyMatcher.score("kitten", "kittens") > far) // insertion
    }

    @Test
    fun `short query does not wildly match unrelated short word`() {
        val score = FuzzyMatcher.score("ab", "xy")
        assertTrue("score=$score", score < 0.5)
    }

    @Test
    fun `blank query or target scores zero`() {
        assertEquals(0.0, FuzzyMatcher.score("", "anything"), 0.0)
        assertEquals(0.0, FuzzyMatcher.score("   ", "anything"), 0.0)
        assertEquals(0.0, FuzzyMatcher.score("anything", ""), 0.0)
    }

    @Test
    fun `unicode rtl and emoji do not throw and are finite`() {
        assertTrue(FuzzyMatcher.score("مرحبا", "مرحبا").isFinite())
        assertTrue(FuzzyMatcher.score("emoji 😀 title", "emoji 😀 title").isFinite())
        assertEquals(1.0, FuzzyMatcher.score("emoji 😀 title", "emoji 😀 title"), 0.0)
    }

    @Test
    fun `long title completes with finite score`() {
        val longTitle = "Some.1080p.BluRay.x264-Group[" + "x".repeat(200) + "]"
        val score = FuzzyMatcher.score("some 1080p bluray", longTitle)
        assertTrue(score.isFinite())
        assertTrue(score in 0.0..1.0)
    }

    @Test
    fun `deterministic across repeated calls`() {
        val first = FuzzyMatcher.score("dark knight", "The Dark Knight 2008 1080p")
        val second = FuzzyMatcher.score("dark knight", "The Dark Knight 2008 1080p")
        assertEquals(first, second, 0.0)
    }
}