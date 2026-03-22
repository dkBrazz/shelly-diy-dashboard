package org.dkbrazz.service

import org.dkbrazz.repository.UserRepository
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.stereotype.Service

@Service
class UserAuthorizationService(private val userRepository: UserRepository) {
    fun verifyEmail(email: String?) {
        if (email == null || userRepository.findByEmail(email) == null) {
            throw OAuth2AuthenticationException(OAuth2Error("invalid_user"), "User with email $email is not allowed")
        }
    }
}
