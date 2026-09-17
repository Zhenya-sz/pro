package com.fitnesslemon.app.data.api

import retrofit2.Response

sealed class ApiResult<out T> {
    data class Loading<out T>(val data: T? = null) : ApiResult<T>()
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error<out T>(val error: AppError, val data: T? = null) : ApiResult<T>()
}

data class AppError(
    val code: Int? = null,
    val message: String,
    val cause: Throwable? = null
) : Throwable(message, cause)

object ApiErrorHandler {
    fun <T> fromResponse(response: Response<T>): ApiResult<T> {
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) return ApiResult.Success(body)
            return ApiResult.Error(AppError(response.code(), "Empty response"))
        }

        val message = when (response.code()) {
            401 -> "Session expired or unauthorized"
            403 -> "Forbidden"
            404 -> "Endpoint not found"
            429 -> "Too many requests"
            in 500..599 -> "Server error"
            else -> "Request failed"
        }
        return ApiResult.Error(AppError(response.code(), message))
    }

    fun fromThrowable(t: Throwable): AppError {
        return AppError(
            message = when {
                t is java.io.IOException -> "Network error"
                t is java.net.SocketTimeoutException -> "Request timed out"
                else -> t.message ?: "Unknown error"
            },
            cause = t
        )
    }
}
