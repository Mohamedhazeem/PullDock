package com.pulldock.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import com.pulldock.core.model.DownloadState

class PdDownloadStatusTest {

    @Test
    fun runningAndCompletedUseSuccessTone() {
        assertEquals(PdStatusTone.Success, pdStatusTone(DownloadState.RUNNING))
        assertEquals(PdStatusTone.Success, pdStatusTone(DownloadState.COMPLETED))
    }

    @Test
    fun failedUsesErrorToneAndDistinctIcon() {
        assertEquals(PdStatusTone.Error, pdStatusTone(DownloadState.FAILED))
        assertNotEquals(pdStatusIcon(DownloadState.FAILED), pdStatusIcon(DownloadState.COMPLETED))
    }

    @Test
    fun pausedAndRunningHaveDistinctIcons() {
        assertNotEquals(pdStatusIcon(DownloadState.PAUSED), pdStatusIcon(DownloadState.RUNNING))
    }
}