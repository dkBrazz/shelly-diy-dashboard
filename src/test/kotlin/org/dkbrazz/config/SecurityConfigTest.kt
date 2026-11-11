package org.dkbrazz.config

import org.dkbrazz.service.UserAuthorizationService
import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.RestController

/**
 * Runs the security filter chain against the SPA fallback of [WebConfig] without the rest of the
 * application (database, Shelly Cloud, actuator). Controllers are excluded because authorization is
 * decided before any handler runs; `src/test/resources/static/index.html` stands in for the built
 * frontend so the fallback has something to serve.
 */
@WebMvcTest(
    excludeFilters = [ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [RestController::class])],
    properties = [
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
    ]
)
@Import(SecurityConfig::class, WebConfig::class)
class SecurityConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userAuthorizationService: UserAuthorizationService

    /** What an anonymous browser gets for a protected page: a redirect into the Google login flow. */
    private val redirectedToGoogleLogin = ResultMatcher { result ->
        status().is3xxRedirection.match(result)
        header().string(HttpHeaders.LOCATION, endsWith("/oauth2/authorization/google")).match(result)
    }

    @Test
    fun `serves SPA deep links to anonymous browsers`() {
        mockMvc.perform(get("/device/3").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
    }

    @Test
    fun `keeps the API authenticated`() {
        mockMvc.perform(get("/api/devices"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `keeps the public actuator endpoints reachable`() {
        // Actuator is not part of the slice, so the SPA fallback answers; a forbidden path would
        // redirect to Google instead, which is what this guards against.
        mockMvc.perform(get("/actuator/health").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk)
    }

    @Test
    fun `sends anonymous browsers on other actuator endpoints to Google`() {
        mockMvc.perform(get("/actuator/env").accept(MediaType.TEXT_HTML))
            .andExpect(redirectedToGoogleLogin)
    }

    @Test
    fun `only permits GET deep links`() {
        // Carries a valid CSRF token so the request gets past CsrfFilter and the authorization
        // rule, not CSRF, is what turns it away.
        val token = obtainCsrfCookie()

        mockMvc.perform(post("/device/3").accept(MediaType.TEXT_HTML).cookie(token).header(CSRF_HEADER, token.value))
            .andExpect(redirectedToGoogleLogin)
    }

    @Test
    fun `keeps unknown dotted paths authenticated`() {
        mockMvc.perform(get("/backup.sql").accept(MediaType.TEXT_HTML))
            .andExpect(redirectedToGoogleLogin)
    }

    /** The cookie a browser receives on first contact, replayed exactly as the SPA would. */
    private fun obtainCsrfCookie() =
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getCookie(CSRF_COOKIE)
            ?: error("GET / did not set the $CSRF_COOKIE cookie")

    @Nested
    inner class Csrf {
        @Test
        fun `writes a readable XSRF-TOKEN cookie on GET responses`() {
            mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk)
                .andExpect(cookie().exists(CSRF_COOKIE))
                .andExpect(cookie().httpOnly(CSRF_COOKIE, false))
        }

        @Test
        fun `rejects POST logout without a CSRF token`() {
            mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `rejects POST logout whose header does not match the cookie`() {
            val token = obtainCsrfCookie()

            mockMvc.perform(post("/logout").cookie(token).header(CSRF_HEADER, "not-the-cookie-value"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `logs out on POST logout with a matching CSRF token and redirects home`() {
            val token = obtainCsrfCookie()

            mockMvc.perform(post("/logout").cookie(token).header(CSRF_HEADER, token.value))
                .andExpect(status().is3xxRedirection)
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/")))
        }

        @Test
        fun `no longer logs out on GET logout`() {
            // Spring Security only maps POST /logout once CSRF is enabled, so GET falls through to
            // the SPA deep-link rule and is answered by the fallback instead of ending the session.
            mockMvc.perform(get("/logout").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        }

        @Test
        fun `rejects unsafe API requests without a CSRF token before authentication`() {
            mockMvc.perform(post("/api/devices"))
                .andExpect(status().isForbidden)
        }
    }

    private companion object {
        const val CSRF_COOKIE = "XSRF-TOKEN"
        const val CSRF_HEADER = "X-XSRF-TOKEN"
    }
}
