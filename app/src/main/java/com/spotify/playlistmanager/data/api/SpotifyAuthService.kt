package com.spotify.playlistmanager.data.api

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Endpoint tokenów Spotify Accounts (https://accounts.spotify.com/api/token).
 *
 * Uwaga: to inny host niż Web API (api.spotify.com), dlatego serwis korzysta
 * z osobnego Retrofit/OkHttp BEZ [AuthInterceptor] i BEZ [TokenAuthenticator]
 * (patrz AppModule, qualifier @Named("auth")) — w przeciwnym razie odświeżanie
 * tokena wpadłoby w rekurencję.
 *
 * PKCE: klient publiczny (aplikacja mobilna) nie wysyła client_secret —
 * tożsamość potwierdza code_verifier przy wymianie kodu na token.
 */
interface SpotifyAuthService {

    /** Wymiana authorization code (z PKCE) na access_token + refresh_token. */
    @FormUrlEncoded
    @POST("api/token")
    fun exchangeCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String
    ): Call<SpotifyTokenResponse>

    /** Odświeżenie access_token przy użyciu refresh_token (sesja bez ponownego logowania). */
    @FormUrlEncoded
    @POST("api/token")
    fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): Call<SpotifyTokenResponse>
}

/**
 * Odpowiedź endpointu tokenów. Nazwy pól = klucze JSON (Gson IDENTITY).
 *
 * `refresh_token` bywa nieobecny przy odświeżaniu (Spotify nie zawsze rotuje
 * refresh token) — wtedy zachowujemy poprzedni.
 */
data class SpotifyTokenResponse(
    val access_token: String,
    val token_type: String? = null,
    val expires_in: Int = 3600,
    val refresh_token: String? = null,
    val scope: String? = null
)
