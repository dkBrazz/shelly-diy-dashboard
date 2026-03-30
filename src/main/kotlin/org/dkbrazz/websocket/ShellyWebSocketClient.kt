package org.dkbrazz.websocket
import org.dkbrazz.config.ShellyProperties
import org.dkbrazz.model.dto.ShellyEvent
import org.dkbrazz.service.ShellyAuthService
import org.dkbrazz.service.ShellyEventProcessor
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.ObjectMapper
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Component
class ShellyWebSocketClient(
    private val props: ShellyProperties,
    private val authService: ShellyAuthService,
    private val eventProcessor: ShellyEventProcessor,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val CHECK_DELAY_MS = 2_000L
        private const val IDLE_PING_AFTER_MS = 30_000L
        private const val PONG_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }

    private val logger = LoggerFactory.getLogger(ShellyWebSocketClient::class.java)
    
    @Volatile
    private var session: WebSocketSession? = null
    private val connecting = AtomicBoolean(false)
    
    private val lastActivityAt = AtomicLong(System.currentTimeMillis())
    private val pingSentAt = AtomicLong(0)
    private val connectingSince = AtomicLong(0)

    @Scheduled(fixedDelay = CHECK_DELAY_MS)
    fun checkConnection() {
        val now = System.currentTimeMillis()

        if (connecting.get()) {
            if (now - connectingSince.get() > CONNECT_TIMEOUT_MS) {
                logger.warn("WebSocket connection attempt timed out, retrying")
                resetConnectionState()
            } else {
                return
            }
        }

        val currentSession = session
        if (currentSession == null || !currentSession.isOpen) {
            connect()
            return
        }

        val outstandingPingSentAt = pingSentAt.get()
        if (outstandingPingSentAt != 0L) {
            if (now - outstandingPingSentAt > PONG_TIMEOUT_MS) {
                logger.warn("WebSocket ping timeout, re-initiating connection")
                closeAndReconnect()
            }
            return
        }

        if (now - lastActivityAt.get() > IDLE_PING_AFTER_MS) {
            sendPing(currentSession)
        }
    }

    private fun sendPing(session: WebSocketSession) {
        try {
            logger.debug("Sending WebSocket ping after inactivity")
            session.sendMessage(PingMessage(ByteBuffer.allocate(0)))
            pingSentAt.compareAndSet(0, System.currentTimeMillis())
        } catch (e: Exception) {
            logger.error("Failed to send WebSocket ping: ${e.message}", e)
            closeAndReconnect()
        }
    }

    private fun closeAndReconnect() {
        try {
            session?.close()
        } catch (e: Exception) {
            logger.error("Error closing session during reconnect: ${e.message}")
        }
        resetConnectionState()
        connect()
    }

    private fun resetConnectionState() {
        session = null
        pingSentAt.set(0)
        connectingSince.set(0)
        connecting.set(false)
    }

    private fun markActivity() {
        lastActivityAt.set(System.currentTimeMillis())
        pingSentAt.set(0)
    }

    private fun connect() {
        if (!connecting.compareAndSet(false, true)) return
        connectingSince.set(System.currentTimeMillis())

        Thread.ofVirtual().name("shelly-ws-connect").start {
            try {
                val url = props.websocketUrl
                val accessToken = authService.getAccessToken()
                
                logger.info("Connecting to Shelly Cloud WebSocket: $url")
                val client = StandardWebSocketClient()
                
                // Append auth key (JWT token)
                val fullUrl = if (url.contains("?")) "$url&t=$accessToken" else "$url?t=$accessToken"
                
                val handler = object : TextWebSocketHandler() {
                    override fun afterConnectionEstablished(session: WebSocketSession) {
                        logger.info("WebSocket connection established")
                        this@ShellyWebSocketClient.session = session
                        markActivity()
                        connectingSince.set(0)
                        connecting.set(false)
                    }

                    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                        markActivity()

                        try {
                            val root = objectMapper.readTree(message.payload)
                            val eventType = root.get("event")?.asText()

                            when (eventType) {
                                "Shelly:StatusOnChange" -> {
                                    val event = objectMapper.treeToValue(root, ShellyEvent::class.java)
                                    eventProcessor.processEvent(event)
                                }
                                else -> {
                                    logger.debug("Received message with event: $eventType")
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to parse Shelly message: ${message.payload.take(100)}...", e)
                        }
                    }

                    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
                        logger.debug("Received WebSocket pong")
                        markActivity()
                    }

                    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
                        logger.error("WebSocket transport error: ${exception.message}", exception)
                        closeAndReconnect()
                    }

                    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
                        logger.warn("WebSocket connection closed: $status")
                        resetConnectionState()
                    }
                }

                client.execute(handler, fullUrl).whenComplete { _, throwable ->
                    if (throwable != null) {
                        logger.error("Failed to establish WebSocket connection: ${throwable.message}", throwable)
                        resetConnectionState()
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to initiate WebSocket connection: ${e.message}", e)
                resetConnectionState()
            }
        }
    }
}
