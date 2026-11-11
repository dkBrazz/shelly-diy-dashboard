package org.dkbrazz.service

import org.dkbrazz.config.AuthProperties
import org.dkbrazz.model.entity.User
import org.dkbrazz.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class UserAllowlistSeederTest {
    private val userRepository = mock(UserRepository::class.java)

    @Test
    fun `adds only emails that are not yet allowlisted`() {
        val properties = AuthProperties().apply { allowedEmails = listOf("new@example.com", "Known@Example.com") }
        `when`(userRepository.findByEmailIgnoreCase("known@example.com")).thenReturn(User(email = "known@example.com"))
        `when`(userRepository.count()).thenReturn(2L)

        val added = UserAllowlistSeeder(userRepository, properties).seed()

        val captor = ArgumentCaptor.forClass(User::class.java)
        verify(userRepository, times(1)).save(captor.capture())
        assertEquals("new@example.com", captor.value.email)
        assertEquals(listOf("new@example.com"), added)
    }

    @Test
    fun `does nothing when no emails are configured`() {
        `when`(userRepository.count()).thenReturn(0L)

        val added = UserAllowlistSeeder(userRepository, AuthProperties()).seed()

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any())
        assertEquals(emptyList<String>(), added)
    }

    @Test
    fun `binds a comma separated environment value and normalizes it`() {
        val source = MapConfigurationPropertySource(
            mapOf("app.auth.allowed-emails" to " Alice@Example.com, bob@example.com ,,alice@example.com ")
        )

        val properties = Binder(source).bind("app.auth", Bindable.of(AuthProperties::class.java)).get()

        assertEquals(listOf("alice@example.com", "bob@example.com"), properties.allowedEmails)
    }

    @Test
    fun `binds an empty environment value to an empty list`() {
        val source = MapConfigurationPropertySource(mapOf("app.auth.allowed-emails" to ""))

        val properties = Binder(source).bind("app.auth", Bindable.of(AuthProperties::class.java)).get()

        assertEquals(emptyList<String>(), properties.allowedEmails)
    }
}
