package com.fitnesslemon.app.data.repository

/** Shared freshness policy for Room-backed feature repositories. */
object CachePolicy {
    const val NEWS_MAX_AGE_MINUTES = 30L
    const val NEWS_MAX_AGE_MILLIS = NEWS_MAX_AGE_MINUTES * 60L * 1000L

    fun isFresh(cachedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        now >= cachedAt && now - cachedAt <= NEWS_MAX_AGE_MILLIS

    fun shouldRefresh(cachedAt: Long?, now: Long = System.currentTimeMillis()): Boolean =
        cachedAt == null || !isFresh(cachedAt, now)
}
