package com.fitnesslemon.app.data.repository

import android.content.Context
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.NewsResponse
import com.fitnesslemon.app.data.database.NewsDatabase
import com.fitnesslemon.app.data.database.NewsEntity
import com.fitnesslemon.app.data.models.News
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response

class NewsRepository(private val context: Context) {

    private val newsDao by lazy {
        NewsDatabase.getInstance(context).newsDao()
    }

    // Получение новостей из API
    suspend fun getNews(
        perPage: Int = 20,
        page: Int = 1
    ): Response<NewsResponse> {
        return try {
            val response = ApiClient.apiService.getNews(perPage, page)
            // Сохраняем в кэш при успешном ответе
            if (response.isSuccessful) {
                val newsData = response.body()
                newsData?.data?.news?.let { newsList ->
                    withContext(Dispatchers.IO) {
                        newsDao.insertAll(newsList.map { it.toEntity() })
                    }
                }
            }
            response
        } catch (e: Exception) {
            // Если API не доступен, пробуем получить из кэша
            val cachedNews = getCachedNews()
            if (cachedNews.isNotEmpty()) {
                // Возвращаем успешный ответ с кэшированными данными
                val response = NewsResponse(
                    success = true,
                    message = "Загружено из кэша",
                    data = com.fitnesslemon.app.data.api.NewsData(
                        news = cachedNews,
                        total = cachedNews.size,
                        page = 1,
                        perPage = cachedNews.size,
                        totalPages = 1
                    )
                )
                return retrofit2.Response.success(response)
            }
            throw e
        }
    }

    // Получение кэшированных новостей из Room
    suspend fun getCachedNews(): List<News> {
        return withContext(Dispatchers.IO) {
            newsDao.getAll().map { it.toModel() }
        }
    }

    // Получение новостей как Flow (для LiveData)
    fun getNewsFlow(): Flow<List<News>> {
        return newsDao.getAllFlow().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun likeNews(newsId: Int): Response<com.fitnesslemon.app.data.api.ApiResponse> {
        val token = PreferencesManager.getToken() ?: return retrofit2.Response.error(
            401,
            okhttp3.ResponseBody.create(null, "Unauthorized")
        )
        return ApiClient.apiService.likeNews("Bearer $token", newsId)
    }

    suspend fun unlikeNews(newsId: Int): Response<com.fitnesslemon.app.data.api.ApiResponse> {
        val token = PreferencesManager.getToken() ?: return retrofit2.Response.error(
            401,
            okhttp3.ResponseBody.create(null, "Unauthorized")
        )
        return ApiClient.apiService.unlikeNews("Bearer $token", newsId)
    }

    suspend fun saveNews(newsId: Int): Response<com.fitnesslemon.app.data.api.ApiResponse> {
        val token = PreferencesManager.getToken() ?: return retrofit2.Response.error(
            401,
            okhttp3.ResponseBody.create(null, "Unauthorized")
        )
        return ApiClient.apiService.saveNews("Bearer $token", newsId)
    }

    suspend fun unsaveNews(newsId: Int): Response<com.fitnesslemon.app.data.api.ApiResponse> {
        val token = PreferencesManager.getToken() ?: return retrofit2.Response.error(
            401,
            okhttp3.ResponseBody.create(null, "Unauthorized")
        )
        return ApiClient.apiService.unsaveNews("Bearer $token", newsId)
    }

    fun clearCache() {
        // Очищаем кэш в памяти и базе
        // Можно реализовать очистку Room
    }
}

// Extension functions для конвертации
fun News.toEntity(): NewsEntity {
    return NewsEntity(
        id = id,
        title = title,
        excerpt = excerpt,
        content = content,
        date = date,
        formattedDate = formattedDate,
        thumbnail = thumbnail,
        authorId = authorId,
        authorName = authorName,
        categories = categories?.joinToString(","),
        link = link,
        images = images?.joinToString(","),
        videos = videos?.joinToString(","),
        shortDescription = shortDescription,
        importance = importance,
        categoryId = categoryId,
        categoryName = categoryName,
        sendPush = sendPush,
        viewsCount = viewsCount,
        likesCount = likesCount,
        commentsCount = commentsCount,
        authorAvatar = authorAvatar
    )
}

fun NewsEntity.toModel(): News {
    return News(
        id = id,
        title = title,
        excerpt = excerpt,
        content = content,
        date = date,
        formattedDate = formattedDate,
        thumbnail = thumbnail,
        authorId = authorId,
        authorName = authorName,
        categories = categories?.split(",")?.filter { it.isNotEmpty() },
        link = link,
        images = images?.split(",")?.filter { it.isNotEmpty() },
        videos = videos?.split(",")?.filter { it.isNotEmpty() },
        shortDescription = shortDescription,
        importance = importance,
        categoryId = categoryId,
        categoryName = categoryName,
        sendPush = sendPush,
        viewsCount = viewsCount,
        likesCount = likesCount ?: 0,
        commentsCount = commentsCount ?: 0,
        authorAvatar = authorAvatar
    )
}