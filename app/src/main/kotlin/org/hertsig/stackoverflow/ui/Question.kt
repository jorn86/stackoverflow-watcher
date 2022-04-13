package org.hertsig.stackoverflow.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.TextLine
import org.hertsig.stackoverflow.dto.Question
import java.awt.Desktop
import java.net.URI
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val PATTERN = DateTimeFormatter.ofPattern("HH:mm:ss")
private val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Question(question: Question, modifier: Modifier = Modifier) {
    AnimatedContent(question.questionId, transitionSpec = {
        expandVertically(spring(
            stiffness = Spring.StiffnessVeryLow,
            visibilityThreshold = IntSize.VisibilityThreshold
        ), Alignment.Top) with shrinkVertically(spring(
            stiffness = Spring.StiffnessVeryLow,
            visibilityThreshold = IntSize.VisibilityThreshold
        ), Alignment.Top)
    }) {
        Row(modifier.height(50.dp).clickable { desktop?.browse(URI(question.url)) },
            Arrangement.spacedBy(16.dp),
            Alignment.CenterVertically
        ) {
            Row(
                Modifier.background(MaterialTheme.colors.secondary, RoundedCornerShape(16.dp)).size(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextLine(question.score.toString(), Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
            Column {
                val closed = if (question.closedReason != null) "[CLOSED] " else ""
                TextLine("$closed${question.title}", Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
                    if (question.acceptedAnswerId != null) Icon(Icons.Default.Check, "Accepted")
                    if (question.answerCount > 0) Text("${question.answerCount} answer(s)")
                    question.tags.forEach { Tag(it) }
                    Spacer(Modifier.weight(1f))
                    val localDate = question.parsedDate.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
                    TextLine(localDate.format(PATTERN))
                }
            }
        }
    }
}

@Composable
private fun Tag(tag: String) {
    Text(tag,
        Modifier.background(MaterialTheme.colors.secondary, RoundedCornerShape(4.dp)).padding(4.dp),
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.onSecondary)
    )
}
