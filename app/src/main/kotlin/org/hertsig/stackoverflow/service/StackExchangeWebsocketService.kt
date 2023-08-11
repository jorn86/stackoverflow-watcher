package org.hertsig.stackoverflow.service

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hertsig.core.debug
import org.hertsig.core.error
import org.hertsig.core.info
import org.hertsig.core.logger
import org.hertsig.stackoverflow.dto.websocket.NewQuestionMessage
import org.hertsig.stackoverflow.dto.websocket.WebsocketMessage
import org.hertsig.stackoverflow.util.backgroundTask
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = logger {}

typealias NewQuestionListener = suspend (NewQuestionMessage) -> Unit

class StackExchangeWebsocketService(
    private val json: Json = defaultJson,
    private val client: HttpClient = defaultClient,
) {
    private var activeWebsocket: WebSocketSession? = null
    private val listeners = mutableListOf<NewQuestionListener>()
    private var lastPong = Instant.EPOCH

    fun addListener(listener: NewQuestionListener) {
        listeners.add(listener)
    }

    fun hasActiveWebsocket() = activeWebsocket != null

    suspend fun connect() {
        require(activeWebsocket == null) { "Websocket session already active: $activeWebsocket" }
        val socket = client.webSocketSession(urlString = "wss://qa.sockets.stackexchange.com/")
        activeWebsocket = socket
        log.info("Websocket connected")
        socket.launch {
            backgroundTask("Websocket", 0.seconds) { socket.receiveNextFrame() }
            log.warn("Receive loop has exited, closing websocket connection")
            cleanupWebsocket(socket)
        }
        socket.launch {
            backgroundTask("Websocket ping", 5.minutes, false) {
                socket.send(Frame.Text("ping"))
                delay(5.seconds)
                if (lastPong.isBefore(Instant.now().minusSeconds(6))) {
                    log.warn("Ping did not receive pong response")
                }
            }
            log.warn("Ping loop has exited, closing websocket connection")
            cleanupWebsocket(socket)
        }
    }

    private suspend fun cleanupWebsocket(socket: DefaultClientWebSocketSession) {
        socket.close(CloseReason(CloseReason.Codes.GOING_AWAY, "bye"))
        try {
            socket.cancel()
        } finally {
            activeWebsocket = null
        }
    }

    private suspend fun WebSocketSession.receiveNextFrame() {
        val frame = incoming.receive()
        log.debug{"Received frame $frame"}
        if (frame !is Frame.Text) {
            return log.warn("Skipping unexpected frame $frame")
        }

        val text = frame.readText()
        if (text == "pong") {
            lastPong = Instant.now()
            return
        }

        val message = try {
            val container = json.decodeFromString<WebsocketMessage>(text)
            if (container.action == "hb") {
                log.trace("Responding to heartbeat: ${container.data}")
                return send(Frame.Text(container.data)) // pong
            }
            json.decodeFromString<NewQuestionMessage>(container.data)
        } catch (e: SerializationException) {
            return log.error(e) {"Error parsing frame: $text"}
        } catch (e: Exception) {
            return log.error(e) {"Unexpected exception for frame $text"}
        }
        log.info { "Received new question: $message" }
        listeners.forEach { it(message) }
    }

    fun addWatchedTag(tag: String, siteId: Int = 1) {
        withWebsocket {
            send(Frame.Text("$siteId-questions-newest-tag-$tag"))
            log.info { "Subscribed to tag $tag" }
        }
    }

    private fun withWebsocket(action: suspend WebSocketSession.() -> Unit) {
        with(activeWebsocket) {
            require(this != null) { "No active websocket" }
            launch { action() }
        }
    }
}
