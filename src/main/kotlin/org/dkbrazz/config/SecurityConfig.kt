package org.dkbrazz.config

import jakarta.servlet.http.HttpServletRequest
import org.dkbrazz.service.UserAuthorizationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.http.HttpStatus
import org.springframework.security.web.util.matcher.RequestMatcher

@Configuration
@EnableWebSecurity
class SecurityConfig(private val userAuthService: UserAuthorizationService) {

    /**
     * Browser navigations to SPA routes such as `/device/3`, which [WebConfig] answers with
     * `index.html`: GET requests whose path contains no dot. Static assets are dotted and have
     * their own rules, and the API and actuator prefixes are matched before this so they stay
     * authenticated. `PathPatternRequestMatcher` cannot express "no dot in any segment" because
     * a double wildcard is only allowed at the end of a pattern, hence the hand-written matcher.
     */
    private val spaDeepLink = RequestMatcher { request ->
        HttpMethod.GET.matches(request.method) && '.' !in pathWithinApplication(request)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/", "/index.html", "/static/**", "/assets/**", "/favicon.svg").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/api/**", "/actuator/**").authenticated()
                    .requestMatchers(spaDeepLink).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .defaultAuthenticationEntryPointFor(
                        HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        RequestMatcher { it.requestURI.startsWith("/api/") }
                    )
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .defaultSuccessUrl("/", true)
                    .userInfoEndpoint { userInfo ->
                        userInfo.oidcUserService(oidcUserService())
                        userInfo.userService(oauth2UserService())
                    }
            }
            .logout { logout ->
                logout
                    .logoutSuccessUrl("/")
                    .permitAll()
            }
            .csrf { csrf -> csrf.disable() }
            
        return http.build()
    }

    private fun pathWithinApplication(request: HttpServletRequest): String =
        request.requestURI.removePrefix(request.contextPath)

    @Bean
    fun oidcUserService(): OAuth2UserService<OidcUserRequest, OidcUser> {
        val delegate = OidcUserService()
        return OAuth2UserService { userRequest ->
            val oidcUser = delegate.loadUser(userRequest)
            userAuthService.verifyEmail(oidcUser.email)
            oidcUser
        }
    }

    @Bean
    fun oauth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> {
        val delegate = DefaultOAuth2UserService()
        return OAuth2UserService { userRequest ->
            val oauth2User = delegate.loadUser(userRequest)
            userAuthService.verifyEmail(oauth2User.attributes["email"] as String?)
            oauth2User
        }
    }
}
