package com.samuel.ielts.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.IeltsSkill
import com.samuel.ielts.model.NextStepCardData
import com.samuel.ielts.model.StudyProgressSnapshot
import com.samuel.ielts.ui.screens.BrandBlue
import com.samuel.ielts.ui.screens.BrandBlueSoft
import com.samuel.ielts.ui.screens.BrandTeal
import com.samuel.ielts.ui.screens.BrandTealSoft
import com.samuel.ielts.ui.screens.NeutralBorder
import com.samuel.ielts.ui.screens.SuccessGreen
import com.samuel.ielts.ui.screens.SuccessGreenBg
import com.samuel.ielts.ui.screens.WarningAmber
import com.samuel.ielts.ui.screens.WarningAmberBg
import com.samuel.ielts.ui.theme.IeltsSpacing

private val FlameOrange = Color(0xFFEA580C)
private val FlameOrangeBg = Color(0xFFFFEDD5)

@Composable
fun StreakFlameTile(
    snapshot: StudyProgressSnapshot,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dayWord = if (snapshot.currentStreakDays == 1) "day" else "days"
    val titleText = if (snapshot.currentStreakDays > 0) {
        "${snapshot.currentStreakDays} $dayWord streak"
    } else {
        "Study streak"
    }
    val helperText = when {
        !compact && snapshot.bestOverallBand > 0.0 ->
            "Best overall band: ${IeltsScoring.formatBand(snapshot.bestOverallBand)}"
        snapshot.studiedToday -> "Studied today."
        snapshot.currentStreakDays > 0 -> "Complete one practice today to keep it."
        else -> "Finish any practice to start a streak."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FlameOrangeBg.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.White,
                border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.22f)),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = IeltsIcons.StreakFlame,
                        contentDescription = "Study streak",
                        tint = if (snapshot.currentStreakDays > 0) FlameOrange else FlameOrange.copy(alpha = 0.55f),
                        modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (snapshot.longestStreakDays > 0) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = Color.White
                        ) {
                            Text(
                                text = "Best ${snapshot.longestStreakDays}d",
                                modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FlameOrange
                            )
                        }
                    }
                }
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NewBestBandBanner(
    bestBand: Double,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = SuccessGreenBg,
        border = BorderStroke(1.5.dp, SuccessGreen.copy(alpha = 0.45f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
        ) {
            Icon(
                imageVector = IeltsIcons.TrophyBestBand,
                contentDescription = "New best band",
                tint = SuccessGreen,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Text(
                text = "New best band: ${IeltsScoring.formatBand(bestBand)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(IeltsSpacing.TouchTarget)
            ) {
                Icon(
                    imageVector = IeltsIcons.Close,
                    contentDescription = "Dismiss",
                    tint = SuccessGreen,
                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                )
            }
        }
    }
}

@Composable
fun NextStepCardsSection(
    title: String,
    subtitle: String = "",
    steps: List<NextStepCardData>,
    onSelectStep: (NextStepCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty()) return

    var showMore by rememberSaveable(title) { mutableStateOf(false) }
    val primaryStep = steps.first()
    val extraSteps = steps.drop(1)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (extraSteps.isNotEmpty()) {
                TextButton(onClick = { showMore = !showMore }) {
                    Text(if (showMore) "Show less" else "More (${extraSteps.size})")
                }
            }
        }

        NextStepCard(
            step = primaryStep,
            onClick = { onSelectStep(primaryStep) }
        )

        if (extraSteps.isNotEmpty()) {
            AnimatedVisibility(visible = showMore) {
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
                    extraSteps.forEach { step ->
                        NextStepCard(
                            step = step,
                            onClick = { onSelectStep(step) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextStepCard(
    step: NextStepCardData,
    onClick: () -> Unit
) {
    val accent = skillAccentColor(step.skill)
    val accentSoft = skillSoftColor(step.skill)
    val icon = skillIconFor(step.skill)
    val plainTitle = plainIeltsCopy(step.title)
    val plainHelper = plainIeltsCopy(step.reasonTag)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (step.isTopPriority) accentSoft.copy(alpha = 0.36f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (step.isTopPriority) accent.copy(alpha = 0.35f) else NeutralBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = accentSoft,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = step.skill.label,
                        tint = accent,
                        modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
            ) {
                Text(
                    text = plainTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = plainHelper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.sizeIn(
                    minWidth = IeltsSpacing.TouchTarget,
                    minHeight = IeltsSpacing.TouchTarget
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = IeltsIcons.NextStepArrow,
                        contentDescription = step.actionLabel,
                        tint = accent,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                }
            }
        }
    }
}

internal fun plainIeltsCopy(raw: String): String {
    return raw
        .replace("T/F/NG", "True / False / Not Given")
        .replace("Y/N/NG", "Yes / No / Not Given")
        .replace("MCQ", "Multiple Choice")
        .replace("Fill-Blank", "Fill in the Blank")
        .replace("TA / TR", "Task Achievement")
        .replace("TR, CC, LR, and GRA", "all four writing criteria")
        .replace("Avg Band", "Average band")
        .replace("Starter Drill", "Practice")
        .replace("Elimination Drill", "Practice")
        .replace("Word Drill", "Practice")
        .replace("Drill", "Practice")
        .replace('\u2014', '-')
        .replace('\u2013', '-')
        .trim()
}

private fun skillIconFor(skill: IeltsSkill): ImageVector = when (skill) {
    IeltsSkill.LISTENING -> IeltsIcons.Listening
    IeltsSkill.READING -> IeltsIcons.Reading
    IeltsSkill.WRITING -> IeltsIcons.Writing
    IeltsSkill.SPEAKING -> IeltsIcons.Speaking
}

private fun skillAccentColor(skill: IeltsSkill): Color = when (skill) {
    IeltsSkill.LISTENING -> BrandTeal
    IeltsSkill.READING -> BrandBlue
    IeltsSkill.WRITING -> WarningAmber
    IeltsSkill.SPEAKING -> SuccessGreen
}

private fun skillSoftColor(skill: IeltsSkill): Color = when (skill) {
    IeltsSkill.LISTENING -> BrandTealSoft
    IeltsSkill.READING -> BrandBlueSoft
    IeltsSkill.WRITING -> WarningAmberBg
    IeltsSkill.SPEAKING -> SuccessGreenBg
}
