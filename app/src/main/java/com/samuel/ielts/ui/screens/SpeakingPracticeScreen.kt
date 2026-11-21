package com.samuel.ielts.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.SpeakingPromptData

private val SpeakingSelfBandOptions = listOf(5.5, 6.0, 6.5, 7.0, 7.5, 8.0)

@Composable
fun SpeakingPracticeScreen(
    testLabel: String,
    speakingData: SpeakingPromptData,
    initialBand: Double?,
    onSaveBand: (Double) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notes by rememberSaveable(speakingData.testId) { mutableStateOf("") }
    var selectedBand by rememberSaveable(speakingData.testId) {
        mutableDoubleStateOf(initialBand ?: 6.5)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
            }
        }

        item {
            SpeakingPartCard(
                partBadge = "Part 1",
                title = "Introduction and Interview",
                body = speakingData.part1
            )
        }

        item {
            SpeakingPartCard(
                partBadge = "Part 2",
                title = "Individual Long Turn (Cue Card)",
                body = speakingData.part2
            )
        }

        item {
            SpeakingPartCard(
                partBadge = "Part 3",
                title = "Two-Way Discussion",
                body = speakingData.part3
            )
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        onClick = { onSaveBand(IeltsScoring.roundToHalfBand(selectedBand)) },
                        modifier = Modifier.fillMaxWidth()
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
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = BrandTealSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = partBadge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
