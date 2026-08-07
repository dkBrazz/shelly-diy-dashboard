package org.dkbrazz.websocket

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import java.time.Clock
import java.util.concurrent.CompletableFuture

fun interface ShellyWebSocketConnector {
    fun connect(handler: WebSocketHandler, url: String): CompletableFuture<WebSocketSession>
}

@Configuration
class ShellyWebSocketConfiguration {
    @Bean
    fun shellyWebSocketConnector(): ShellyWebSocketConnector =
        ShellyWebSocketConnector { handler, url -> StandardWebSocketClient().execute(handler, url) }

    @Bean
    fun shellyWebSocketClock(): Clock = Clock.systemUTC()
}
