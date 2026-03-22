package com.spotify.playlistmanager.data.api

import com.spotify.playlistmanager.util.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp Interceptor – wstrzykuje nagłówek Authorization: Bearer <token>
 * do każdego żądania Retrofit.
 */
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getAccessToken()
        val request = chain.request().newBuilder().apply {
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
        }.build()
        return chain.proceed(request)
    }
}
