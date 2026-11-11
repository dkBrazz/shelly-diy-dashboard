package org.dkbrazz.websocket

import org.dkbrazz.config.ShellyProperties
import org.dkbrazz.model.dto.ShellyDeviceDTO
import org.dkbrazz.model.dto.ShellyEvent
import org.dkbrazz.model.dto.ShellyStatusDTO
import org.dkbrazz.service.ShellyAuthService
import org.dkbrazz.service.ShellyEventProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.PongMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class ShellyWebSocketClientTest {
    @Test
    fun `connects every region with shared authentication and forwards their events`() {
        val harness = Harness()
        val event = shellyEvent()
        val root = mock(JsonNode::class.java)
        val eventType = mock(JsonNode::class.java)
        `when`(harness.objectMapper.readTree(anyString())).thenReturn(root)
        `when`(root.get("event")).thenReturn(eventType)
        `when`(eventType.stringValue()).thenReturn("Shelly:StatusOnChange")
        `when`(harness.objectMapper.treeToValue(root, ShellyEvent::class.java)).thenReturn(event)

        harness.client.checkConnection()
        val attempts = harness.connector.awaitAttempts(2)
        val attempt241 = attempts.forRegion("shelly-241-eu")
        val attempt242 = attempts.forRegion("shelly-242-eu")
        val session241 = openSession()
        val session242 = openSession()

        attempt241.establish(session241)
        attempt242.establish(session242)
        attempt241.handler.handleMessage(session241, TextMessage("{}"))
        attempt242.handler.handleMessage(session242, TextMessage("{}"))

        assertTrue(attempt241.url.endsWith("?t=shared-token"))
        assertTrue(attempt242.url.endsWith("?t=shared-token"))
        verify(harness.eventProcessor, times(2)).processEvent(event)
    }

    @Test
    fun `transport error reconnects only its own region`() {
        val harness = Harness()
        harness.client.checkConnection()
        val initialAttempts = harness.connector.awaitAttempts(2)
        val attempt241 = initialAttempts.forRegion("shelly-241-eu")
        val attempt242 = initialAttempts.forRegion("shelly-242-eu")
        val session241 = openSession()
        val session242 = openSession()
        attempt241.establish(session241)
        attempt242.establish(session242)

        attempt241.handler.handleTransportError(session241, IOException("connection lost"))
        harness.client.checkConnection()
        val allAttempts = harness.connector.awaitAttempts(3)

        assertEquals(2, allAttempts.countForRegion("shelly-241-eu"))
        assertEquals(1, allAttempts.countForRegion("shelly-242-eu"))
        verify(session241).close()
        verify(session242, never()).close()
    }

    @Test
    fun `failed handshake retries only its own region`() {
        val harness = Harness()
        harness.client.checkConnection()
        val initialAttempts = harness.connector.awaitAttempts(2)
        val attempt241 = initialAttempts.forRegion("shelly-241-eu")
        initialAttempts.forRegion("shelly-242-eu").establish(openSession())

        attempt241.future.completeExceptionally(IOException("upgrade rejected"))
        val allAttempts = harness.awaitAttemptsWhileChecking(3)

        assertEquals(2, allAttempts.countForRegion("shelly-241-eu"))
        assertEquals(1, allAttempts.countForRegion("shelly-242-eu"))
    }

    @Test
    fun `heartbeat state and ping timeout are isolated by region`() {
        val harness = Harness()
        harness.client.checkConnection()
        val attempts = harness.connector.awaitAttempts(2)
        val attempt241 = attempts.forRegion("shelly-241-eu")
        val attempt242 = attempts.forRegion("shelly-242-eu")
        val session241 = openSession()
        val session242 = openSession()
        attempt241.establish(session241)
        attempt242.establish(session242)

        harness.clock.advanceSeconds(31)
        attempt242.handler.handleMessage(session242, PongMessage())
        harness.client.checkConnection()

        verify(session241).sendMessage(isA(PingMessage::class.java))
        verify(session242, never()).sendMessage(isA(PingMessage::class.java))

        harness.clock.advanceSeconds(11)
        harness.client.checkConnection()

        verify(session241).close()
        verify(session242, never()).close()
    }

    @Test
    fun `stale timed out attempt cannot replace its retry session`() {
        val harness = Harness()
        harness.client.checkConnection()
        val initialAttempts = harness.connector.awaitAttempts(2)
        val old241 = initialAttempts.forRegion("shelly-241-eu")
        val attempt242 = initialAttempts.forRegion("shelly-242-eu")
        attempt242.establish(openSession())

        harness.clock.advanceSeconds(16)
        harness.client.checkConnection()
        val allAttempts = harness.connector.awaitAttempts(3)
        val new241 = allAttempts.filterForRegion("shelly-241-eu").last()
        val staleSession = openSession()
        val currentSession = openSession()

        old241.handler.afterConnectionEstablished(staleSession)
        new241.establish(currentSession)
        old241.handler.afterConnectionClosed(staleSession, CloseStatus.NORMAL)
        harness.client.checkConnection()

        verify(staleSession).close()
        verify(currentSession, never()).close()
        assertEquals(3, harness.connector.attempts.size)
    }

    @Test
    fun `unknown and malformed messages do not reset either connection`() {
        val harness = Harness()
        val root = mock(JsonNode::class.java)
        val eventType = mock(JsonNode::class.java)
        `when`(harness.objectMapper.readTree("{}")).thenReturn(root)
        `when`(root.get("event")).thenReturn(eventType)
        `when`(eventType.stringValue()).thenReturn("Shelly:Online")
        `when`(harness.objectMapper.readTree("not-json")).thenThrow(IllegalArgumentException("bad payload"))
        harness.client.checkConnection()
        val attempts = harness.connector.awaitAttempts(2)
        val attempt241 = attempts.forRegion("shelly-241-eu")
        val session241 = openSession()
        val session242 = openSession()
        attempt241.establish(session241)
        attempts.forRegion("shelly-242-eu").establish(session242)

        attempt241.handler.handleMessage(session241, TextMessage("{}"))
        attempt241.handler.handleMessage(session241, TextMessage("not-json"))
        harness.client.checkConnection()

        assertEquals(2, harness.connector.attempts.size)
        verify(session241, never()).close()
        verify(session242, never()).close()
        verifyNoInteractions(harness.eventProcessor)
    }

    @Test
    fun `shutdown closes all sessions and suppresses retries`() {
        val harness = Harness()
        harness.client.checkConnection()
        val attempts = harness.connector.awaitAttempts(2)
        val session241 = openSession()
        val session242 = openSession()
        val attempt241 = attempts.forRegion("shelly-241-eu")
        attempt241.establish(session241)
        attempts.forRegion("shelly-242-eu").establish(session242)

        harness.client.shutdown()
        attempt241.handler.afterConnectionClosed(session241, CloseStatus.NORMAL)
        harness.client.checkConnection()

        verify(session241).close()
        verify(session242).close()
        assertEquals(2, harness.connector.attempts.size)
    }

    @Test
    fun `additional configured region creates an additional listener`() {
        val harness = Harness(listOf("shelly-241-eu", "shelly-242-eu", "shelly-243-eu"))

        harness.client.checkConnection()
        val attempts = harness.connector.awaitAttempts(3)

        assertEquals(1, attempts.countForRegion("shelly-241-eu"))
        assertEquals(1, attempts.countForRegion("shelly-242-eu"))
        assertEquals(1, attempts.countForRegion("shelly-243-eu"))
    }

    @Test
    fun `account region from the token is monitored even when the configured list omits it`() {
        val harness = Harness(listOf("shelly-241-eu", "shelly-242-eu"), accountRegion = "shelly-243-eu")

        harness.client.checkConnection()
        val attempts = harness.connector.awaitAttempts(3)

        assertEquals(1, attempts.countForRegion("shelly-241-eu"))
        assertEquals(1, attempts.countForRegion("shelly-242-eu"))
        assertEquals(1, attempts.countForRegion("shelly-243-eu"))
    }

    @Test
    fun `account region already in the configured list is connected once`() {
        val harness = Harness(listOf("shelly-242-eu"), accountRegion = "shelly-242-eu")

        harness.client.checkConnection()
        val attempts = harness.connector.awaitAttempts(1)

        assertEquals(1, attempts.countForRegion("shelly-242-eu"))
    }

    private class Harness(
        regions: List<String> = listOf("shelly-241-eu", "shelly-242-eu"),
        accountRegion: String = "shelly-241-eu"
    ) {
        val authService: ShellyAuthService = mock(ShellyAuthService::class.java)
        val eventProcessor: ShellyEventProcessor = mock(ShellyEventProcessor::class.java)
        val objectMapper: ObjectMapper = mock(ObjectMapper::class.java)
        val connector = FakeConnector()
        val clock = MutableClock(Instant.parse("2026-08-07T00:00:00Z"))
        val client: ShellyWebSocketClient

        init {
            val properties = ShellyProperties()
            properties.cloud.websocketRegions = regions
            `when`(authService.getAccessToken()).thenReturn("shared-token")
            `when`(authService.accountRegion).thenReturn(accountRegion)
            client = ShellyWebSocketClient(
                properties,
                authService,
                eventProcessor,
                objectMapper,
                connector,
                clock
            )
        }

        fun awaitAttemptsWhileChecking(expected: Int): List<Attempt> {
            val deadline = System.nanoTime() + 2_000_000_000L
            while (connector.attempts.size < expected && System.nanoTime() < deadline) {
                client.checkConnection()
                Thread.sleep(5)
            }
            assertEquals(expected, connector.attempts.size)
            return connector.attempts.toList()
        }
    }

    private data class Attempt(
        val handler: WebSocketHandler,
        val url: String,
        val future: CompletableFuture<WebSocketSession> = CompletableFuture()
    ) {
        fun establish(session: WebSocketSession) {
            handler.afterConnectionEstablished(session)
            future.complete(session)
        }
    }

    private class FakeConnector : ShellyWebSocketConnector {
        val attempts = CopyOnWriteArrayList<Attempt>()

        override fun connect(handler: WebSocketHandler, url: String): CompletableFuture<WebSocketSession> {
            val attempt = Attempt(handler, url)
            attempts.add(attempt)
            return attempt.future
        }

        fun awaitAttempts(expected: Int): List<Attempt> {
            val deadline = System.nanoTime() + 2_000_000_000L
            while (attempts.size < expected && System.nanoTime() < deadline) {
                Thread.sleep(5)
            }
            assertEquals(expected, attempts.size)
            return attempts.toList()
        }
    }

    private class MutableClock(initialInstant: Instant) : Clock() {
        private val epochMillis = AtomicLong(initialInstant.toEpochMilli())

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(epochMillis.get())

        fun advanceSeconds(seconds: Long) {
            epochMillis.addAndGet(seconds * 1_000)
        }
    }

    private companion object {
        fun openSession(): WebSocketSession = mock(WebSocketSession::class.java).also {
            `when`(it.isOpen).thenReturn(true)
        }

        fun shellyEvent() = ShellyEvent(
            event = "Shelly:StatusOnChange",
            device = ShellyDeviceDTO("device-1", "type", "code", "2"),
            status = ShellyStatusDTO(null, null, 1.0)
        )

        fun List<Attempt>.filterForRegion(region: String): List<Attempt> =
            filter { it.url.contains("//$region.") }

        fun List<Attempt>.forRegion(region: String): Attempt = filterForRegion(region).single()

        fun List<Attempt>.countForRegion(region: String): Int = filterForRegion(region).size
    }
}
