package com.samuel.ielts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var showNotes by rememberSaveable(speakingData.testId) { mutableStateOf(false) }
    var selectedBand by rememberSaveable(speakingData.testId) {
        mutableDoubleStateOf(initialBand ?: 6.5)
    }
    var savedBandNotice by rememberSaveable(speakingData.testId) {
        mutableStateOf<Double?>(null)
    }

    val recordedBand = savedBandNotice ?: initialBand

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(IeltsSpacing.CardPadding),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.SectionGap)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap),
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
                            contentDescription = "Speaking",
                            tint = BrandTeal,
                            modifier = Modifier.size(IeltsSpacing.FeatureIcon)
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
                        text = if (recordedBand != null) {
                            "Band ${IeltsScoring.formatBand(recordedBand)}"
                        } else {
                            "Not marked yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recordedBand != null) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
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
                    shape = MaterialTheme.shapes.large,
                    color = SuccessGreenBg,
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(IeltsSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.Completed,
                            contentDescription = "Saved",
                            tint = SuccessGreen,
                            modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                        )
                        Text(
                            text = "Saved Band ${IeltsScoring.formatBand(noticeBand)}.",
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
                key = "${speakingData.testId}_p1",
                partIcon = IeltsIcons.SpeakingPart1,
                title = "Part 1: Interview",
                body = speakingData.part1
            )
        }

        item {
            SpeakingPartCard(
                key = "${speakingData.testId}_p2",
                partIcon = IeltsIcons.SpeakingPart2,
                title = "Part 2: Cue Card",
                body = speakingData.part2
            )
        }

        item {
            SpeakingPartCard(
                key = "${speakingData.testId}_p3",
                partIcon = IeltsIcons.SpeakingPart3,
                title = "Part 3: Discussion",
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
                    modifier = Modifier.padding(IeltsSpacing.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Self-Assessment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (recordedBand != null) {
                                    "Saved: Band ${IeltsScoring.formatBand(recordedBand)}"
                                } else {
                                    "Not marked yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showNotes = !showNotes }) {
                            Text(if (showNotes) "Hide notes" else "Notes")
                        }
                    }

                    AnimatedVisibility(visible = showNotes) {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            label = { Text("Cue card notes") }
                        )
                    }

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
                        Text("Save Band ${IeltsScoring.formatBand(selectedBand)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakingPartCard(
    key: String,
    partIcon: ImageVector,
    title: String,
    body: String
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = partIcon,
                        contentDescription = title,
                        tint = BrandTeal,
                        modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
                    HorizontalDivider(color = NeutralBorder)
                    Text(
                        text = body.ifBlank { "No prompt available." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
