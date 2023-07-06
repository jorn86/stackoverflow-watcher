package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.SpacedColumn
import org.hertsig.compose.component.SpacedRow
import org.hertsig.compose.component.TextLine
import org.hertsig.compose.component.TooltipText
import org.hertsig.stackoverflow.dto.api.Question
import java.awt.Desktop
import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter

private val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

@Composable
fun Question(question: Question, modifier: Modifier = Modifier) {
    SpacedRow(modifier.clickable { desktop?.browse(URI(question.url)) },
        16.dp, vertical = Alignment.CenterVertically
    ) {
        Row(
            Modifier.background(MaterialTheme.colors.primary, RoundedCornerShape(16.dp)).size(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextLine(
                question.score.toString(),
                Modifier.fillMaxWidth(),
                MaterialTheme.colors.onPrimary,
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
                question.tags.forEach { Tag(it) }
                Spacer(Modifier.weight(1f))
                if (question.bountyAmount != null && question.bountyClosesDate != null) {
                    val bountyDate = resolveLocal(question.bountyClosesDate, ZoneId.systemDefault())
                        .format(DATETIME_PATTERN)
                    TooltipText("until $bountyDate") {
                        Tag("+${question.bountyAmount}", Modifier.width(60.dp))
                    }
                }
                if (question.viewCount > 0) {
                    TextLine(question.viewCount.toString(), Modifier.width(56.dp), align = TextAlign.End)
                    Icon(Icons.Default.Visibility, "views")
                }
                val date = remember(question.creationDate) { resolveLocal(question.creationDate) }
                TooltipText(date.format(DATETIME_PATTERN)) {
                    TextLine(date.formatDateOrTime(), Modifier.width(100.dp), align = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun Tag(tag: String, modifier: Modifier = Modifier) {
    Text(tag,
        modifier.background(MaterialTheme.colors.primaryVariant, RoundedCornerShape(4.dp)).padding(4.dp),
        textAlign = TextAlign.End,
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.onPrimary)
    )
}

private fun resolveLocal(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime = Instant
    .ofEpochSecond(timestamp).atZone(ZoneOffset.UTC)
    .withZoneSameInstant(zone)

private val DATE_PATTERN = DateTimeFormatter.ofPattern("d MMM ''yy")
private val TIME_PATTERN = DateTimeFormatter.ofPattern("HH:mm")
private val DATETIME_PATTERN = DateTimeFormatter.ofPattern("d MMMM yyyy '@' HH:mm")

private val ZonedDateTime.isToday get() = toLocalDate() == LocalDate.now()
private fun ZonedDateTime.formatDateOrTime() = format(if (isToday) TIME_PATTERN else DATE_PATTERN)
