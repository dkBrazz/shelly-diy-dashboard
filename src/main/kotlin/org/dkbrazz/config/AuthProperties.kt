package org.dkbrazz.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth")
class AuthProperties {
    /** Google account emails seeded into the `users` allowlist on every startup. */
    var allowedEmails: List<String> = emptyList()
        set(value) {
            field = value.map { it.trim().lowercase() }.filter(String::isNotEmpty).distinct()
        }
}
