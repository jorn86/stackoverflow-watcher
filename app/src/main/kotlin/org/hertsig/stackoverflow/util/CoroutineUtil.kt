package org.hertsig.stackoverflow.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.hertsig.core.debug
import org.hertsig.core.error
import org.hertsig.core.info
import org.hertsig.core.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = logger {}

suspend fun backgroundTask(
    name: String,
    delay: Duration = 1.seconds,
    immediately: Boolean = true,
    task: suspend () -> Unit
) {
    log.debug{"Started: $name"}
    try {
        if (immediately) task()
        while (true) {
            delay(delay)
            task()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error(e) { "Exception in background task" }
    } finally {
        log.info{"Cancelled: $name"}
    }
}
