package com.samuel.ielts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.CriterionScore
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.InlineComment
import com.samuel.ielts.model.WritingTaskData
import com.samuel.ielts.model.WritingTaskType

@Composable
fun WritingScreen(
    testLabel: String,
    task1Data: WritingTaskData,
    task2Data: WritingTaskData,
    onOverallBandUpdated: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTask by rememberSaveable { mutableStateOf(WritingTaskType.TASK_1) }
    var task1Draft by rememberSaveable(task1Data.prompt) { mutableStateOf(task1Data.initialDraft) }
    var task2Draft by rememberSaveable(task2Data.prompt) { mutableStateOf(task2Data.initialDraft) }
    var showFeedback by rememberSaveable { mutableStateOf(true) }
    var showBand75Rewrite by rememberSaveable { mutableStateOf(false) }

    val currentTaskData = if (activeTask == WritingTaskType.TASK_1) task1Data else task2Data
    val currentDraft = if (activeTask == WritingTaskType.TASK_1) task1Draft else task2Draft
    val wordCount = IeltsScoring.countWords(currentDraft)
    val minWords = activeTask.minWords
    val underWordWarning = wordCount < minWords

    val task1Band = IeltsScoring.calculateTaskBand(task1Data.scores)
    val task2Band = IeltsScoring.calculateTaskBand(task2Data.scores)
    val overallWritingBand = IeltsScoring.calculateWritingOverall(task1Band, task2Band)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$testLabel Writing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Switch between Task 1 (150 words) and Task 2 (250 words).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OverallWritingBandCard(
                task1Band = task1Band,
                task2Band = task2Band,
                overallBand = overallWritingBand
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WritingTaskType.entries.forEach { taskType ->
                    FilterChip(
                        selected = activeTask == taskType,
                        onClick = { activeTask = taskType },
                        label = {
                            Text("${taskType.shortLabel} (${taskType.minWords}+ words)")
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NeutralBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = currentTaskData.taskType.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentTaskData.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = currentDraft,
                        onValueChange = { updated ->
                            if (activeTask == WritingTaskType.TASK_1) {
                                task1Draft = updated
                            } else {
                                task2Draft = updated
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        label = { Text("Your ${currentTaskData.taskType.shortLabel} draft") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Words: $wordCount / $minWords target",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (underWordWarning) WarningAmber else SuccessGreen
                        )
                        Button(
                            onClick = {
                                showFeedback = true
                                onOverallBandUpdated(overallWritingBand)
                            }
                        ) {
                            Text("Review Draft")
                        }
                    }

                    if (underWordWarning) {
                        WordCountWarningBanner(
                            wordCount = wordCount,
                            minWords = minWords,
                            firstCriterionName = activeTask.firstCriterionName
                        )
                    } else {
                        Surface(
                            color = SuccessGreenBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Word count target met ($wordCount words).",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }

        if (showFeedback) {
            item {
                FourScoreBarsCard(
                    taskType = currentTaskData.taskType,
                    scores = currentTaskData.scores,
                    taskBand = IeltsScoring.calculateTaskBand(currentTaskData.scores)
                )
            }

            item {
                InlineCommentsCard(
                    draftText = currentDraft,
                    comments = currentTaskData.inlineComments
                )
            }

            item {
                Band75RewriteCollapsibleCard(
                    expanded = showBand75Rewrite,
                    rewriteText = currentTaskData.band75Rewrite,
                    onToggle = { showBand75Rewrite = !showBand75Rewrite }
                )
            }

            item {
                Band8ModelCard(
                    modelText = currentTaskData.band8Model
                )
            }
        }
    }
}

@Composable
private fun OverallWritingBandCard(
    task1Band: Double,
    task2Band: Double,
    overallBand: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BrandBlueSoft.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Overall Writing Band",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Formula: round((T1 + T2 x 2) / 3 to 0.5)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = IeltsScoring.formatBand(overallBand),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Task 1: ${IeltsScoring.formatBand(task1Band)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Task 2 (2x weight): ${IeltsScoring.formatBand(task2Band)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun WordCountWarningBanner(
    wordCount: Int,
    minWords: Int,
    firstCriterionName: String
) {
    Surface(
        color = WarningAmberBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = WarningAmber
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Word count warning ($wordCount / $minWords words)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
                Text(
                    text = "You can still review and submit this draft. Writing under $minWords words may lower your $firstCriterionName score.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber
                )
            }
        }
    }
}

@Composable
private fun FourScoreBarsCard(
    taskType: WritingTaskType,
    scores: List<CriterionScore>,
    taskBand: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${taskType.shortLabel} Scores (${taskType.firstCriterionCode}, CC, LR, GRA)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Band ${IeltsScoring.formatBand(taskBand)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            scores.forEach { score ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${score.criterion.code}: ${score.criterion.fullName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = IeltsScoring.formatBand(score.band),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (score.band / 9.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = score.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineCommentsCard(
    draftText: String,
    comments: List<InlineComment>
) {
    val annotatedDraft = buildAnnotatedDraft(draftText, comments)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Inline Comments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = annotatedDraft,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider(color = NeutralBorder)

            comments.forEach { comment ->
                Surface(
                    color = WarningAmberBg.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = WarningAmber,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "#${comment.id} ${comment.criterion.code}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "\"${comment.excerpt}\"",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = comment.note,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Fix: ${comment.suggestion}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTeal
                        )
                    }
                }
            }
        }
    }
}

private fun buildAnnotatedDraft(
    draftText: String,
    comments: List<InlineComment>
): AnnotatedString {
    if (draftText.isBlank()) {
        return AnnotatedString("No draft text yet.")
    }

    data class MatchSpan(val start: Int, val end: Int, val commentId: Int)

    val matches = mutableListOf<MatchSpan>()
    comments.forEach { comment ->
        if (comment.excerpt.isNotBlank()) {
            val start = draftText.indexOf(comment.excerpt, ignoreCase = true)
            if (start >= 0) {
                val end = start + comment.excerpt.length
                val overlaps = matches.any { start < it.end && end > it.start }
                if (!overlaps) {
                    matches.add(MatchSpan(start, end, comment.id))
                }
            }
        }
    }

    matches.sortBy { it.start }

    return buildAnnotatedString {
        var cursor = 0
        matches.forEach { match ->
            if (match.start > cursor) {
                append(draftText.substring(cursor, match.start))
            }
            withStyle(
                SpanStyle(
                    background = WarningAmberBg,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(draftText.substring(match.start, match.end))
            }
            withStyle(
                SpanStyle(
                    color = WarningAmber,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(" [#${match.commentId}]")
            }
            cursor = match.end
        }
        if (cursor < draftText.length) {
            append(draftText.substring(cursor))
        }
    }
}

@Composable
private fun Band75RewriteCollapsibleCard(
    expanded: Boolean,
    rewriteText: String,
    onToggle: () -> Unit
) {
    val wordCount = IeltsScoring.countWords(rewriteText)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Band 7.5 Rewrite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (expanded) {
                            "Tap to hide ($wordCount words)"
                        } else {
                            "Tap to open improved version ($wordCount words)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = NeutralBorder)
                    Text(
                        text = rewriteText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun Band8ModelCard(
    modelText: String
) {
    val wordCount = IeltsScoring.countWords(modelText)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = BrandTealSoft,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Band 8",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandTeal
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Band 8 Model",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$wordCount words",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = NeutralBorder)

            Text(
                text = modelText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
