package org.dkbrazz.service

import org.dkbrazz.config.ShellyProperties
import org.dkbrazz.util.describeSafely
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@Service
class ShellyAuthService(
    private val props: ShellyProperties,
    private val objectMapper: ObjectMapper,
    private val restClient: RestClient
) {
    private val logger = LoggerFactory.getLogger(ShellyAuthService::class.java)
    private val tokenCache = AtomicReference<AccessTokenInfo?>()

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
                    "${props.apiUrl}/oauth/auth?client_id={clientId}&grant_type=code&code={code}",
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

    private fun decodeExp(token: String): Long {
        try {
            val parts = token.split(".")
            if (parts.size != 3) throw IllegalArgumentException("Invalid JWT format")
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            val node = objectMapper.readTree(payload)
            return node["exp"].asLong() * 1000
        } catch (e: Exception) {
            logger.error("Failed to decode JWT expiration: ${e.message}")
            // Fallback to a short life if we can't parse it
            return System.currentTimeMillis() + 3600000
        }
    }

    private data class AccessTokenInfo(val token: String, val exp: Long)
}
