package com.spotify.playlistmanager.desktop.data.auth

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Endpoint tokenów Spotify Accounts (accounts.spotify.com/api/token).
 * Klient publiczny PKCE — bez client_secret.
 */
interface SpotifyAuthApi {

    @FormUrlEncoded
    @POST("api/token")
    fun exchangeCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String,
    ): Call<SpotifyTokenResponse>

    @FormUrlEncoded
    @POST("api/token")
    fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
    ): Call<SpotifyTokenResponse>
}

data class SpotifyTokenResponse(
    val access_token: String,
    val token_type: String? = null,
    val expires_in: Int = 3600,
    val refresh_token: String? = null,
    val scope: String? = null,
)
