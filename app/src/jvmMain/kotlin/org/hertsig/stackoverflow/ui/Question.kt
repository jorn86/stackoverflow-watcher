package org.hertsig.stackoverflow.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.*
import org.hertsig.stackoverflow.controller.QuestionController
import org.hertsig.stackoverflow.dto.api.Question
import java.awt.Desktop
import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter

private val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

@Composable
fun Question(controller: QuestionController, question: Question) {
    val alpha by animateFloatAsState(if (controller.fade(question)) .6f else 1f)
    SpacedRow(Modifier.alpha(alpha)
        .clickable {
            controller.removeNew(question.questionId)
            desktop?.browse(URI(question.link))
        },
        16.dp, vertical = Alignment.CenterVertically
    ) {
        Row(
            Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)).size(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextLine(
                question.score.toString(),
                Modifier.fillMaxWidth(),
                MaterialTheme.colorScheme.onPrimary,
                style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                align = TextAlign.Center
            )
        }
        SpacedColumn {
            SpacedRow {
                if (question.closedDate != null) {
                    val closed = resolveLocal(question.closedDate).formatDateOrTime()
                    TooltipText("Closed at $closed: ${question.closedReason}") {
                        Icon(Icons.Default.SpeakerNotesOff, "closed")
                    }
                }
                TextLine(question.title, Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
            }
            SpacedRow(Modifier.fillMaxWidth(), vertical = Alignment.CenterVertically) {
                if (question.acceptedAnswerId != null) Icon(Icons.Default.Check, "Accepted")
                if (question.answerCount > 0) {
                    TextLine(question.answerCount.toString())
                    Icon(Icons.Outlined.QuestionAnswer, "answers")
                }
                if (question.commentCount > 0) {
                    TextLine(question.commentCount.toString())
                    Icon(Icons.Default.Comment, "comments")
                }
                question.tags.forEach { Tag(controller, it) }
                Spacer(Modifier.weight(1f))
                if (controller.isNew(question.questionId)) {
                    Icon(Icons.Default.FiberNew, "new")
                }
                if (question.bountyClosesDate != null) {
                    val bountyDate = resolveLocal(question.bountyClosesDate, ZoneId.systemDefault())
                        .format(DATETIME_PATTERN)
                    TooltipText("until $bountyDate") {
                        TagText("+${question.bountyAmount}", Modifier.width(60.dp))
                    }
                }
                if (question.viewCount > 0) {
                    val viewText = if (question.viewCount < 10000) question.viewCount.toString()
                        else "${question.viewCount/1000}k"
                    TextLine(viewText, Modifier.width(56.dp), align = TextAlign.End)
                    Icon(Icons.Default.Visibility, "views")
                }
                val date = resolveLocal(controller.displayDate(question))
                TooltipText(date.format(DATETIME_PATTERN)) {
                    TextLine(date.formatDateOrTime(), Modifier.width(100.dp), align = TextAlign.End)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tag(controller: QuestionController, tag: String, modifier: Modifier = Modifier) {
    TooltipArea({
        val text = remember { controller.getTagWikiExcerpt(tag).orEmpty() }
        if (text.isNotBlank()) {
            Text(text, tooltipModifier().widthIn(max = 800.dp))
        }
    }) {
        TagText(tag, modifier)
    }
}

@Composable
private fun TagText(tag: String, modifier: Modifier) {
    TextLine(
        tag,
        modifier.background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)).padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        align = TextAlign.End,
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSecondary)
    )
}

fun resolveLocal(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime = Instant
    .ofEpochSecond(timestamp).atZone(ZoneOffset.UTC)
    .withZoneSameInstant(zone)

private val DATE_PATTERN = DateTimeFormatter.ofPattern("d MMM ''yy")
private val TIME_PATTERN = DateTimeFormatter.ofPattern("HH:mm")
private val DATETIME_PATTERN = DateTimeFormatter.ofPattern("d MMMM yyyy '@' HH:mm")

private val ZonedDateTime.isToday get() = toLocalDate() == LocalDate.now()
fun ZonedDateTime.formatDateOrTime(): String = format(if (isToday) TIME_PATTERN else DATE_PATTERN)
