package com.samuel.ielts.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.SpeakingPromptData
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.theme.IeltsSpacing

private val SpeakingSelfBandOptions = listOf(5.5, 6.0, 6.5, 7.0, 7.5, 8.0)

@Composable
fun SpeakingPracticeScreen(
    testLabel: String,
    speakingData: SpeakingPromptData,
    initialBand: Double?,
    onSaveBand: (Double) -> Unit,
    onBack: () -> Unit,
    availableTests: List<Pair<String, String>> = emptyList(),
    selectedTestId: String = speakingData.testId,
    onSelectTest: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var notes by rememberSaveable(speakingData.testId) { mutableStateOf("") }
    var selectedBand by rememberSaveable(speakingData.testId) {
        mutableDoubleStateOf(initialBand ?: 6.5)
    }
    var savedBandNotice by rememberSaveable(speakingData.testId) {
        mutableStateOf<Double?>(null)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(IeltsSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = BrandTealSoft,
                    modifier = Modifier.size(IeltsSpacing.TouchTarget)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = IeltsIcons.Speaking,
                            contentDescription = "Speaking practice microphone",
                            tint = BrandTeal,
                            modifier = Modifier.size(IeltsSpacing.ActionIcon)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                ) {
                    Text(
                        text = "$testLabel Speaking",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Practice Parts 1, 2, and 3 and log your self-rated band.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (availableTests.size > 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    availableTests.forEach { (id, label) ->
                        FilterChip(
                            selected = id == selectedTestId,
                            onClick = { onSelectTest(id) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        val noticeBand = savedBandNotice
        if (noticeBand != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = SuccessGreenBg,
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(IeltsSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.Completed,
                            contentDescription = "Speaking band saved",
                            tint = SuccessGreen,
                            modifier = Modifier.size(IeltsSpacing.ActionIcon)
                        )
                        Text(
                            text = "Saved $testLabel Speaking Band ${IeltsScoring.formatBand(noticeBand)} to Progress.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessGreen
                        )
                    }
                }
            }
        }

        item {
            SpeakingPartCard(
                partBadge = "Part 1",
                partIcon = IeltsIcons.SpeakingPart1,
                title = "Introduction and Interview",
                body = speakingData.part1
            )
        }

        item {
            SpeakingPartCard(
                partBadge = "Part 2",
                partIcon = IeltsIcons.SpeakingPart2,
                title = "Individual Long Turn (Cue Card)",
                body = speakingData.part2
            )
        }

        item {
            SpeakingPartCard(
                partBadge = "Part 3",
                partIcon = IeltsIcons.SpeakingPart3,
                title = "Two-Way Discussion",
                body = speakingData.part3
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NeutralBorder)
            ) {
                Column(
                    modifier = Modifier.padding(IeltsSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    Text(
                        text = "Practice Notes and Band Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = { Text("Cue card notes or key phrases") }
                    )
                    Text(
                        text = "Select speaking band estimate:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                    ) {
                        SpeakingSelfBandOptions.forEach { band ->
                            FilterChip(
                                selected = selectedBand == band,
                                onClick = { selectedBand = band },
                                label = { Text(IeltsScoring.formatBand(band)) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val rounded = IeltsScoring.roundToHalfBand(selectedBand)
                            savedBandNotice = rounded
                            onSaveBand(rounded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IeltsSpacing.TouchTarget)
                    ) {
                        Text("Save Speaking Band (${IeltsScoring.formatBand(selectedBand)})")
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakingPartCard(
    partBadge: String,
    partIcon: ImageVector,
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
            ) {
                Icon(
                    imageVector = partIcon,
                    contentDescription = partBadge,
                    tint = BrandTeal,
                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                )
                Surface(
                    color = BrandTealSoft,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = partBadge,
                        modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandTeal
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = body.ifBlank { "No prompt text available." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
