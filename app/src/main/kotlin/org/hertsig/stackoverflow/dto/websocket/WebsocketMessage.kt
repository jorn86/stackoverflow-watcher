package org.hertsig.stackoverflow.dto.websocket

import kotlinx.serialization.Serializable

@Serializable
data class WebsocketMessage(val action: String, val data: String)