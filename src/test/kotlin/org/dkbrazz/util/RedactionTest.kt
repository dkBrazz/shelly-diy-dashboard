package org.dkbrazz.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException

class RedactionTest {
    @Test
    fun `redacts auth code and websocket token query parameters`() {
        val text = "I/O error on GET request for \"https://x.shelly.cloud/oauth/auth?client_id=shelly-diy&grant_type=code&code=abc.def-ghi\"" +
            " and wss://x.shelly.cloud:6113/shelly/wss/hk_sock?t=eyJhbGciOi.payload.sig closed"

        assertEquals(
            "I/O error on GET request for \"https://x.shelly.cloud/oauth/auth?client_id=shelly-diy&grant_type=code&code=<redacted>\"" +
                " and wss://x.shelly.cloud:6113/shelly/wss/hk_sock?t=<redacted> closed",
            redactSecrets(text)
        )
    }

    @Test
    fun `leaves unrelated parameters and null text untouched`() {
        assertEquals("https://host/path?client_id=shelly-diy&grant_type=code", redactSecrets("https://host/path?client_id=shelly-diy&grant_type=code"))
        assertNull(redactSecrets(null))
    }

    @Test
    fun `describes throwables without a message by class name`() {
        assertEquals("IOException", describeSafely(IOException()))
        assertEquals("connection reset", describeSafely(IOException("connection reset")))
    }
}
