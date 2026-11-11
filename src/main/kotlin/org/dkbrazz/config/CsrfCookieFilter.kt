package org.dkbrazz.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Forces the deferred [CsrfToken] to load on every request so that `CookieCsrfTokenRepository`
 * writes the `XSRF-TOKEN` cookie on plain GET responses too. Without this the cookie would only be
 * set once something reads the token, and the SPA would have nothing to echo back in
 * `X-XSRF-TOKEN` on its first state-changing request (the logout POST).
 *
 * Registered by [SecurityConfig] inside the security filter chain; it must not also be a servlet
 * `@Component` filter or it would run twice.
 */
class CsrfCookieFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
        filterChain.doFilter(request, response)
    }
}
