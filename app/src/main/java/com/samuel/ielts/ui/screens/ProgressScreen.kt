package com.samuel.ielts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.BookProgressSummary
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.IeltsSkill
import com.samuel.ielts.model.NextStepCardData
import com.samuel.ielts.model.QuestionTypeAccuracy
import com.samuel.ielts.model.QuestionTypeCategory
import com.samuel.ielts.model.SkillProgress
import com.samuel.ielts.model.StudyProgressSnapshot
import com.samuel.ielts.model.TestProgressRecord
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.components.NextStepSingleCard
import com.samuel.ielts.ui.components.StreakFlameCountBadge
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
    val bandBySkill = rememberSkillBandMap(skillProgress)

    var showDetails by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(IeltsSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.SectionGap)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progress",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                StreakFlameCountBadge(streakDays = streakSnapshot.currentStreakDays)
            }
        }

        item {
            OverallBandRingCard(
                overallBand = overallBand,
                targetBand = targetBand,
                onStartPractice = onStartPractice
            )
        }

        item {
            FourSkillMiniRingsCard(
                bandBySkill = bandBySkill,
                targetBand = targetBand
            )
        }

        if (weakestType != null) {
            item {
                WeakestTypeChip(
                    weakestType = weakestType,
                    onClick = onOpenQuiz
                )
            }
        }

        val topNextStep = nextSteps.firstOrNull()
        if (topNextStep != null) {
            item {
                NextStepSingleCard(
                    step = topNextStep,
                    onStart = { onSelectNextStep(topNextStep) }
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NeutralBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDetails = !showDetails }
                        .padding(IeltsSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (showDetails) IeltsIcons.ExpandLess else IeltsIcons.ExpandMore,
                            contentDescription = if (showDetails) "Collapse Details" else "Expand Details",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IeltsSpacing.ActionIcon)
                        )
                    }

                    AnimatedVisibility(visible = showDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)) {
                            HorizontalDivider(color = NeutralBorder)

                            DetailVisualSection(title = "Writing Criteria") {
                                HorizontalBarsContent(
                                    items = writingCriteria,
                                    barColor = BrandTeal,
                                    ctaLabel = "Open Writing",
                                    onCtaClick = onOpenWriting
                                )
                            }

                            HorizontalDivider(color = NeutralBorder)

                            DetailVisualSection(title = "Question Types") {
                                QuestionTypeAccuracyContent(
                                    stats = questionTypeStats,
                                    weakestType = weakestType,
                                    onStartQuiz = onOpenQuiz
                                )
                            }

                            if (bookSummaries.isNotEmpty()) {
                                HorizontalDivider(color = NeutralBorder)
                                DetailVisualSection(title = "Books") {
                                    BooksBarsContent(bookSummaries = bookSummaries)
                                }
                            }

                            HorizontalDivider(color = NeutralBorder)

                            DetailVisualSection(title = "Recent Tests") {
                                RecentTestsBarsContent(
                                    records = recentTests,
                                    onOpenLibrary = onOpenLibrary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun rememberSkillBandMap(skillProgress: List<SkillProgress>): Map<IeltsSkill, Double?> {
    val byLabel = skillProgress.associateBy { it.label }
    return mapOf(
        IeltsSkill.LISTENING to byLabel["Listening"]?.band?.takeIf { it > 0.0 },
        IeltsSkill.READING to byLabel["Reading"]?.band?.takeIf { it > 0.0 },
        IeltsSkill.WRITING to byLabel["Writing"]?.band?.takeIf { it > 0.0 },
        IeltsSkill.SPEAKING to byLabel["Speaking"]?.band?.takeIf { it > 0.0 }
    )
}

@Composable
private fun BandProgressRing(
    progress: Float,
    ringSize: Dp,
    strokeWidth: Dp,
    activeColor: Color,
    trackColor: Color = NeutralBorder,
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit
) {
    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            val clamped = progress.coerceIn(0f, 1f)
            if (clamped > 0f) {
                drawArc(
                    color = activeColor,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        centerContent()
    }
}

@Composable
private fun OverallBandRingCard(
    overallBand: Double?,
    targetBand: Double,
    onStartPractice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            if (overallBand == null) {
                BandProgressRing(
                    progress = 0f,
                    ringSize = 136.dp,
                    strokeWidth = 8.dp,
                    activeColor = BrandBlue
                ) {
                    Icon(
                        imageVector = IeltsIcons.Progress,
                        contentDescription = "Not marked yet",
                        tint = LockedSlate,
                        modifier = Modifier.size(IeltsSpacing.HeroIcon)
                    )
                }
                Text(
                    text = "Not marked yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onStartPractice,
                    modifier = Modifier.height(IeltsSpacing.TouchTarget)
                ) {
                    Text("Start")
                }
            } else {
                val progress = (overallBand / targetBand).toFloat().coerceIn(0f, 1f)
                BandProgressRing(
                    progress = progress,
                    ringSize = 148.dp,
                    strokeWidth = 8.dp,
                    activeColor = BrandBlue
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = IeltsScoring.formatBand(overallBand),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                        Text(
                            text = "/ ${IeltsScoring.formatBand(targetBand)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FourSkillMiniRingsCard(
    bandBySkill: Map<IeltsSkill, Double?>,
    targetBand: Double
) {
    val skills = listOf(
        Triple(IeltsSkill.LISTENING, IeltsIcons.Listening, BrandTeal),
        Triple(IeltsSkill.READING, IeltsIcons.Reading, BrandBlue),
        Triple(IeltsSkill.WRITING, IeltsIcons.Writing, WarningAmber),
        Triple(IeltsSkill.SPEAKING, IeltsIcons.Speaking, SuccessGreen)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.Md),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            skills.forEach { (skill, icon, color) ->
                val band = bandBySkill[skill]
                val progress = if (band != null) {
                    (band / targetBand).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = skill.label,
                        tint = if (band != null) color else LockedSlate,
                        modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                    )
                    BandProgressRing(
                        progress = progress,
                        ringSize = 62.dp,
                        strokeWidth = 5.dp,
                        activeColor = color
                    ) {
                        Text(
                            text = band?.let { IeltsScoring.formatBand(it) } ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (band != null) color else LockedSlate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeakestTypeChip(
    weakestType: QuestionTypeAccuracy,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = WarningAmberBg,
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.ItemGap),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = IeltsIcons.Warning,
                contentDescription = "Weakest type",
                tint = WarningAmber,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Text(
                text = "${plainQuestionTypeName(weakestType.category)} ${weakestType.accuracyPercent}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = WarningAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailVisualSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
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
private fun BooksBarsContent(
    bookSummaries: List<BookProgressSummary>
) {
    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
        bookSummaries.forEach { summary ->
            val completionFraction = if (summary.availableSections > 0) {
                (summary.completedSections.toFloat() / summary.availableSections.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = summary.book.code,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (summary.hasSeed) {
                            "${summary.completedSections}/${summary.availableSections}"
                        } else {
                            "Locked"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (summary.hasSeed) BrandTeal else LockedSlate
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
                        text = if (entry.total > 0) "${entry.accuracyPercent}%" else "-",
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
            .height(148.dp),
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
                        .width(28.dp)
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
        raw.endsWith(" L") -> raw.removeSuffix(" L") + "\nL"
        raw.endsWith(" R") -> raw.removeSuffix(" R") + "\nR"
        raw.endsWith(" W") -> raw.removeSuffix(" W") + "\nW"
        raw.endsWith(" S") -> raw.removeSuffix(" S") + "\nS"
        else -> raw
    }
}
