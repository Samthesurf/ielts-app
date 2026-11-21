package com.samuel.ielts.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.IeltsBook
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.IeltsSkill
import com.samuel.ielts.model.NextStepCardData
import com.samuel.ielts.model.PracticeTest
import com.samuel.ielts.model.SectionStatus
import com.samuel.ielts.model.SkillSectionState
import com.samuel.ielts.model.StudyProgressSnapshot
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.components.NextStepSingleCard
import com.samuel.ielts.ui.components.StreakFlameTile
import com.samuel.ielts.ui.theme.IeltsSpacing

private enum class BookFilter(val label: String) {
    ALL("All"),
    C20("C20"),
    C21("C21"),
    C19("C19")
}

internal fun plainBookName(book: IeltsBook): String = book.code

@Composable
fun LibraryScreen(
    tests: List<PracticeTest>,
    onOpenTest: (PracticeTest) -> Unit,
    streakSnapshot: StudyProgressSnapshot = StudyProgressSnapshot(),
    topNextStep: NextStepCardData? = null,
    onSelectNextStep: (NextStepCardData) -> Unit = {},
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
        contentPadding = PaddingValues(IeltsSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
    ) {
        item {
            Text(
                text = "Test Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            StreakFlameTile(
                snapshot = streakSnapshot,
                compact = true
            )
        }

        if (topNextStep != null) {
            item {
                NextStepSingleCard(
                    step = topNextStep,
                    onStart = { onSelectNextStep(topNextStep) }
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
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
            val availableSections = allSections.count { it.status != SectionStatus.LOCKED }
            val completedSections = allSections.count { it.status == SectionStatus.COMPLETED }

            item(key = "header_${book.code}") {
                BookSectionHeader(
                    book = book,
                    availableSections = availableSections,
                    completedSections = completedSections
                )
            }

            items(items = bookTests, key = { it.id }) { test ->
                PracticeTestRow(
                    test = test,
                    onClick = { onOpenTest(test) }
                )
            }
        }
    }
}

@Composable
private fun BookSectionHeader(
    book: IeltsBook,
    availableSections: Int,
    completedSections: Int
) {
    val headerText = if (availableSections == 0) {
        "${book.code} - Locked"
    } else {
        "${book.code} - $completedSections of $availableSections done"
    }

    Text(
        text = headerText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (availableSections == 0) LockedSlate else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = IeltsSpacing.Sm)
    )
}

@Composable
private fun PracticeTestRow(
    test: PracticeTest,
    onClick: () -> Unit
) {
    val sectionStates = test.sectionStates()
    val availableStates = sectionStates.filter { it.status != SectionStatus.LOCKED }
    val completedCount = availableStates.count { it.status == SectionStatus.COMPLETED }

    val statusWord = when {
        !test.hasAnySeededSection -> "Locked"
        availableStates.isNotEmpty() && completedCount == availableStates.size -> "Done"
        else -> "Ready"
    }

    val statusColor = when (statusWord) {
        "Done" -> SuccessGreen
        "Ready" -> BrandBlue
        else -> LockedSlate
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (test.hasAnySeededSection) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (test.hasAnySeededSection) {
                MaterialTheme.colorScheme.surface
            } else {
                LockedSlateBg.copy(alpha = 0.7f)
            }
        ),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.Md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Test ${test.testNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (test.hasAnySeededSection) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    LockedSlate
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
            ) {
                Text(
                    text = statusWord,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    maxLines = 1
                )
                if (test.hasAnySeededSection) {
                    Icon(
                        imageVector = IeltsIcons.ChevronRight,
                        contentDescription = "Open Test ${test.testNumber}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                }
            }
        }
    }
}

@Composable
fun TestDetailScreen(
    test: PracticeTest,
    onBack: () -> Unit,
    onOpenSkill: (IeltsSkill) -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionStates = test.sectionStates()
    val recordedBands = listOfNotNull(
        test.lastListeningBand,
        test.lastReadingBand,
        test.lastWritingBand,
        test.lastSpeakingBand
    )
    val averageBand = recordedBands.takeIf { it.isNotEmpty() }?.let {
        IeltsScoring.roundToHalfBand(it.average())
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(IeltsSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(IeltsSpacing.TouchTarget)
                ) {
                    Icon(
                        imageVector = IeltsIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${test.book.code} Test ${test.testNumber}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (averageBand != null) {
                            "Band ${IeltsScoring.formatBand(averageBand)}"
                        } else {
                            "Not marked yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (averageBand != null) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        items(items = sectionStates, key = { it.skill.name }) { state ->
            SkillDetailRowCard(
                state = state,
                onClick = { onOpenSkill(state.skill) }
            )
        }
    }
}

@Composable
private fun SkillDetailRowCard(
    state: SkillSectionState,
    onClick: () -> Unit
) {
    val isLocked = state.status == SectionStatus.LOCKED
    val isDone = state.status == SectionStatus.COMPLETED

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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isLocked) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) LockedSlateBg.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
        ),
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
                imageVector = if (isLocked) IeltsIcons.EmptyNoSeeds else skillIcon(state.skill),
                contentDescription = state.skill.label,
                tint = accentColor,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Text(
                text = state.skill.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isLocked) LockedSlate else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                maxLines = 1
            )
            if (!isLocked) {
                Icon(
                    imageVector = IeltsIcons.ChevronRight,
                    contentDescription = "Start ${state.skill.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                )
            }
        }
    }
}

private fun skillIcon(skill: IeltsSkill): ImageVector = when (skill) {
    IeltsSkill.LISTENING -> IeltsIcons.Listening
    IeltsSkill.READING -> IeltsIcons.Reading
    IeltsSkill.WRITING -> IeltsIcons.Writing
    IeltsSkill.SPEAKING -> IeltsIcons.Speaking
}
