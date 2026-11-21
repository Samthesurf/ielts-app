package com.samuel.ielts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.BookProgressSummary
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.NextStepCardData
import com.samuel.ielts.model.QuestionTypeAccuracy
import com.samuel.ielts.model.QuestionTypeCategory
import com.samuel.ielts.model.SkillProgress
import com.samuel.ielts.model.StudyProgressSnapshot
import com.samuel.ielts.model.TestProgressRecord
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.components.NextStepCardsSection
import com.samuel.ielts.ui.components.StreakFlameTile
import com.samuel.ielts.ui.theme.IeltsSpacing

internal fun plainQuestionTypeName(category: QuestionTypeCategory): String = when (category) {
    QuestionTypeCategory.TFNG -> "True / False / Not Given"
    QuestionTypeCategory.YNNG -> "Yes / No / Not Given"
    QuestionTypeCategory.MCQ -> "Multiple Choice"
    QuestionTypeCategory.FILL_BLANK -> "Fill in the Blank"
}

@Composable
fun ProgressScreen(
    skillProgress: List<SkillProgress>,
    recentTests: List<TestProgressRecord>,
    writingCriteria: List<SkillProgress>,
    bookSummaries: List<BookProgressSummary> = emptyList(),
    questionTypeStats: List<QuestionTypeAccuracy> = emptyList(),
    targetBand: Double = 7.5,
    streakSnapshot: StudyProgressSnapshot = StudyProgressSnapshot(),
    nextSteps: List<NextStepCardData> = emptyList(),
    onSelectNextStep: (NextStepCardData) -> Unit = {},
    onStartPractice: () -> Unit = {},
    onOpenQuiz: () -> Unit = {},
    onOpenWriting: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeSkillBands = skillProgress.filter { it.band > 0.0 }
    val overallBand: Double? = if (activeSkillBands.isEmpty()) {
        null
    } else {
        IeltsScoring.roundToHalfBand(activeSkillBands.map { it.band }.average())
    }
    val weakestType = IeltsScoring.findWeakestQuestionType(questionTypeStats)
    val totalCompleted = bookSummaries.sumOf { it.completedSections }
    val totalAvailable = bookSummaries.sumOf { it.availableSections }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(IeltsSpacing.CardPadding),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.SectionGap)
    ) {
        item {
            Text(
                text = "Progress",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            OverallProgressSummaryCard(
                overallBand = overallBand,
                bestOverallBand = streakSnapshot.bestOverallBand,
                targetBand = targetBand,
                completedSections = totalCompleted,
                availableSections = totalAvailable,
                weakestType = weakestType,
                onStartPractice = onStartPractice
            )
        }

        item {
            StreakFlameTile(
                snapshot = streakSnapshot,
                compact = true
            )
        }

        if (nextSteps.isNotEmpty()) {
            item {
                NextStepCardsSection(
                    title = "Next Step",
                    steps = nextSteps,
                    onSelectStep = onSelectNextStep
                )
            }
        }

        item {
            CollapsibleProgressCard(
                key = "skills",
                title = "Skills",
                summary = if (skillProgress.isEmpty()) {
                    "Not marked yet"
                } else {
                    "${skillProgress.size} skills marked"
                }
            ) {
                HorizontalBarsContent(
                    items = skillProgress,
                    barColor = BrandBlue,
                    ctaLabel = "Start Quiz",
                    onCtaClick = onOpenQuiz
                )
            }
        }

        item {
            val hasQuestionData = questionTypeStats.any { it.total > 0 }
            CollapsibleProgressCard(
                key = "question_types",
                title = "Question Types",
                summary = when {
                    !hasQuestionData -> "Not marked yet"
                    weakestType != null -> "Focus: ${plainQuestionTypeName(weakestType.category)}"
                    else -> "Accuracy tracked"
                }
            ) {
                QuestionTypeAccuracyContent(
                    stats = questionTypeStats,
                    weakestType = weakestType,
                    onStartQuiz = onOpenQuiz
                )
            }
        }

        item {
            CollapsibleProgressCard(
                key = "writing_criteria",
                title = "Writing Criteria",
                summary = if (writingCriteria.isEmpty()) {
                    "Not marked yet"
                } else {
                    "4 criteria tracked"
                }
            ) {
                HorizontalBarsContent(
                    items = writingCriteria,
                    barColor = BrandTeal,
                    ctaLabel = "Open Writing",
                    onCtaClick = onOpenWriting
                )
            }
        }

        if (bookSummaries.isNotEmpty()) {
            item {
                CollapsibleProgressCard(
                    key = "books",
                    title = "Books",
                    summary = if (totalCompleted == 0) {
                        "Not marked yet"
                    } else {
                        "$totalCompleted of $totalAvailable done"
                    }
                ) {
                    AllBooksAggregationContent(bookSummaries = bookSummaries)
                }
            }
        }

        item {
            CollapsibleProgressCard(
                key = "recent_tests",
                title = "Recent Tests",
                summary = if (recentTests.isEmpty()) {
                    "Not marked yet"
                } else {
                    "${recentTests.size} recorded"
                }
            ) {
                RecentTestsBarsContent(
                    records = recentTests,
                    onOpenLibrary = onOpenLibrary
                )
            }
        }
    }
}

@Composable
private fun OverallProgressSummaryCard(
    overallBand: Double?,
    bestOverallBand: Double,
    targetBand: Double,
    completedSections: Int,
    availableSections: Int,
    weakestType: QuestionTypeAccuracy?,
    onStartPractice: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = BrandBlueSoft.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
        ) {
            if (overallBand == null) {
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                    Text(
                        text = "Overall Band",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Not marked yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Target Band ${IeltsScoring.formatBand(targetBand)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onStartPractice,
                    modifier = Modifier.height(IeltsSpacing.TouchTarget)
                ) {
                    Text("Start Practice")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                    ) {
                        Text(
                            text = "Overall Band",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target ${IeltsScoring.formatBand(targetBand)} | $completedSections of $availableSections done",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = IeltsScoring.formatBand(overallBand),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue
                    )
                }

                if (bestOverallBand > 0.0 || weakestType != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bestOverallBand > 0.0) {
                            Surface(
                                color = Color.White,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, NeutralBorder)
                            ) {
                                Text(
                                    text = "Best ${IeltsScoring.formatBand(bestOverallBand)}",
                                    modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandTeal
                                )
                            }
                        }
                        if (weakestType != null) {
                            Surface(
                                color = WarningAmberBg,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "Focus: ${plainQuestionTypeName(weakestType.category)} (${weakestType.accuracyPercent}%)",
                                    modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WarningAmber,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleProgressCard(
    key: String,
    title: String,
    summary: String,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) IeltsIcons.ExpandLess else IeltsIcons.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)) {
                    HorizontalDivider(color = NeutralBorder)
                    content()
                }
            }
        }
    }
}

@Composable
private fun EmptyMetricPrompt(
    ctaLabel: String,
    onCtaClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Not marked yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onCtaClick) {
            Text(ctaLabel)
        }
    }
}

@Composable
private fun AllBooksAggregationContent(
    bookSummaries: List<BookProgressSummary>
) {
    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
        bookSummaries.forEach { summary ->
            val completionFraction = if (summary.availableSections > 0) {
                (summary.completedSections.toFloat() / summary.availableSections.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = if (summary.hasSeed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else LockedSlateBg,
                border = BorderStroke(1.dp, NeutralBorder)
            ) {
                Column(
                    modifier = Modifier.padding(IeltsSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = plainBookName(summary.book),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (!summary.hasSeed) {
                                    "Unavailable"
                                } else {
                                    "${summary.completedSections} of ${summary.availableSections} done"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (summary.hasSeed) MaterialTheme.colorScheme.onSurfaceVariant else LockedSlate
                            )
                        }
                        Text(
                            text = summary.averageBand?.let { "Band ${IeltsScoring.formatBand(it)}" } ?: "Not marked yet",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (summary.averageBand != null) BrandBlue else LockedSlate
                        )
                    }

                    if (summary.averageBand != null) {
                        val skillPairs = listOf(
                            "Listening" to summary.listeningBand,
                            "Reading" to summary.readingBand,
                            "Writing" to summary.writingBand,
                            "Speaking" to summary.speakingBand
                        ).filter { it.second != null }

                        Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                            skillPairs.forEach { (name, band) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = IeltsScoring.formatBand(band!!),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandBlue
                                    )
                                }
                            }
                        }
                    }

                    if (summary.hasSeed) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(completionFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BrandTeal)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionTypeAccuracyContent(
    stats: List<QuestionTypeAccuracy>,
    weakestType: QuestionTypeAccuracy?,
    onStartQuiz: () -> Unit
) {
    if (stats.all { it.total == 0 }) {
        EmptyMetricPrompt(
            ctaLabel = "Start Quiz",
            onCtaClick = onStartQuiz
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
        stats.forEach { entry ->
            val isWeakest = weakestType != null && weakestType.category == entry.category
            val barColor = when {
                entry.total == 0 -> LockedSlate
                isWeakest -> WarningAmber
                else -> BrandTeal
            }
            val fraction = entry.accuracyRatio.toFloat().coerceIn(0f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = plainQuestionTypeName(entry.category),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (entry.total > 0) {
                            "${entry.accuracyPercent}% (${entry.correct}/${entry.total})"
                        } else {
                            "Not marked yet"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = barColor
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalBarsContent(
    items: List<SkillProgress>,
    barColor: Color,
    ctaLabel: String,
    onCtaClick: () -> Unit
) {
    val markedItems = items.filter { it.band > 0.0 }
    if (markedItems.isEmpty()) {
        EmptyMetricPrompt(
            ctaLabel = ctaLabel,
            onCtaClick = onCtaClick
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
        markedItems.forEach { item ->
            val fraction = (item.band / 9.0).toFloat().coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = IeltsScoring.formatBand(item.band),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTestsBarsContent(
    records: List<TestProgressRecord>,
    onOpenLibrary: () -> Unit
) {
    if (records.isEmpty()) {
        EmptyMetricPrompt(
            ctaLabel = "Open Library",
            onCtaClick = onOpenLibrary
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        records.takeLast(6).forEach { record ->
            val fraction = (record.band / 9.0).toFloat().coerceIn(0.1f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    text = IeltsScoring.formatBand(record.band),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue
                )
                Box(
                    modifier = Modifier
                        .padding(vertical = IeltsSpacing.Sm)
                        .width(32.dp)
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(MaterialTheme.shapes.small)
                            .background(BrandBlue)
                    )
                }
                Text(
                    text = formatRecordLabel(record.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatRecordLabel(raw: String): String {
    return when {
        raw.endsWith(" L") -> raw.removeSuffix(" L") + "\nList"
        raw.endsWith(" R") -> raw.removeSuffix(" R") + "\nRead"
        raw.endsWith(" W") -> raw.removeSuffix(" W") + "\nWrite"
        raw.endsWith(" S") -> raw.removeSuffix(" S") + "\nSpeak"
        else -> raw
    }
}
