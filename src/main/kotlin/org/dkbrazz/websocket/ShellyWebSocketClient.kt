package org.dkbrazz.websocket

import jakarta.annotation.PreDestroy
import org.dkbrazz.config.ShellyProperties
import org.dkbrazz.model.dto.ShellyEvent
import org.dkbrazz.service.ShellyAuthService
import org.dkbrazz.service.ShellyEventProcessor
import org.dkbrazz.util.describeSafely
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.PongMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.ObjectMapper
import java.nio.ByteBuffer
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ShellyWebSocketClient(
    private val props: ShellyProperties,
    private val authService: ShellyAuthService,
    private val eventProcessor: ShellyEventProcessor,
    private val objectMapper: ObjectMapper,
    private val connector: ShellyWebSocketConnector,
    private val clock: Clock
) {
    companion object {
        private const val CHECK_DELAY_MS = 2_000L
        private const val IDLE_PING_AFTER_MS = 30_000L
        private const val PONG_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }

    private class RegionConnectionState(now: Long) {
        var generation: Long = 0
        var session: WebSocketSession? = null
        var connectionAttempt: CompletableFuture<WebSocketSession>? = null
        var connecting: Boolean = false
        var connectingSince: Long = 0
        var lastActivityAt: Long = now
        var pingSentAt: Long = 0
    }

    private data class SessionSnapshot(
        val generation: Long,
        val session: WebSocketSession,
        val lastActivityAt: Long,
        val pingSentAt: Long
    )

    private val logger = LoggerFactory.getLogger(ShellyWebSocketClient::class.java)
    private val stopping = AtomicBoolean(false)
    private val connections = props.cloud.websocketRegions.associateWith { RegionConnectionState(clock.millis()) }

    @Scheduled(fixedDelay = CHECK_DELAY_MS)
    fun checkConnection() {
        if (stopping.get()) return

        val now = clock.millis()
        connections.forEach { (region, state) -> checkConnection(region, state, now) }
    }

    private fun checkConnection(region: String, state: RegionConnectionState, now: Long) {
        var timedOutAttempt: CompletableFuture<WebSocketSession>? = null
        var shouldConnect = false
        var snapshot: SessionSnapshot? = null

        synchronized(state) {
            if (stopping.get()) return

            if (state.connecting) {
                if (now - state.connectingSince <= CONNECT_TIMEOUT_MS) return

                logger.warn("Shelly WebSocket connection attempt timed out for region $region, retrying")
                timedOutAttempt = state.connectionAttempt
                invalidateConnectionLocked(state)
                shouldConnect = true
            } else {
                val currentSession = state.session
                if (currentSession == null || !currentSession.isOpen) {
                    if (currentSession != null) {
                        state.session = null
                        state.generation++
                    }
                    shouldConnect = true
                } else {
                    snapshot = SessionSnapshot(
                        generation = state.generation,
                        session = currentSession,
                        lastActivityAt = state.lastActivityAt,
                        pingSentAt = state.pingSentAt
                    )
                }
            }
        }

        timedOutAttempt?.cancel(true)

        if (shouldConnect) {
            connect(region, state)
            return
        }

        val current = snapshot ?: return
        if (current.pingSentAt != 0L) {
            if (now - current.pingSentAt > PONG_TIMEOUT_MS) {
                logger.warn("Shelly WebSocket ping timed out for region $region")
                disconnectCurrent(region, state, current.generation, current.session)
            }
            return
        }

        if (now - current.lastActivityAt > IDLE_PING_AFTER_MS) {
            sendPing(region, state, current.generation, current.session, now)
        }
    }

    private fun sendPing(
        region: String,
        state: RegionConnectionState,
        generation: Long,
        session: WebSocketSession,
        now: Long
    ) {
        val shouldSend = synchronized(state) {
            if (!isCurrentSessionLocked(state, generation, session) || state.pingSentAt != 0L) {
                false
            } else {
                state.pingSentAt = now
                true
            }
        }
        if (!shouldSend) return

        try {
            logger.debug("Sending Shelly WebSocket ping after inactivity for region $region")
            session.sendMessage(PingMessage(ByteBuffer.allocate(0)))
        } catch (e: Exception) {
            logger.error("Failed to send Shelly WebSocket ping for region $region: ${safeError(e)}")
            disconnectCurrent(region, state, generation, session)
        }
    }

    private fun connect(region: String, state: RegionConnectionState) {
        val generation = synchronized(state) {
            if (stopping.get() || state.connecting || state.session?.isOpen == true) return

            state.generation++
            state.connecting = true
            state.connectingSince = clock.millis()
            state.generation
        }

        Thread.ofVirtual().name("shelly-ws-connect-$region").start {
            try {
                val url = props.websocketUrl(region)
                val accessToken = authService.getAccessToken()

                if (!isCurrentAttempt(state, generation)) return@start

                logger.info("Connecting to Shelly Cloud WebSocket for region $region: $url")
                val fullUrl = if (url.contains("?")) "$url&t=$accessToken" else "$url?t=$accessToken"
                val handler = createHandler(region, state, generation)
                val attempt = connector.connect(handler, fullUrl)
                var cancelAttempt = false

                synchronized(state) {
                    if (stopping.get() || state.generation != generation) {
                        cancelAttempt = true
                    } else if (state.connecting) {
                        state.connectionAttempt = attempt
                    }
                }

                if (cancelAttempt) {
                    attempt.cancel(true)
                    return@start
                }

                attempt.whenComplete { _, throwable ->
                    if (throwable != null) {
                        failConnectionAttempt(region, state, generation, throwable)
                    }
                }
            } catch (e: Exception) {
                failConnectionAttempt(region, state, generation, e)
            }
        }
    }

    private fun createHandler(
        region: String,
        state: RegionConnectionState,
        generation: Long
    ): TextWebSocketHandler = object : TextWebSocketHandler() {
        override fun afterConnectionEstablished(session: WebSocketSession) {
            val accepted = synchronized(state) {
                if (stopping.get() || state.generation != generation || !state.connecting) {
                    false
                } else {
                    state.session = session
                    state.connectionAttempt = null
                    state.connecting = false
                    state.connectingSince = 0
                    markActivityLocked(state)
                    true
                }
            }

            if (!accepted) {
                logger.debug("Closing stale Shelly WebSocket session for region $region")
                closeQuietly(region, session)
                return
            }

            logger.info("Shelly WebSocket connection established for region $region")
        }

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            if (!markActivityIfCurrent(state, generation, session)) return

            try {
                val root = objectMapper.readTree(message.payload)
                val eventType = root.get("event")?.stringValue()

                when (eventType) {
                    "Shelly:StatusOnChange" -> {
                        val event = objectMapper.treeToValue(root, ShellyEvent::class.java)
                        eventProcessor.processEvent(event)
                    }
                    else -> logger.debug("Received Shelly message with event $eventType from region $region")
                }
            } catch (e: Exception) {
                logger.error(
                    "Failed to parse Shelly message from region $region: ${message.payload.take(100)}...",
                    e
                )
            }
        }

        override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
            if (markActivityIfCurrent(state, generation, session)) {
                logger.debug("Received Shelly WebSocket pong for region $region")
            }
        }

        override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
            if (!isCurrentSession(state, generation, session)) return

            logger.error("Shelly WebSocket transport error for region $region: ${safeError(exception)}")
            disconnectCurrent(region, state, generation, session)
        }

        override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
            val reset = resetCurrentSession(state, generation, session)
            if (reset) {
                logger.warn("Shelly WebSocket connection closed for region $region: $status")
            }
        }
    }

    private fun failConnectionAttempt(
        region: String,
        state: RegionConnectionState,
        generation: Long,
        throwable: Throwable
    ) {
        val failed = synchronized(state) {
            if (stopping.get() || state.generation != generation || !state.connecting) {
                false
            } else {
                invalidateConnectionLocked(state)
                true
            }
        }

        if (failed) {
            logger.error("Failed to establish Shelly WebSocket connection for region $region: ${safeError(throwable)}")
        }
    }

    private fun disconnectCurrent(
        region: String,
        state: RegionConnectionState,
        generation: Long,
        session: WebSocketSession
    ) {
        if (resetCurrentSession(state, generation, session)) {
            closeQuietly(region, session)
        }
    }

    private fun resetCurrentSession(
        state: RegionConnectionState,
        generation: Long,
        session: WebSocketSession
    ): Boolean = synchronized(state) {
        if (!isCurrentSessionLocked(state, generation, session)) {
            false
        } else {
            state.generation++
            state.session = null
            state.pingSentAt = 0
            state.lastActivityAt = clock.millis()
            true
        }
    }

    private fun markActivityIfCurrent(
        state: RegionConnectionState,
        generation: Long,
        session: WebSocketSession
    ): Boolean = synchronized(state) {
        if (!isCurrentSessionLocked(state, generation, session)) {
            false
        } else {
            markActivityLocked(state)
            true
        }
    }

    private fun markActivityLocked(state: RegionConnectionState) {
        state.lastActivityAt = clock.millis()
        state.pingSentAt = 0
    }

    private fun isCurrentAttempt(state: RegionConnectionState, generation: Long): Boolean = synchronized(state) {
        !stopping.get() && state.generation == generation && state.connecting
    }

    private fun isCurrentSession(
        state: RegionConnectionState,
        generation: Long,
        session: WebSocketSession
    ): Boolean = synchronized(state) {
        isCurrentSessionLocked(state, generation, session)
    }

    private fun isCurrentSessionLocked(
        state: RegionConnectionState,
        generation: Long,
        session: WebSocketSession
    ): Boolean = !stopping.get() && state.generation == generation && state.session === session

    private fun invalidateConnectionLocked(state: RegionConnectionState) {
        state.generation++
        state.connectionAttempt = null
        state.connecting = false
        state.connectingSince = 0
        state.pingSentAt = 0
    }

    private fun closeQuietly(region: String, session: WebSocketSession) {
        try {
            session.close()
        } catch (e: Exception) {
            logger.error("Failed to close Shelly WebSocket session for region $region: ${safeError(e)}")
        }
    }

    private fun safeError(throwable: Throwable): String = describeSafely(throwable)

    @PreDestroy
    fun shutdown() {
        if (!stopping.compareAndSet(false, true)) return

        val attempts = mutableListOf<CompletableFuture<WebSocketSession>>()
        val sessions = mutableListOf<Pair<String, WebSocketSession>>()

        connections.forEach { (region, state) ->
            synchronized(state) {
                state.generation++
                state.connectionAttempt?.let(attempts::add)
                state.session?.let { sessions.add(region to it) }
                state.connectionAttempt = null
                state.session = null
                state.connecting = false
                state.connectingSince = 0
                state.pingSentAt = 0
            }
        }

        attempts.forEach { it.cancel(true) }
        sessions.forEach { (region, session) -> closeQuietly(region, session) }
    }
}
