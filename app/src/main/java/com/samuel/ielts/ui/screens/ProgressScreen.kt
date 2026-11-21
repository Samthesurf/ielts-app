package com.samuel.ielts.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.BookProgressSummary
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.QuestionTypeAccuracy
import com.samuel.ielts.model.SkillProgress
import com.samuel.ielts.model.TestProgressRecord

@Composable
fun ProgressScreen(
    skillProgress: List<SkillProgress>,
    recentTests: List<TestProgressRecord>,
    writingCriteria: List<SkillProgress>,
    bookSummaries: List<BookProgressSummary> = emptyList(),
    questionTypeStats: List<QuestionTypeAccuracy> = emptyList(),
    targetBand: Double = 7.5,
    modifier: Modifier = Modifier
) {
    val activeSkillBands = skillProgress.filter { it.band > 0.0 }
    val overallBand = IeltsScoring.roundToHalfBand(
        if (activeSkillBands.isEmpty()) 0.0 else activeSkillBands.map { it.band }.average()
    )
    val weakestType = IeltsScoring.findWeakestQuestionType(questionTypeStats)
    val totalCompleted = bookSummaries.sumOf { it.completedSections }
    val totalAvailable = bookSummaries.sumOf { it.availableSections }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Your Progress",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Aggregated across Books C20, C21, and C19 with weakest question-type focus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OverallProgressSummaryCard(
                overallBand = overallBand,
                targetBand = targetBand,
                completedSections = totalCompleted,
                availableSections = totalAvailable,
                weakestType = weakestType
            )
        }

        if (bookSummaries.isNotEmpty()) {
            item {
                AllBooksAggregationCard(bookSummaries = bookSummaries)
            }
        }

        item {
            QuestionTypeAccuracyCard(
                stats = questionTypeStats,
                weakestType = weakestType
            )
        }

        item {
            SimpleHorizontalBarsCard(
                title = "Band by Skill (All Books)",
                items = skillProgress,
                barColor = BrandBlue,
                emptyMessage = "Complete a Listening, Reading, Writing, or Speaking test to see skill averages."
            )
        }

        item {
            SimpleVerticalBarsCard(
                title = "Recent Tests (C20, C21, C19)",
                records = recentTests
            )
        }

        item {
            SimpleHorizontalBarsCard(
                title = "Writing Criteria Average",
                items = writingCriteria,
                barColor = BrandTeal,
                emptyMessage = "Submit a Writing Task 1 or Task 2 draft to track TA/TR, CC, LR, and GRA."
            )
        }
    }
}

@Composable
private fun OverallProgressSummaryCard(
    overallBand: Double,
    targetBand: Double,
    completedSections: Int,
    availableSections: Int,
    weakestType: QuestionTypeAccuracy?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BrandBlueSoft.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Current Overall Band",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Target: Band ${IeltsScoring.formatBand(targetBand)} | $completedSections / $availableSections seeded sections done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = IeltsScoring.formatBand(overallBand),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue
                )
            }

            WeakestTypeTagBanner(weakestType = weakestType)
        }
    }
}

@Composable
private fun WeakestTypeTagBanner(
    weakestType: QuestionTypeAccuracy?
) {
    val bgColor = if (weakestType != null) WarningAmberBg else MaterialTheme.colorScheme.surface
    val borderColor = if (weakestType != null) WarningAmber.copy(alpha = 0.4f) else NeutralBorder
    val textColor = if (weakestType != null) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (weakestType != null) {
                    "Weakest type: ${weakestType.category.tag}"
                } else {
                    "Weakest type: Not enough quiz data yet"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            if (weakestType != null) {
                Surface(
                    color = WarningAmber,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${weakestType.accuracyPercent}% (${weakestType.correct}/${weakestType.total})",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AllBooksAggregationCard(
    bookSummaries: List<BookProgressSummary>
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
            Text(
                text = "All Books Summary (C20, C21, C19)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            bookSummaries.forEach { summary ->
                val completionFraction = if (summary.availableSections > 0) {
                    (summary.completedSections.toFloat() / summary.availableSections.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (summary.hasSeed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else LockedSlateBg,
                    border = BorderStroke(1.dp, NeutralBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = summary.book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (!summary.hasSeed) {
                                        "Locked (seed missing)"
                                    } else {
                                        "${summary.completedSections} of ${summary.availableSections} seeded sections completed"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (summary.hasSeed) MaterialTheme.colorScheme.onSurfaceVariant else LockedSlate
                                )
                            }
                            Text(
                                text = summary.averageBand?.let { "Avg ${IeltsScoring.formatBand(it)}" } ?: "--",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (summary.averageBand != null) BrandBlue else LockedSlate
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BookSkillMiniChip("L", summary.listeningBand, Modifier.weight(1f))
                            BookSkillMiniChip("R", summary.readingBand, Modifier.weight(1f))
                            BookSkillMiniChip("W", summary.writingBand, Modifier.weight(1f))
                            BookSkillMiniChip("S", summary.speakingBand, Modifier.weight(1f))
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
                                    .fillMaxWidth(completionFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (summary.hasSeed) BrandTeal else LockedSlate)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookSkillMiniChip(
    code: String,
    band: Double?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (band != null) BrandBlueSoft.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Text(
            text = if (band != null) "$code ${IeltsScoring.formatBand(band)}" else "$code --",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (band != null) BrandBlue else LockedSlate
        )
    }
}

@Composable
private fun QuestionTypeAccuracyCard(
    stats: List<QuestionTypeAccuracy>,
    weakestType: QuestionTypeAccuracy?
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
                    text = "Question Type Accuracy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (weakestType != null) {
                    Surface(
                        color = ErrorRedBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "Weakest: ${weakestType.category.tag}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                    }
                }
            }

            if (stats.all { it.total == 0 }) {
                Text(
                    text = "Check answers in Listening or Reading Quiz to track T/F/NG, Y/N/NG, MCQ, and Fill-Blank accuracy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                stats.forEach { entry ->
                    val isWeakest = weakestType != null && weakestType.category == entry.category
                    val barColor = when {
                        entry.total == 0 -> LockedSlate
                        isWeakest -> WarningAmber
                        else -> BrandTeal
                    }
                    val fraction = entry.accuracyRatio.toFloat().coerceIn(0f, 1f)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry.category.tag,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isWeakest) {
                                    Surface(
                                        color = WarningAmberBg,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Weakest",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (entry.total > 0) {
                                    "${entry.accuracyPercent}% (${entry.correct}/${entry.total})"
                                } else {
                                    "No attempts"
                                },
                                style = MaterialTheme.typography.labelMedium,
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
    }
}

@Composable
private fun SimpleHorizontalBarsCard(
    title: String,
    items: List<SkillProgress>,
    barColor: Color,
    emptyMessage: String
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (items.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    val fraction = (item.band / 9.0).toFloat().coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                text = "${IeltsScoring.formatBand(item.band)} / 9.0",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = barColor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleVerticalBarsCard(
    title: String,
    records: List<TestProgressRecord>
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (records.isEmpty()) {
                Text(
                    text = "No completed tests yet. Submit a quiz, writing draft, or speaking log to chart recent bands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
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
                                    .padding(vertical = 6.dp)
                                    .width(34.dp)
                                    .weight(1f),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BrandBlue)
                                )
                            }
                            Text(
                                text = record.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
