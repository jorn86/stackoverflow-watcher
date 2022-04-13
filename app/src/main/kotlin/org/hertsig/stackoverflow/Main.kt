package org.hertsig.stackoverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hertsig.compose.registerExceptionHandler
import org.hertsig.core.logger
import org.hertsig.stackoverflow.ui.App
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = logger {}
internal const val APP_NAME = "StackOverflow watcher"
private val initialTags = arrayOf("java", "kotlin", "jooq", "guava", "guice", "jersey", "compose-desktop")

fun main() = runBlocking {
    registerExceptionHandler()

    val service = StackOverflowService()
    service.preloadWatchedQuestions(initialTags.toSet())
    launch { service.startWebsocket(*initialTags) }
    launch { service.startQuestionPoller() }

    application {
        Window(
            ::exitApplication,
            rememberWindowState(width = 1500.dp, height = 900.dp),
            title = APP_NAME,
        ) {
            App(service)
        }
    }
}

internal fun sendNotification(title: String, message: String, timeout: Duration = 10.seconds) {
    val builder = ProcessBuilder("notify-send", "-a", APP_NAME, "-t", "${timeout.inWholeMilliseconds}",
        title, message)
    builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    builder.redirectError(ProcessBuilder.Redirect.INHERIT)
    try {
        val process = builder.start()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            log.debug("Notification process terminated successfully")
        } else {
            log.error("Notification process terminated with code $exitCode")
        }
    } catch (e: Exception) {
        log.error("Error showing notification", e)
    }
}
