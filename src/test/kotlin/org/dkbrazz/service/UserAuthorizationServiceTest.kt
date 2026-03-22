package org.dkbrazz.service

import org.dkbrazz.model.entity.User
import org.dkbrazz.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.security.oauth2.core.OAuth2AuthenticationException

class UserAuthorizationServiceTest {
    private val userRepository = mock(UserRepository::class.java)
    private val userAuthService = UserAuthorizationService(userRepository)

    @Test
    fun `verifyEmail should pass when email exists in database`() {
        val email = "allowed@example.com"
        `when`(userRepository.findByEmail(email)).thenReturn(User(email = email))
        
        userAuthService.verifyEmail(email)
    }

    @Test
    fun `verifyEmail should throw exception when email is null`() {
        assertThrows<OAuth2AuthenticationException> {
            userAuthService.verifyEmail(null)
        }
    }

    @Test
    fun `verifyEmail should throw exception when email is not in database`() {
        val email = "notallowed@example.com"
        `when`(userRepository.findByEmail(email)).thenReturn(null)
        
        assertThrows<OAuth2AuthenticationException> {
            userAuthService.verifyEmail(email)
        }
    }
}
