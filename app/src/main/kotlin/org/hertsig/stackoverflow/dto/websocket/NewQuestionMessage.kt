package org.hertsig.stackoverflow.dto.websocket

import kotlinx.serialization.Serializable

@Serializable
data class NewQuestionMessage(val id: String, val tags: List<String>)
