package org.dkbrazz.service

import org.dkbrazz.config.AuthProperties
import org.dkbrazz.model.entity.User
import org.dkbrazz.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Seeds the `users` allowlist from `APP_ALLOWED_EMAILS` so a fresh installation
 * can be logged into without running SQL by hand. Existing rows are never removed.
 */
@Component
class UserAllowlistSeeder(
    private val userRepository: UserRepository,
    private val authProperties: AuthProperties
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(UserAllowlistSeeder::class.java)

    override fun run(args: ApplicationArguments) {
        seed()
    }

    fun seed(): List<String> {
        val added = authProperties.allowedEmails
            .filter { userRepository.findByEmailIgnoreCase(it) == null }
            .onEach { userRepository.save(User(email = it)) }

        if (added.isNotEmpty()) {
            logger.info("Added ${added.size} email(s) from APP_ALLOWED_EMAILS to the user allowlist")
        }
        if (userRepository.count() == 0L) {
            logger.warn("The user allowlist is empty: nobody can log in. Set APP_ALLOWED_EMAILS or insert into the users table.")
        }
        return added
    }
}
