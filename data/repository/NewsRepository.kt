package com.fitnesslemon.app.data.repository

import android.content.Context
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.ApiResponse
import com.fitnesslemon.app.data.api.NewsData
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
    private val newsDao by lazy { NewsDatabase.getInstance(context).newsDao() }

    suspend fun getNews(perPage: Int = 20, page: Int = 1): Response<NewsResponse> {
        return try {
            val response = ApiClient.apiService.getNews(perPage, page)
            if (response.isSuccessful) {
                response.body()?.data?.news?.let { news ->
                    withContext(Dispatchers.IO) {
                        newsDao.insertAll(news.map(News::toEntity))
                    }
                }
                response
            } else {
                cachedResponseOr(response)
            }
        } catch (error: Exception) {
            cachedResponseOr(null) ?: throw error
        }
    }

    private suspend fun cachedResponseOr(failedResponse: Response<NewsResponse>?): Response<NewsResponse> {
        val cached = getCachedNews()
        if (cached.isNotEmpty()) {
            return Response.success(
                NewsResponse(
                    success = true,
                    message = if (failedResponse == null) "Loaded from local cache" else "Showing cached data",
                    data = NewsData(cached, cached.size, 1, cached.size, 1)
                )
            )
        }
        return failedResponse ?: Response.error(503, okhttp3.ResponseBody.create(null, "No cached data"))
    }

    suspend fun getCachedNews(): List<News> = withContext(Dispatchers.IO) {
        newsDao.getAll().map(NewsEntity::toModel)
    }

    fun getNewsFlow(): Flow<List<News>> = newsDao.getAllFlow().map { list -> list.map(NewsEntity::toModel) }

    suspend fun clearCache() = withContext(Dispatchers.IO) { newsDao.clearAll() }

    suspend fun likeNews(newsId: Int): Response<ApiResponse> = authorized { ApiClient.apiService.likeNews(it, newsId) }
    suspend fun unlikeNews(newsId: Int): Response<ApiResponse> = authorized { ApiClient.apiService.unlikeNews(it, newsId) }
    suspend fun saveNews(newsId: Int): Response<ApiResponse> = authorized { ApiClient.apiService.saveNews(it, newsId) }
    suspend fun unsaveNews(newsId: Int): Response<ApiResponse> = authorized { ApiClient.apiService.unsaveNews(it, newsId) }

    private suspend fun authorized(call: suspend (String) -> Response<ApiResponse>): Response<ApiResponse> {
        val token = PreferencesManager.getToken() ?: return Response.error(401, okhttp3.ResponseBody.create(null, "Unauthorized"))
        return call("Bearer $token")
    }
}

fun News.toEntity() = NewsEntity(
    id, title, excerpt, content, date, formattedDate, thumbnail, authorId, authorName,
    categories?.joinToString(","), link, images?.joinToString(","), videos?.joinToString(","),
    shortDescription, importance, categoryId, categoryName, sendPush, viewsCount,
    likesCount, commentsCount, authorAvatar
)

fun NewsEntity.toModel() = News(
    id, title, excerpt, content, date, formattedDate, thumbnail, authorId, authorName,
    categories?.split(",")?.filter(String::isNotEmpty), link,
    images?.split(",")?.filter(String::isNotEmpty), videos?.split(",")?.filter(String::isNotEmpty),
    shortDescription, importance, categoryId, categoryName, sendPush, viewsCount,
    likesCount ?: 0, commentsCount ?: 0, authorAvatar
)
