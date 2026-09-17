package com.fitnesslemon.app.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CachePolicyTest {
    @Test
    fun `cache is fresh inside configured window`() {
        val now = 1_000_000L
        assertTrue(CachePolicy.isFresh(now - CachePolicy.NEWS_MAX_AGE_MILLIS + 1, now))
    }

    @Test
    fun `future cache timestamp is not accepted`() {
        val now = 1_000_000L
        assertFalse(CachePolicy.isFresh(now + 1, now))
    }

    @Test
    fun `missing cache must refresh`() {
        assertTrue(CachePolicy.shouldRefresh(null, 1_000_000L))
    }
}
