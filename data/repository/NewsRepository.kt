package com.fitnesslemon.app.data.repository

import com.fitnesslemon.app.data.api.ApiService
import com.fitnesslemon.app.data.database.NewsDao
import com.fitnesslemon.app.data.database.NewsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class NewsRepository(
    private val api: ApiService,
    private val dao: NewsDao
) {
    private val _newsFlow = MutableStateFlow<List<NewsEntity>>(emptyList())
    val newsFlow: StateFlow<List<NewsEntity>> = _newsFlow.asStateFlow()

    suspend fun loadNews(force: Boolean = false): Result<List<NewsEntity>> =
        withContext(Dispatchers.IO) {
            val cached = dao.getAll()
            val cachedAt = if (cached.isNotEmpty()) cached.firstOrNull()?.cachedAt ?: 0L else 0L

            val shouldRefresh =
                force || cached.isEmpty() || (cachedAt != 0L && !CachePolicy.isFresh(cachedAt))

            if (!shouldRefresh && cached.isNotEmpty()) {
                _newsFlow.value = cached
                return@withContext Result.success(cached)
            }

            try {
                val response = api.getNews()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Ошибка загрузки новостей"))
                }

                val entities = response.body().orEmpty().map { item ->
                    NewsEntity(
                        id = item.id,
                        title = item.title,
                        content = item.content,
                        excerpt = item.excerpt,
                        thumbnail = item.thumbnail,
                        authorName = item.authorName,
                        date = item.date,
                        viewsCount = item.likesCount,
                        likesCount = item.likesCount,
                        sendPush = 0,
                        cachedAt = System.currentTimeMillis()
                    )
                }

                if (entities.isNotEmpty()) {
                    dao.clearAll()
                    dao.insertAll(entities)
                }

                _newsFlow.value = entities
                Result.success(entities)
            } catch (e: Exception) {
                if (cached.isNotEmpty()) {
                    _newsFlow.value = cached
                    Result.success(cached)
                } else {
                    Result.failure(e)
                }
            }
        }

    suspend fun refreshNews(): Result<List<NewsEntity>> = loadNews(force = true)

    suspend fun getCachedNews(): List<NewsEntity> = withContext(Dispatchers.IO) {
        val items = dao.getAll()
        _newsFlow.value = items
        items
    }

    suspend fun likeNews(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            api.likeNews(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveNews(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            api.saveNews(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
