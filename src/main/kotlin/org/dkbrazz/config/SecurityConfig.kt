package org.dkbrazz.config

import org.dkbrazz.service.UserAuthorizationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/", "/index.html", "/static/**", "/assets/**", "/vite.svg").permitAll()
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
