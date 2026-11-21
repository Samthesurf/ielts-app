package com.samuel.ielts.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
import com.samuel.ielts.ui.screens.BrandTeal
import com.samuel.ielts.ui.screens.NeutralBorder
import com.samuel.ielts.ui.screens.SuccessGreen
import com.samuel.ielts.ui.screens.SuccessGreenBg
import com.samuel.ielts.ui.screens.WarningAmber
import com.samuel.ielts.ui.theme.IeltsSpacing

internal val FlameOrange = Color(0xFFEA580C)
internal val FlameOrangeBg = Color(0xFFFFEDD5)

@Composable
fun StreakFlameTile(
    snapshot: StudyProgressSnapshot,
    compact: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FlameOrangeBg.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.ItemGap),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = IeltsIcons.StreakFlame,
                contentDescription = "Day streak",
                tint = if (snapshot.currentStreakDays > 0) FlameOrange else FlameOrange.copy(alpha = 0.65f),
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Text(
                text = "Day streak ${snapshot.currentStreakDays}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StreakFlameCountBadge(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = FlameOrangeBg.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = IeltsSpacing.ItemGap, vertical = IeltsSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = IeltsIcons.StreakFlame,
                contentDescription = "Streak",
                tint = if (streakDays > 0) FlameOrange else FlameOrange.copy(alpha = 0.65f),
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Text(
                text = streakDays.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FlameOrange
            )
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
    title: String = "",
    subtitle: String = "",
    steps: List<NextStepCardData>,
    onSelectStep: (NextStepCardData) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryStep = steps.firstOrNull() ?: return
    NextStepSingleCard(
        step = primaryStep,
        onStart = { onSelectStep(primaryStep) },
        modifier = modifier
    )
}

@Composable
fun NextStepSingleCard(
    step: NextStepCardData,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = skillAccentColor(step.skill)
    val plainTitle = plainIeltsCopy(step.title)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            Icon(
                imageVector = skillIconFor(step.skill),
                contentDescription = step.skill.label,
                tint = accent,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )

            Text(
                text = plainTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onStart,
                modifier = Modifier.height(IeltsSpacing.TouchTarget)
            ) {
                Text("Start")
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
