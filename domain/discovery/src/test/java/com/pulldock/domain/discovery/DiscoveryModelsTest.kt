package com.pulldock.domain.discovery

import com.pulldock.domain.discovery.model.DescriptorKind
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryModelsTest {

    @Test
    fun `category model includes adult and documents`() {
        assertTrue(SearchCategory.entries.contains(SearchCategory.ADULT))
        assertTrue(SearchCategory.entries.contains(SearchCategory.DOCUMENTS))
    }

    @Test
    fun `rights status values are stable`() {
        assertEquals(4, RightsStatus.entries.size)
        assertTrue(RightsStatus.entries.contains(RightsStatus.UNKNOWN))
    }

    @Test
    fun `descriptor kinds and sort orders exist`() {
        assertEquals(2, DescriptorKind.entries.size)
        assertEquals(5, SortOrder.entries.size)
    }
}