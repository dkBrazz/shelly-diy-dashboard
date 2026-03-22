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
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ShellyWebSocketClient(
    private val props: ShellyProperties,
    private val authService: ShellyAuthService,
    private val eventProcessor: ShellyEventProcessor,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(ShellyWebSocketClient::class.java)
    private var session: WebSocketSession? = null
    private val connecting = AtomicBoolean(false)

    @Scheduled(fixedDelay = 30000)
    fun checkConnection() {
        if (session == null || session?.isOpen == false) {
            connect()
        }
    }

    private fun connect() {
        if (!connecting.compareAndSet(false, true)) return

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
                    connecting.set(false)
                }

                override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                    try {
                        val event = objectMapper.readValue(message.payload, ShellyEvent::class.java)
                        eventProcessor.processEvent(event)
                    } catch (e: Exception) {
                        logger.error("Failed to parse Shelly event: ${message.payload.take(100)}...", e)
                    }
                }

                override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
                    logger.error("WebSocket transport error: ${exception.message}", exception)
                }

                override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
                    logger.warn("WebSocket connection closed: $status")
                    this@ShellyWebSocketClient.session = null
                    connecting.set(false)
                }
            }

            client.execute(handler, fullUrl)
        } catch (e: Exception) {
            logger.error("Failed to initiate WebSocket connection: ${e.message}", e)
            connecting.set(false)
        }
    }
}
