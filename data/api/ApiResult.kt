package com.fitnesslemon.app.data.api

import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.Response

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

data class ApiError(
    val kind: Kind,
    val statusCode: Int? = null,
    val cause: Throwable? = null
) {
    enum class Kind { Unauthorized, Forbidden, NotFound, RateLimited, Server, Network, Timeout, Empty, Unknown }
}

object ApiResults {
    fun <T> Response<T>.toResult(): ApiResult<T> {
        if (isSuccessful) return body()?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(ApiError(ApiError.Kind.Empty, code()))
        val kind = when (code()) {
            401 -> ApiError.Kind.Unauthorized
            403 -> ApiError.Kind.Forbidden
            404 -> ApiError.Kind.NotFound
            429 -> ApiError.Kind.RateLimited
            in 500..599 -> ApiError.Kind.Server
            else -> ApiError.Kind.Unknown
        }
        return ApiResult.Failure(ApiError(kind, code()))
    }

    fun fromThrowable(error: Throwable): ApiError = when (error) {
        is SocketTimeoutException -> ApiError(ApiError.Kind.Timeout, cause = error)
        is IOException -> ApiError(ApiError.Kind.Network, cause = error)
        else -> ApiError(ApiError.Kind.Unknown, cause = error)
    }
}
