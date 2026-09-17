package com.fitnesslemon.app.utils

import com.fitnesslemon.app.data.api.ApiErrorHandler
import com.fitnesslemon.app.data.api.ApiResult
import retrofit2.Response

object ApiResultExt {
    fun <T> Response<T>.toApiResult(): ApiResult<T> = ApiErrorHandler.fromResponse(this)
}
