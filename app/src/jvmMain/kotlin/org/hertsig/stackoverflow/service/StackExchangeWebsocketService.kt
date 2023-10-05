package org.hertsig.stackoverflow.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hertsig.logger.logger
import org.hertsig.stackoverflow.SiteMetadata
import org.hertsig.stackoverflow.dto.websocket.NewQuestionMessage
import org.hertsig.stackoverflow.dto.websocket.WebsocketMessage
import org.hertsig.util.backgroundTask
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = logger {}

typealias NewQuestionListener = suspend (String, NewQuestionMessage) -> Unit

class StackExchangeWebsocketService(
    private val json: Json = defaultJson,
    private val client: HttpClient = defaultClient,
) {
    private var activeWebsocket: WebSocketSession? = null
    private val listeners = mutableListOf<NewQuestionListener>()
    private var lastContact = Instant.EPOCH
    var connectionError by mutableStateOf(false); private set

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
            connectionError = true
            cleanupWebsocket(socket)
        }
        socket.launch {
            backgroundTask("Websocket ping", 2.minutes, false) {
                if (lastContact.isBefore(Instant.now().minusSeconds(119))) {
                    log.debug("Sending ping")
                    socket.send(Frame.Text("ping"))
                    delay(15.seconds)
                    if (lastContact.isBefore(Instant.now().minusSeconds(16))) {
                        error("Ping did not receive pong response") // throwing stops background task
                    }
                }
            }
            connectionError = true
            cleanupWebsocket(socket)
        }
    }

    private suspend fun cleanupWebsocket(socket: WebSocketSession) {
        socket.close(CloseReason(CloseReason.Codes.GOING_AWAY, "bye"))
        try {
            socket.cancel()
        } finally {
            activeWebsocket = null
        }
    }

    private suspend fun WebSocketSession.receiveNextFrame() {
        val frame = incoming.receive()
        log.debug { "Received frame $frame" }
        lastContact = Instant.now()
        if (frame !is Frame.Text) {
            return log.warn("Skipping unexpected frame $frame")
        }

        val text = frame.readText()
        if (text == "pong") {
            return
        }

        val (action, message) = try {
            val container = json.decodeFromString<WebsocketMessage>(text)
            if (container.action == "hb") {
                log.trace("Responding to heartbeat: ${container.data}")
                return send(Frame.Text(container.data)) // pong
            }
            container.action to json.decodeFromString<NewQuestionMessage>(container.data)
        } catch (e: SerializationException) {
            return log.error(e) {"Error parsing frame: $text"}
        } catch (e: Exception) {
            return log.error(e) {"Unexpected exception for frame $text"}
        }
        log.info { "Received new question: $message" }
        listeners.forEach { it(action, message) }
    }

    fun addWatchedTag(site: SiteMetadata, tag: String) {
        withWebsocket {
            send(Frame.Text("${site.siteId}-questions-newest-tag-$tag"))
            log.info { "Subscribed to tag $tag on ${site.name}" }
        }
    }

    private fun withWebsocket(action: suspend WebSocketSession.() -> Unit) {
        with(activeWebsocket) {
            require(this != null && isActive) { "No active websocket" }
            launch { action() }
        }
    }
}
