package com.fitnesslemon.app.data.api

import android.content.Context
import com.fitnesslemon.app.utils.Constants
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

object ApiClient {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    // ✅ ИСПРАВЛЕНО: увеличены таймауты для больших файлов (видео)
    private val httpClient: OkHttpClient by lazy {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("ApiClient.init() must be called before using ApiClient")
        }
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor(appContext))
            .cookieJar(JavaNetCookieJar(cookieManager))
            .connectTimeout(120, TimeUnit.SECONDS)   // ✅ 2 минуты
            .readTimeout(300, TimeUnit.SECONDS)      // ✅ 5 минут
            .writeTimeout(300, TimeUnit.SECONDS)     // ✅ 5 минут
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
    val adminApiService: AdminApiService by lazy { retrofit.create(AdminApiService::class.java) }
}