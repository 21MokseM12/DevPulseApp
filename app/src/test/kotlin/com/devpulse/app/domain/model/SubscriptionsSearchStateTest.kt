package com.devpulse.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionsSearchStateTest {
    @Test
    fun normalizedQuery_trimsAndLowercasesInput() {
        val state = SubscriptionsSearchState(query = "  TAG:Kotlin News ")

        assertEquals("tag:kotlin news", state.normalizedQuery())
    }

    @Test
    fun hasActiveCriteria_detectsDefaultAndConfiguredState() {
        assertFalse(SubscriptionsSearchState().hasActiveCriteria())
        assertTrue(SubscriptionsSearchState(query = " kotlin ").hasActiveCriteria())
        assertTrue(SubscriptionsSearchState(tagFilter = "release").hasActiveCriteria())
        assertTrue(SubscriptionsSearchState(hasFiltersOnly = true).hasActiveCriteria())
        assertTrue(SubscriptionsSearchState(onlyTagged = true).hasActiveCriteria())
        assertTrue(
            SubscriptionsSearchState(sortMode = SubscriptionsSortMode.URL_ASCENDING).hasActiveCriteria(),
        )
    }
}
