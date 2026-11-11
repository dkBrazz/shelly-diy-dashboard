package org.dkbrazz.service

import org.dkbrazz.config.ShellyProperties
import org.dkbrazz.util.describeSafely
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/** Claims this application reads from Shelly's JWTs; both the authorization code and the access token carry them. */
data class ShellyTokenClaims(
    /** Expiry as epoch milliseconds, or null when the token has no `exp` claim. */
    val expiresAtMillis: Long?,
    /** Designated API server of the authorizing user, e.g. `https://shelly-241-eu.shelly.cloud`. */
    val userApiUrl: String?
)

@Service
class ShellyAuthService(
    private val props: ShellyProperties,
    private val objectMapper: ObjectMapper,
    private val restClient: RestClient
) {
    private val logger = LoggerFactory.getLogger(ShellyAuthService::class.java)
    private val tokenCache = AtomicReference<AccessTokenInfo?>()

    /**
     * Base URL of the account's designated Shelly server. Shelly requires HTTP calls to address the host from the
     * `user_api_url` claim of the authorization code; `SHELLY_REGION` only remains as a deprecated override.
     */
    val apiUrl: String = resolveApiUrl()

    /** Region label of [apiUrl]. The account's own devices only emit events on this region's WebSocket. */
    val accountRegion: String = ShellyProperties.regionOf(
        URI(apiUrl).host ?: throw IllegalStateException("Shelly API URL has no host: $apiUrl")
    )

    init {
        logger.info("Shelly Cloud API server: $apiUrl (region $accountRegion)")
    }

    fun getAccessToken(): String {
        val currentToken = tokenCache.get()
        if (currentToken != null && !isExpired(currentToken)) {
            return currentToken.token
        }

        return refreshToken()
    }

    @Synchronized
    private fun refreshToken(): String {
        // Double check after lock
        val currentToken = tokenCache.get()
        if (currentToken != null && !isExpired(currentToken)) {
            return currentToken.token
        }

        logger.info("Refreshing Shelly Cloud access token using auth code")
        try {
            // URI variables are strictly percent-encoded, as the Shelly docs require for the code.
            val response = restClient.get()
                .uri(
                    "$apiUrl/oauth/auth?client_id={clientId}&grant_type=code&code={code}",
                    props.cloud.clientId,
                    props.cloud.authCode
                )
                .retrieve()
                .body(Map::class.java)

            val accessToken = response?.get("access_token") as? String
                ?: throw IllegalStateException("Access token missing in response (keys: ${response?.keys})")

            val exp = decodeExp(accessToken)
            tokenCache.set(AccessTokenInfo(accessToken, exp))
            logger.info("New access token retrieved, expires at: ${Date(exp)}")
            return accessToken
        } catch (e: Exception) {
            // Spring's client exceptions embed the request URL, which carries the auth code.
            // Re-throw a sanitized exception without the cause so no stack trace can leak it.
            val reason = "${e.javaClass.simpleName}: ${describeSafely(e)}"
            logger.error("Failed to refresh Shelly access token: $reason")
            throw IllegalStateException("Failed to refresh Shelly access token: $reason")
        }
    }

    private fun isExpired(info: AccessTokenInfo): Boolean {
        // Expire 1 minute before actual expiration to be safe
        return System.currentTimeMillis() > (info.exp - 60000)
    }

    private fun resolveApiUrl(): String {
        val override = props.cloud.region ?: return resolveUserApiUrl(props.cloud.authCode)
        logger.warn(
            "SHELLY_REGION is deprecated: the Shelly server is derived from the authorization code's " +
                "user_api_url claim. Remove the variable unless the derived server is wrong."
        )
        return props.apiUrl(override)
    }

    /**
     * The authorization code is itself a JWT. Exceptions deliberately carry neither the code nor the
     * parser's message, because the latter may quote the decoded payload.
     */
    private fun resolveUserApiUrl(authCode: String): String {
        val claims = try {
            decodeClaims(authCode)
        } catch (e: Exception) {
            throw IllegalStateException(
                "SHELLY_AUTH_CODE is not a decodable Shelly JWT (${e.javaClass.simpleName}); " +
                    "obtain a fresh authorization code or set SHELLY_REGION to your Shelly server"
            )
        }
        return (claims.userApiUrl ?: throw IllegalStateException(
            "SHELLY_AUTH_CODE carries no user_api_url claim; " +
                "set SHELLY_REGION to the server shown next to your authorization key in the Shelly app"
        )).trimEnd('/')
    }

    private fun decodeExp(token: String): Long {
        try {
            return decodeClaims(token).expiresAtMillis
                ?: throw IllegalArgumentException("Access token has no exp claim")
        } catch (e: Exception) {
            logger.error("Failed to decode JWT expiration: ${describeSafely(e)}")
            // Fallback to a short life if we can't parse it
            return System.currentTimeMillis() + 3600000
        }
    }

    private fun decodeClaims(token: String): ShellyTokenClaims {
        val parts = token.split(".")
        require(parts.size == 3) { "Invalid JWT format" }
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        val node = objectMapper.readTree(payload)
        return ShellyTokenClaims(
            expiresAtMillis = node.get("exp")?.takeIf { it.isNumber }?.asLong()?.times(1000),
            userApiUrl = node.get("user_api_url")?.takeIf { it.isString }?.stringValue()?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private data class AccessTokenInfo(val token: String, val exp: Long)
}
