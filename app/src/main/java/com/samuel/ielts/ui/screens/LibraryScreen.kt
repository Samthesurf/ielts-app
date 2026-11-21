package com.samuel.ielts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.samuel.ielts.ui.components.NextStepCardsSection
import com.samuel.ielts.ui.components.StreakFlameTile
import com.samuel.ielts.ui.theme.IeltsSpacing

private enum class BookFilter(val label: String) {
    ALL("All"),
    C20("Cambridge 20"),
    C21("Cambridge 21"),
    C19("Cambridge 19")
}

internal fun plainBookName(book: IeltsBook): String = when (book) {
    IeltsBook.C20 -> "Cambridge 20"
    IeltsBook.C21 -> "Cambridge 21"
    IeltsBook.C19 -> "Cambridge 19"
}

@Composable
fun LibraryScreen(
    tests: List<PracticeTest>,
    onOpenListening: (PracticeTest) -> Unit,
    onOpenReading: (PracticeTest) -> Unit,
    onOpenWriting: (PracticeTest) -> Unit,
    onOpenSpeaking: (PracticeTest) -> Unit,
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
        contentPadding = PaddingValues(IeltsSpacing.CardPadding),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.SectionGap)
    ) {
        item {
            Text(
                text = "Library",
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
                NextStepCardsSection(
                    title = "Next Step",
                    steps = listOf(topNextStep),
                    onSelectStep = onSelectNextStep
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

            if (availableSections == 0) {
                item(key = "empty_${book.code}") {
                    UnavailableBookCard(book = book)
                }
            } else {
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
}

@Composable
private fun BookSectionHeader(
    book: IeltsBook,
    availableSections: Int,
    completedSections: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = IeltsSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (availableSections == 0) IeltsIcons.EmptyNoSeeds else IeltsIcons.Library,
                contentDescription = plainBookName(book),
                tint = if (availableSections == 0) LockedSlate else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Spacer(modifier = Modifier.width(IeltsSpacing.Sm))
            Text(
                text = plainBookName(book),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        val statusSummary = if (availableSections == 0) {
            "Unavailable"
        } else {
            "$completedSections of $availableSections done"
        }
        Text(
            text = statusSummary,
            style = MaterialTheme.typography.labelMedium,
            color = if (availableSections == 0) LockedSlate else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnavailableBookCard(
    book: IeltsBook
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = LockedSlateBg,
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
        ) {
            Icon(
                imageVector = IeltsIcons.EmptyNoSeeds,
                contentDescription = "Unavailable",
                tint = LockedSlate,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                Text(
                    text = "${plainBookName(book)} tests not added yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LockedSlate
                )
                Text(
                    text = "Choose another book above to practice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LockedSlate
                )
            }
        }
    }
}

@Composable
private fun PracticeTestCard(
    test: PracticeTest,
    onOpenSkill: (IeltsSkill) -> Unit
) {
    var expanded by rememberSaveable(test.id) { mutableStateOf(false) }

    val sectionStates = test.sectionStates()
    val availableStates = sectionStates.filter { it.status != SectionStatus.LOCKED }
    val completedCount = availableStates.count { it.status == SectionStatus.COMPLETED }
    val recordedBands = listOfNotNull(
        test.lastListeningBand,
        test.lastReadingBand,
        test.lastWritingBand,
        test.lastSpeakingBand
    )
    val averageBand = recordedBands.takeIf { it.isNotEmpty() }?.let {
        IeltsScoring.roundToHalfBand(it.average())
    }

    val helperLine = when {
        !test.hasAnySeededSection -> "Unavailable"
        averageBand != null -> "Band ${IeltsScoring.formatBand(averageBand)} ($completedCount of ${availableStates.size} done)"
        else -> "Not marked yet"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (test.hasAnySeededSection) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    }
                )
                .padding(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                ) {
                    Text(
                        text = "Test ${test.testNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (test.hasAnySeededSection) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            LockedSlate
                        }
                    )
                    Text(
                        text = helperLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (averageBand != null) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = when {
                        !test.hasAnySeededSection -> IeltsIcons.EmptyNoSeeds
                        expanded -> IeltsIcons.ExpandLess
                        else -> IeltsIcons.ExpandMore
                    },
                    contentDescription = if (expanded) "Collapse test" else "Expand test",
                    tint = if (test.hasAnySeededSection) MaterialTheme.colorScheme.primary else LockedSlate,
                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                )
            }

            if (test.hasAnySeededSection) {
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
                        HorizontalDivider(color = NeutralBorder)

                        val listeningState = sectionStates[0]
                        val readingState = sectionStates[1]
                        val writingState = sectionStates[2]
                        val speakingState = sectionStates[3]

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
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
                            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
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
        SectionStatus.AVAILABLE -> BrandBlueSoft.copy(alpha = 0.4f)
        SectionStatus.COMPLETED -> SuccessGreenBg
    }

    val borderColor = when (state.status) {
        SectionStatus.LOCKED -> NeutralBorder
        SectionStatus.AVAILABLE -> BrandBlue.copy(alpha = 0.3f)
        SectionStatus.COMPLETED -> SuccessGreen.copy(alpha = 0.35f)
    }

    val accentColor = when (state.status) {
        SectionStatus.LOCKED -> LockedSlate
        SectionStatus.AVAILABLE -> BrandBlue
        SectionStatus.COMPLETED -> SuccessGreen
    }

    val statusText = when {
        isLocked -> "Unavailable"
        isDone && state.lastBand != null -> "Band ${IeltsScoring.formatBand(state.lastBand)}"
        isDone -> "Done"
        else -> "Start"
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 60.dp)
            .then(
                if (!isLocked) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.ItemGap, vertical = IeltsSpacing.ItemGap),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isLocked) IeltsIcons.EmptyNoSeeds else skillIcon(state.skill),
                contentDescription = state.skill.label,
                tint = accentColor,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = state.skill.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isLocked) LockedSlate else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
