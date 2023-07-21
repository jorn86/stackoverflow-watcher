package org.hertsig.stackoverflow.service

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancel
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
import kotlin.time.Duration.Companion.seconds

private val log = logger {}

typealias NewQuestionListener = suspend (NewQuestionMessage) -> Unit

class StackExchangeWebsocketService(
    private val json: Json = defaultJson,
    private val client: HttpClient = defaultClient,
) {
    private var activeWebsocket: WebSocketSession? = null
    private val listeners = mutableListOf<NewQuestionListener>()

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
            socket.close(CloseReason(CloseReason.Codes.GOING_AWAY, "bye"))
            socket.cancel()
            activeWebsocket = null
        }
    }

    private suspend fun WebSocketSession.receiveNextFrame() {
        val frame = incoming.receive()
        log.debug{"Received frame $frame"}
        if (frame !is Frame.Text) {
            log.warn("Skipping unexpected frame $frame")
            return
        }

        val text = frame.readText()
        try {
            val container = json.decodeFromString<WebsocketMessage>(text)
            if (container.action == "hb") {
                log.trace("Responding to heartbeat: ${container.data}")
                send(Frame.Text(container.data)) // pong
                return
            }

            val message = json.decodeFromString<NewQuestionMessage>(container.data)
            log.info { "Received new question: $message" }
            listeners.forEach { it(message) }
        } catch (e: SerializationException) {
            log.error(e) {"Error parsing frame: $text"}
        } catch (e: Exception) {
            log.error(e) {"Unexpected exception for frame $text"}
        }
    }

    fun addWatchedTag(tag: String) {
        withWebsocket {
            send(Frame.Text("1-questions-newest-tag-$tag"))
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
