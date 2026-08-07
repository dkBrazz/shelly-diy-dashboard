package org.dkbrazz.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class ShellyPropertiesTest {
    @Test
    fun `defaults to both known websocket regions`() {
        val properties = ShellyProperties()

        assertEquals(
            listOf("shelly-241-eu", "shelly-242-eu"),
            properties.cloud.websocketRegions
        )
    }

    @Test
    fun `normalizes configured websocket regions`() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "shelly.cloud.websocket-regions" to
                    " shelly-242-eu ,shelly-241-eu,shelly-242-eu, "
            )
        )
        val properties = Binder(source)
            .bind("shelly", Bindable.of(ShellyProperties::class.java))
            .get()

        assertEquals(
            listOf("shelly-242-eu", "shelly-241-eu"),
            properties.cloud.websocketRegions
        )
    }

    @Test
    fun `rejects an empty websocket region list`() {
        val properties = ShellyProperties()

        assertThrows(IllegalArgumentException::class.java) {
            properties.cloud.websocketRegions = listOf(" ", "")
        }
    }

    @Test
    fun `oauth region is independent from websocket regions`() {
        val properties = ShellyProperties()
        properties.cloud.region = "shelly-auth-eu"
        properties.cloud.websocketRegions = listOf("shelly-events-eu")

        assertEquals("https://shelly-auth-eu.shelly.cloud", properties.apiUrl)
        assertEquals(
            "wss://shelly-events-eu.shelly.cloud:6113/shelly/wss/hk_sock",
            properties.websocketUrl("shelly-events-eu")
        )
    }
}
