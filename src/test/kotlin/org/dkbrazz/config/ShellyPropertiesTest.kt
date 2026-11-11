package org.dkbrazz.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.Duration

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
    fun `defaults dashboard limits to 150 measures over at most 90 days`() {
        val properties = ShellyProperties()

        assertEquals(150, properties.dashboard.maxMeasures)
        assertEquals(Duration.ofDays(90), properties.dashboard.maxRange)
    }

    @Test
    fun `binds dashboard max range as a duration`() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "shelly.dashboard.max-measures" to "300",
                "shelly.dashboard.max-range" to "14d"
            )
        )
        val properties = Binder(source)
            .bind("shelly", Bindable.of(ShellyProperties::class.java))
            .get()

        assertEquals(300, properties.dashboard.maxMeasures)
        assertEquals(Duration.ofDays(14), properties.dashboard.maxRange)
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
