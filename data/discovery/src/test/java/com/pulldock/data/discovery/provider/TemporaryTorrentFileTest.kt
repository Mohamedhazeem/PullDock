package com.pulldock.data.discovery.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryTorrentFileTest {

    @Test
    fun `bencoded dict is a torrent`() {
        assertTrue(TemporaryTorrentFile.looksLikeTorrent("d8:announce".toByteArray()))
    }

    @Test
    fun `html error page is rejected`() {
        assertFalse(TemporaryTorrentFile.looksLikeTorrent("<!DOCTYPE html><html>".toByteArray()))
    }

    @Test
    fun `empty is rejected`() {
        assertFalse(TemporaryTorrentFile.looksLikeTorrent(ByteArray(0)))
    }
}