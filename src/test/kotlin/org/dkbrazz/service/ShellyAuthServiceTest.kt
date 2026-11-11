package org.dkbrazz.service

import org.dkbrazz.config.ShellyProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.util.Base64

@ExtendWith(OutputCaptureExtension::class)
class ShellyAuthServiceTest {
    // The code is a JWT; its (opaque) signature carries characters that must be strictly percent-encoded.
    private val authCode = jwt(exp = 0, userApiUrl = "https://shelly-243-eu.shelly.cloud", signature = "secret+auth/code==")
    private val encodedAuthCode = authCode.substringBeforeLast('.') + ".secret%2Bauth%2Fcode%3D%3D"
    private val expectedUrl =
        "https://shelly-243-eu.shelly.cloud/oauth/auth?client_id=shelly-diy&grant_type=code&code=$encodedAuthCode"

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val service = service()

    private fun service(configure: ShellyProperties.() -> Unit = {}) = ShellyAuthService(
        ShellyProperties().apply { cloud.authCode = authCode }.apply(configure),
        JsonMapper.builder().build(),
        builder.build()
    )

    @Test
    fun `derives the token exchange server and account region from the auth code`() {
        assertEquals("https://shelly-243-eu.shelly.cloud", service.apiUrl)
        assertEquals("shelly-243-eu", service.accountRegion)
    }

    @Test
    fun `SHELLY_REGION override replaces the derived server and logs a deprecation warning`(output: CapturedOutput) {
        val overridden = service { cloud.region = "shelly-override-eu" }
        val token = jwt(exp = System.currentTimeMillis() / 1000 + 3600)
        server.expect(requestTo("https://shelly-override-eu.shelly.cloud/oauth/auth?client_id=shelly-diy&grant_type=code&code=$encodedAuthCode"))
            .andRespond(withSuccess("""{"access_token":"$token"}""", MediaType.APPLICATION_JSON))

        assertEquals(token, overridden.getAccessToken())
        assertEquals("shelly-override-eu", overridden.accountRegion)
        assertTrue(output.all.contains("SHELLY_REGION is deprecated"))
        server.verify()
    }

    @Test
    fun `auth code without user_api_url fails fast without echoing the code`() {
        val code = jwt(exp = 0, userApiUrl = null, signature = "secret+auth/code==")

        val error = assertThrows(IllegalStateException::class.java) {
            service { cloud.authCode = code }
        }

        assertTrue(error.message!!.contains("user_api_url"))
        assertTrue(error.message!!.contains("SHELLY_REGION"))
        assertFalse(error.message!!.contains(code))
        assertFalse(error.message!!.contains(code.substringAfterLast('.')))
    }

    @Test
    fun `auth code that is not a JWT fails fast without echoing the code`() {
        val code = "secret+auth/code=="

        val error = assertThrows(IllegalStateException::class.java) {
            service { cloud.authCode = code }
        }

        assertNull(error.cause)
        assertTrue(error.message!!.contains("not a decodable Shelly JWT"))
        assertFalse(error.message!!.contains(code))
    }

    @Test
    fun `exchanges a strictly encoded auth code and caches the token until expiry`() {
        val token = jwt(exp = System.currentTimeMillis() / 1000 + 3600)
        server.expect(requestTo(expectedUrl))
            .andRespond(withSuccess("""{"access_token":"$token"}""", MediaType.APPLICATION_JSON))

        assertEquals(token, service.getAccessToken())
        assertEquals(token, service.getAccessToken())
        server.verify()
    }

    @Test
    fun `refreshes a token that is about to expire`() {
        val expiring = jwt(exp = System.currentTimeMillis() / 1000 + 30)
        val fresh = jwt(exp = System.currentTimeMillis() / 1000 + 3600)
        server.expect(requestTo(expectedUrl))
            .andRespond(withSuccess("""{"access_token":"$expiring"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo(expectedUrl))
            .andRespond(withSuccess("""{"access_token":"$fresh"}""", MediaType.APPLICATION_JSON))

        assertEquals(expiring, service.getAccessToken())
        assertEquals(fresh, service.getAccessToken())
        server.verify()
    }

    @Test
    fun `rejected exchange never exposes the auth code in logs or exceptions`(output: CapturedOutput) {
        server.expect(requestTo(expectedUrl))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""{"isok":false}"""))

        val error = assertThrows(IllegalStateException::class.java) { service.getAccessToken() }

        assertNull(error.cause)
        assertTrue(error.message!!.contains("Failed to refresh Shelly access token"))
        assertFalse(error.message!!.contains(authCode))
        assertFalse(error.message!!.contains("secret%2Bauth"))
        assertFalse(output.all.contains(authCode))
        assertFalse(output.all.contains("secret%2Bauth"))
        assertTrue(output.all.contains("Failed to refresh Shelly access token"))
    }

    @Test
    fun `missing access token in response is reported without echoing the body`(output: CapturedOutput) {
        server.expect(requestTo(expectedUrl))
            .andRespond(withSuccess("""{"isok":false,"errors":["$authCode"]}""", MediaType.APPLICATION_JSON))

        assertThrows(IllegalStateException::class.java) { service.getAccessToken() }

        assertFalse(output.all.contains(authCode))
    }

    private fun jwt(
        exp: Long,
        userApiUrl: String? = "https://shelly-241-eu.shelly.cloud",
        signature: String = "signature"
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val claims = buildString {
            append("""{"exp":$exp""")
            if (userApiUrl != null) append(""","user_api_url":"$userApiUrl"""")
            append("}")
        }
        val payload = encoder.encodeToString(claims.toByteArray())
        return "$header.$payload.$signature"
    }
}
