package com.samuel.ielts.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.IeltsBook
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.IeltsSkill
import com.samuel.ielts.model.PracticeTest
import com.samuel.ielts.model.SectionStatus
import com.samuel.ielts.model.SkillSectionState

private enum class BookFilter(val label: String) {
    ALL("All Books"),
    C20("C20"),
    C21("C21"),
    C19("C19")
}

@Composable
fun LibraryScreen(
    tests: List<PracticeTest>,
    onOpenListening: (PracticeTest) -> Unit,
    onOpenReading: (PracticeTest) -> Unit,
    onOpenWriting: (PracticeTest) -> Unit,
    onOpenSpeaking: (PracticeTest) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by rememberSaveable { mutableStateOf(BookFilter.ALL) }

    val visibleBooks = when (selectedFilter) {
        BookFilter.ALL -> IeltsBook.entries
        BookFilter.C20 -> listOf(IeltsBook.C20)
        BookFilter.C21 -> listOf(IeltsBook.C21)
        BookFilter.C19 -> listOf(IeltsBook.C19)
    }

    val groupedByBook = tests.groupBy { it.book }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Test Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Books C20, C21, and C19 with per-test Listening, Reading, Writing, and Speaking status.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                BookFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        visibleBooks.forEach { book ->
            val bookTests = groupedByBook[book].orEmpty().sortedBy { it.testNumber }
            val allSections = bookTests.flatMap { it.sectionStates() }
            val seededSections = allSections.count { it.status != SectionStatus.LOCKED }
            val completedSections = allSections.count { it.status == SectionStatus.COMPLETED }

            item(key = "header_${book.code}") {
                BookSectionHeader(
                    book = book,
                    seededSections = seededSections,
                    completedSections = completedSections,
                    totalSections = allSections.size
                )
            }

            items(items = bookTests, key = { it.id }) { test ->
                PracticeTestCard(
                    test = test,
                    onOpenSkill = { skill ->
                        when (skill) {
                            IeltsSkill.LISTENING -> onOpenListening(test)
                            IeltsSkill.READING -> onOpenReading(test)
                            IeltsSkill.WRITING -> onOpenWriting(test)
                            IeltsSkill.SPEAKING -> onOpenSpeaking(test)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BookSectionHeader(
    book: IeltsBook,
    seededSections: Int,
    completedSections: Int,
    totalSections: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (seededSections == 0) Icons.Default.Lock else Icons.Default.Menu,
                contentDescription = null,
                tint = if (seededSections == 0) LockedSlate else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        val statusSummary = if (seededSections == 0) {
            "Seed missing (locked)"
        } else {
            "$completedSections/$seededSections done ($seededSections/$totalSections seeded)"
        }
        Text(
            text = statusSummary,
            style = MaterialTheme.typography.labelMedium,
            color = if (seededSections == 0) LockedSlate else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PracticeTestCard(
    test: PracticeTest,
    onOpenSkill: (IeltsSkill) -> Unit
) {
    val sectionStates = test.sectionStates()
    val allSeededDone = sectionStates.any { it.status != SectionStatus.LOCKED } &&
        sectionStates.filter { it.status != SectionStatus.LOCKED }.all { it.status == SectionStatus.COMPLETED }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (test.hasAnySeededSection) {
                MaterialTheme.colorScheme.surface
            } else {
                LockedSlateBg.copy(alpha = 0.65f)
            }
        ),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (test.hasAnySeededSection) BrandBlueSoft else LockedSlateBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${test.book.code} Test ${test.testNumber}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (test.hasAnySeededSection) MaterialTheme.colorScheme.primary else LockedSlate
                        )
                    }

                    if (!test.hasAnySeededSection) {
                        Surface(
                            color = LockedSlateBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NeutralBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = LockedSlate,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Seed missing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LockedSlate,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else if (allSeededDone) {
                        Surface(
                            color = SuccessGreenBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Done",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                val scoreText = buildString {
                    if (test.lastListeningBand != null) {
                        append("L ${IeltsScoring.formatBand(test.lastListeningBand)}")
                    }
                    if (test.lastReadingBand != null) {
                        if (isNotEmpty()) append(" ")
                        append("R ${IeltsScoring.formatBand(test.lastReadingBand)}")
                    }
                    if (test.lastWritingBand != null) {
                        if (isNotEmpty()) append(" ")
                        append("W ${IeltsScoring.formatBand(test.lastWritingBand)}")
                    }
                    if (test.lastSpeakingBand != null) {
                        if (isNotEmpty()) append(" ")
                        append("S ${IeltsScoring.formatBand(test.lastSpeakingBand)}")
                    }
                }
                if (scoreText.isNotEmpty()) {
                    Text(
                        text = scoreText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandTeal
                    )
                }
            }

            if (test.hasAnySeededSection) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (test.readingStatus != SectionStatus.LOCKED) {
                        Text(
                            text = "Reading: ${test.readingTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (test.writingStatus != SectionStatus.LOCKED) {
                        Text(
                            text = "Writing: ${test.writingTopic}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val listeningState = sectionStates[0]
            val readingState = sectionStates[1]
            val writingState = sectionStates[2]
            val speakingState = sectionStates[3]

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkillStatusTile(
                    state = listeningState,
                    onClick = { onOpenSkill(IeltsSkill.LISTENING) },
                    modifier = Modifier.weight(1f)
                )
                SkillStatusTile(
                    state = readingState,
                    onClick = { onOpenSkill(IeltsSkill.READING) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkillStatusTile(
                    state = writingState,
                    onClick = { onOpenSkill(IeltsSkill.WRITING) },
                    modifier = Modifier.weight(1f)
                )
                SkillStatusTile(
                    state = speakingState,
                    onClick = { onOpenSkill(IeltsSkill.SPEAKING) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SkillStatusTile(
    state: SkillSectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = state.status == SectionStatus.LOCKED
    val isDone = state.status == SectionStatus.COMPLETED

    val containerColor = when (state.status) {
        SectionStatus.LOCKED -> LockedSlateBg
        SectionStatus.AVAILABLE -> BrandBlueSoft.copy(alpha = 0.45f)
        SectionStatus.COMPLETED -> SuccessGreenBg
    }

    val borderColor = when (state.status) {
        SectionStatus.LOCKED -> NeutralBorder
        SectionStatus.AVAILABLE -> BrandBlue.copy(alpha = 0.35f)
        SectionStatus.COMPLETED -> SuccessGreen.copy(alpha = 0.4f)
    }

    val accentColor = when (state.status) {
        SectionStatus.LOCKED -> LockedSlate
        SectionStatus.AVAILABLE -> BrandBlue
        SectionStatus.COMPLETED -> SuccessGreen
    }

    val statusText = when {
        isLocked -> "Locked"
        isDone && state.lastBand != null -> "Band ${IeltsScoring.formatBand(state.lastBand)}"
        isDone -> "Done"
        else -> "Ready"
    }

    Surface(
        modifier = modifier
            .then(
                if (!isLocked) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.skill.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isLocked) LockedSlate else MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = when (state.status) {
                        SectionStatus.LOCKED -> Icons.Default.Lock
                        SectionStatus.AVAILABLE -> Icons.Default.PlayArrow
                        SectionStatus.COMPLETED -> Icons.Default.CheckCircle
                    },
                    contentDescription = state.status.label,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLocked) LockedSlate else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
        }
    }
}
