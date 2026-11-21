package com.samuel.ielts.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val bestWord = if (snapshot.longestStreakDays == 1) "day" else "days"
    val statusText = when {
        snapshot.studiedToday -> "Active today. Keep momentum going."
        snapshot.currentStreakDays > 0 -> "Complete one drill today to keep your streak."
        else -> "Finish any quiz, essay mark, or speaking log to start a streak."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FlameOrangeBg.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.White,
                border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.25f)),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = IeltsIcons.Whatshot,
                        contentDescription = "Daily study streak flame",
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
                        text = "${snapshot.currentStreakDays} $dayWord streak",
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.White
                    ) {
                        Text(
                            text = "Best ${snapshot.longestStreakDays} $bestWord",
                            modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FlameOrange
                        )
                    }
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact && snapshot.bestOverallBand > 0.0) {
                    Text(
                        text = "Personal best overall band: ${IeltsScoring.formatBand(snapshot.bestOverallBand)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandBlue
                    )
                }
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
                imageVector = IeltsIcons.Trophy,
                contentDescription = "New best band trophy",
                tint = SuccessGreen,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "New Best Overall Band: ${IeltsScoring.formatBand(bestBand)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
                Text(
                    text = "Personal record updated across your study history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(IeltsSpacing.TouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss celebration banner",
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
    subtitle: String,
    steps: List<NextStepCardData>,
    onSelectStep: (NextStepCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        steps.forEach { step ->
            NextStepCard(
                step = step,
                onClick = { onSelectStep(step) }
            )
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (step.isTopPriority) accentSoft.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (step.isTopPriority) 1.5.dp else 1.dp,
            color = if (step.isTopPriority) accent.copy(alpha = 0.45f) else NeutralBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = accentSoft,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "${step.skill.label} next step",
                        tint = accent,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step.isTopPriority) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = WarningAmberBg
                        ) {
                            Text(
                                text = "Top Priority",
                                modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber
                            )
                        }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = step.reasonTag,
                            modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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
                        imageVector = IeltsIcons.ArrowForward,
                        contentDescription = step.actionLabel,
                        tint = accent,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                }
            }
        }
    }
}

private fun skillIconFor(skill: IeltsSkill): ImageVector = when (skill) {
    IeltsSkill.LISTENING -> IeltsIcons.Headphones
    IeltsSkill.READING -> IeltsIcons.MenuBook
    IeltsSkill.WRITING -> Icons.Default.Edit
    IeltsSkill.SPEAKING -> IeltsIcons.Mic
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
