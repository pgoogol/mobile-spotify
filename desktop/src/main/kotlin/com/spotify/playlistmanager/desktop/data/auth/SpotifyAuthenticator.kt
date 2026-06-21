package com.spotify.playlistmanager.desktop.data.auth

import com.spotify.playlistmanager.desktop.config.AppConfig
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Logowanie do Spotify w stylu desktopowym: Authorization Code + PKCE z
 * redirectem na loopback `http://127.0.0.1:8888/callback`.
 *
 * Przebieg [login]:
 *  1. generuje PKCE (verifier/challenge) + state,
 *  2. uruchamia lokalny serwer HTTP na porcie [AppConfig.REDIRECT_PORT],
 *  3. otwiera przeglądarkę na `/authorize`,
 *  4. czeka na redirect z `code`, weryfikuje `state`,
 *  5. wymienia `code` na tokeny i zapisuje je w [TokenStore].
 */
class SpotifyAuthenticator(
    private val authApi: SpotifyAuthApi,
    private val tokenStore: TokenStore,
    private val clientId: String,
) {

    suspend fun login(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val verifier = PkceGenerator.generateCodeVerifier()
            val challenge = PkceGenerator.deriveCodeChallenge(verifier)
            val state = PkceGenerator.generateState()

            val callback = CompletableDeferred<Map<String, String>>()
            val server = HttpServer.create(
                InetSocketAddress("127.0.0.1", AppConfig.REDIRECT_PORT),
                0,
            )
            server.createContext("/callback") { exchange ->
                val params = parseQuery(exchange.requestURI.rawQuery)
                val body = """
                    <html><head><meta charset="utf-8"></head>
                    <body style="font-family:sans-serif;background:#121212;color:#eee;
                                 display:flex;align-items:center;justify-content:center;height:100vh">
                      <div style="text-align:center">
                        <h2 style="color:#1DB954">Zalogowano ✓</h2>
                        <p>Możesz wrócić do aplikacji Spotify Playlist Manager.</p>
                      </div>
                    </body></html>
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
                if (!callback.isCompleted) callback.complete(params)
            }
            server.start()

            try {
                openBrowser(buildAuthorizeUrl(challenge, state))
                // 5 minut na zalogowanie w przeglądarce.
                val params = withTimeout(5 * 60_000L) { callback.await() }

                params["error"]?.let { error("Autoryzacja odrzucona: $it") }
                val code = params["code"] ?: error("Brak kodu autoryzacji w odpowiedzi.")
                if (params["state"] != state) error("Niezgodny parametr state — przerwano (możliwy CSRF).")

                val resp = authApi.exchangeCode(
                    code = code,
                    redirectUri = AppConfig.REDIRECT_URI,
                    clientId = clientId,
                    codeVerifier = verifier,
                ).execute()
                val tokenBody = resp.body()
                if (!resp.isSuccessful || tokenBody == null) {
                    val detail = runCatching { resp.errorBody()?.string() }.getOrNull().orEmpty()
                    error("Wymiana kodu nie powiodła się (HTTP ${resp.code()}). $detail".trim())
                }
                tokenStore.saveTokens(
                    access = tokenBody.access_token,
                    refresh = tokenBody.refresh_token,
                    expiresInSec = tokenBody.expires_in,
                )
            } finally {
                server.stop(0)
            }
            Unit
        }
    }

    private fun buildAuthorizeUrl(codeChallenge: String, state: String): String {
        val q = linkedMapOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to AppConfig.REDIRECT_URI,
            "code_challenge_method" to "S256",
            "code_challenge" to codeChallenge,
            "scope" to AppConfig.scopes.joinToString(" "),
            "state" to state,
        )
        val query = q.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }
        return "${AppConfig.AUTHORIZE_ENDPOINT}?$query"
    }

    private fun openBrowser(url: String) {
        // Preferuj java.awt.Desktop; fallback na natywne polecenia systemowe.
        if (Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        ) {
            runCatching { Desktop.getDesktop().browse(URI(url)); return }
        }
        val os = System.getProperty("os.name").lowercase()
        val cmd = when {
            os.contains("win") -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
            os.contains("mac") -> arrayOf("open", url)
            else -> arrayOf("xdg-open", url)
        }
        runCatching { ProcessBuilder(*cmd).start() }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
            key to value
        }.toMap()
    }
}
