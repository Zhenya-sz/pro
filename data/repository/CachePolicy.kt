package com.fitnesslemon.app.data.repository

import java.util.concurrent.TimeUnit

/** Common freshness policy for Room-backed feature repositories. */
object CachePolicy {
    const val NEWS_MAX_AGE_MINUTES = 30L
    const val NEWS_MAX_AGE_MILLIS = NEWS_MAX_AGE_MINUTES * 60L * 1000L

    fun isFresh(cachedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        now - cachedAt in 0..NEWS_MAX_AGE_MILLIS
}
