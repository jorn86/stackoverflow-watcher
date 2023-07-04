package org.hertsig.stackoverflow

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hertsig.core.logger
import org.hertsig.stackoverflow.dto.websocket.NewQuestionMessage
import org.hertsig.stackoverflow.dto.websocket.WebsocketMessage

private val log = logger {}

typealias NewQuestionListener = (NewQuestionMessage) -> Unit

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

    suspend fun connect() = coroutineScope {
        require(activeWebsocket == null) { "Websocket session already active: $activeWebsocket" }
        activeWebsocket = client.webSocketSession(urlString = "wss://qa.sockets.stackexchange.com/")
        launch {
            try {
                while (true) {
                    activeWebsocket!!.receiveNextFrame()
                }
            } finally {
                log.warn("Receive loop has exited, closing websocket connection")
                activeWebsocket?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "bye"))
                activeWebsocket = null
            }
        }
    }

    private suspend fun WebSocketSession.receiveNextFrame() {
        val frame = incoming.receive()
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
            listeners.forEach { it(message) }
        } catch (e: SerializationException) {
            log.error("Error parsing frame: $text", e)
        } catch (e: Exception) {
            log.error("Unexpected exception", e)
        }
    }

    suspend fun addWatchedTag(tag: String) {
        withWebsocket {
            send(Frame.Text("1-questions-newest-tag-$tag"))
        }
    }

    private suspend fun <T> withWebsocket(action: suspend WebSocketSession.() -> T): T {
        return with(activeWebsocket) {
            require(this != null) { "No active websocket" }
            action()
        }
    }
}
