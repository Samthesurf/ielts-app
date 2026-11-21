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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.samuel.ielts.data.AnswerNormalizer
import com.samuel.ielts.model.IeltsBook
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.PassageParagraph
import com.samuel.ielts.model.QuestionTypeCategory
import com.samuel.ielts.model.QuizAttemptSummary
import com.samuel.ielts.model.QuizQuestion
import com.samuel.ielts.model.QuizSectionBundle
import com.samuel.ielts.model.QuizSkillMode
import com.samuel.ielts.model.ReadingPassage
import com.samuel.ielts.model.TfngOption
import com.samuel.ielts.model.YnngOption
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(
    testId: String,
    book: IeltsBook,
    testNumber: Int,
    testLabel: String,
    activeSkill: QuizSkillMode,
    onSelectSkill: (QuizSkillMode) -> Unit,
    listeningSections: List<QuizSectionBundle>,
    readingSections: List<QuizSectionBundle>,
    onQuizSubmitted: (QuizAttemptSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = if (activeSkill == QuizSkillMode.LISTENING) {
        listeningSections
    } else {
        readingSections
    }

    var selectedSectionIndex by rememberSaveable(testId, activeSkill.name) { mutableIntStateOf(0) }
    val safeSectionIndex = selectedSectionIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
    val currentSection = sections.getOrNull(safeSectionIndex)

    var passageExpanded by rememberSaveable(testId, activeSkill.name) { mutableStateOf(true) }
    var selectedParagraph by rememberSaveable(currentSection?.id ?: testId) {
        mutableStateOf(currentSection?.passage?.paragraphs?.firstOrNull()?.label ?: "A")
    }
    var checked by rememberSaveable(testId, activeSkill.name) { mutableStateOf(false) }

    LaunchedEffect(currentSection?.id) {
        selectedParagraph = currentSection?.passage?.paragraphs?.firstOrNull()?.label ?: "A"
    }

    val mcqAnswers = remember(testId, activeSkill) { mutableStateMapOf<String, Set<Int>>() }
    val tfngAnswers = remember(testId, activeSkill) { mutableStateMapOf<String, TfngOption>() }
    val ynngAnswers = remember(testId, activeSkill) { mutableStateMapOf<String, YnngOption>() }
    val fillAnswers = remember(testId, activeSkill) { mutableStateMapOf<String, String>() }

    val allSkillQuestions = remember(sections) { sections.flatMap { it.questions } }
    val sectionQuestions = currentSection?.questions.orEmpty()

    val totalCorrectCount = allSkillQuestions.count { question ->
        isQuestionCorrect(
            question = question,
            mcqAnswers = mcqAnswers,
            tfngAnswers = tfngAnswers,
            ynngAnswers = ynngAnswers,
            fillAnswers = fillAnswers
        )
    }

    val sectionCorrectCount = sectionQuestions.count { question ->
        isQuestionCorrect(
            question = question,
            mcqAnswers = mcqAnswers,
            tfngAnswers = tfngAnswers,
            ynngAnswers = ynngAnswers,
            fillAnswers = fillAnswers
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        QuizHeaderControls(
            testLabel = testLabel,
            activeSkill = activeSkill,
            hasListening = listeningSections.isNotEmpty(),
            hasReading = readingSections.isNotEmpty(),
            onSelectSkill = onSelectSkill,
            sections = sections,
            selectedSectionIndex = safeSectionIndex,
            onSelectSection = { selectedSectionIndex = it }
        )

        HorizontalDivider(color = NeutralBorder)

        if (currentSection == null) {
            LockedQuizPlaceholder(
                testLabel = testLabel,
                skill = activeSkill
            )
            return@Column
        }

        if (activeSkill == QuizSkillMode.READING && currentSection.passage != null) {
            PassagePane(
                testLabel = "$testLabel ${currentSection.shortLabel}",
                passage = currentSection.passage,
                expanded = passageExpanded,
                selectedParagraph = selectedParagraph,
                onToggleExpanded = { passageExpanded = !passageExpanded },
                onSelectParagraph = { selectedParagraph = it }
            )
            HorizontalDivider(color = NeutralBorder)
        } else if (activeSkill == QuizSkillMode.LISTENING) {
            ListeningSectionBanner(
                testLabel = testLabel,
                section = currentSection
            )
            HorizontalDivider(color = NeutralBorder)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (checked) {
                item {
                    val estimatedBand = IeltsScoring.bandFromRawScore(
                        correct = totalCorrectCount,
                        total = allSkillQuestions.size
                    )
                    val breakdown = computeTypeBreakdown(
                        questions = allSkillQuestions,
                        mcqAnswers = mcqAnswers,
                        tfngAnswers = tfngAnswers,
                        ynngAnswers = ynngAnswers,
                        fillAnswers = fillAnswers
                    )
                    ResultBanner(
                        skill = activeSkill,
                        sectionLabel = currentSection.shortLabel,
                        sectionCorrect = sectionCorrectCount,
                        sectionTotal = sectionQuestions.size,
                        totalCorrect = totalCorrectCount,
                        totalCount = allSkillQuestions.size,
                        estimatedBand = estimatedBand,
                        typeBreakdown = breakdown
                    )
                }
            }

            items(items = sectionQuestions, key = { it.id }) { question ->
                when (question) {
                    is QuizQuestion.Mcq -> McqQuestionCard(
                        question = question,
                        selectedIndices = mcqAnswers[question.id].orEmpty(),
                        checked = checked,
                        onToggleOption = { index ->
                            if (question.requiredSelections <= 1) {
                                mcqAnswers[question.id] = setOf(index)
                            } else {
                                val current = mcqAnswers[question.id].orEmpty().toMutableSet()
                                if (index in current) {
                                    current.remove(index)
                                } else {
                                    current.add(index)
                                }
                                mcqAnswers[question.id] = current
                            }
                        }
                    )

                    is QuizQuestion.TrueFalseNotGiven -> TfngQuestionCard(
                        question = question,
                        selectedOption = tfngAnswers[question.id],
                        checked = checked,
                        onSelect = { tfngAnswers[question.id] = it }
                    )

                    is QuizQuestion.YesNoNotGiven -> YnngQuestionCard(
                        question = question,
                        selectedOption = ynngAnswers[question.id],
                        checked = checked,
                        onSelect = { ynngAnswers[question.id] = it }
                    )

                    is QuizQuestion.FillBlank -> FillBlankQuestionCard(
                        question = question,
                        answerText = fillAnswers[question.id].orEmpty(),
                        checked = checked,
                        onValueChange = { fillAnswers[question.id] = it }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            checked = true
                            val breakdown = computeTypeBreakdown(
                                questions = allSkillQuestions,
                                mcqAnswers = mcqAnswers,
                                tfngAnswers = tfngAnswers,
                                ynngAnswers = ynngAnswers,
                                fillAnswers = fillAnswers
                            )
                            val band = IeltsScoring.bandFromRawScore(
                                correct = totalCorrectCount,
                                total = allSkillQuestions.size
                            )
                            onQuizSubmitted(
                                QuizAttemptSummary(
                                    testId = testId,
                                    book = book,
                                    testNumber = testNumber,
                                    skill = activeSkill,
                                    correctCount = totalCorrectCount,
                                    totalCount = allSkillQuestions.size,
                                    band = band,
                                    typeBreakdown = breakdown
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Check Answers")
                    }
                    OutlinedButton(
                        onClick = {
                            checked = false
                            mcqAnswers.clear()
                            tfngAnswers.clear()
                            ynngAnswers.clear()
                            fillAnswers.clear()
                        }
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizHeaderControls(
    testLabel: String,
    activeSkill: QuizSkillMode,
    hasListening: Boolean,
    hasReading: Boolean,
    onSelectSkill: (QuizSkillMode) -> Unit,
    sections: List<QuizSectionBundle>,
    selectedSectionIndex: Int,
    onSelectSection: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$testLabel Quiz",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = activeSkill == QuizSkillMode.LISTENING,
                        onClick = { onSelectSkill(QuizSkillMode.LISTENING) },
                        label = {
                            Text(if (hasListening) "Listening S1-S4" else "Listening (Locked)")
                        }
                    )
                    FilterChip(
                        selected = activeSkill == QuizSkillMode.READING,
                        onClick = { onSelectSkill(QuizSkillMode.READING) },
                        label = {
                            Text(if (hasReading) "Reading P1-P3" else "Reading (Locked)")
                        }
                    )
                }
            }

            if (sections.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sections.forEachIndexed { index, section ->
                        val qFirst = section.questions.firstOrNull()?.number
                        val qLast = section.questions.lastOrNull()?.number
                        val rangeSuffix = if (qFirst != null && qLast != null) {
                            " (Q$qFirst-$qLast)"
                        } else {
                            ""
                        }
                        FilterChip(
                            selected = selectedSectionIndex == index,
                            onClick = { onSelectSection(index) },
                            label = { Text("${section.shortLabel}$rangeSuffix") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedQuizPlaceholder(
    testLabel: String,
    skill: QuizSkillMode
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = LockedSlateBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NeutralBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = LockedSlate,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "$testLabel ${skill.label} is locked",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "The seed file for this book section is missing. Pick an unlocked test from Library or switch to an available skill.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ListeningSectionBanner(
    testLabel: String,
    section: QuizSectionBundle
) {
    Surface(
        color = BrandTealSoft.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$testLabel: ${section.title}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandTeal
            )
            if (section.instructions.isNotBlank()) {
                Text(
                    text = section.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PassagePane(
    testLabel: String,
    passage: ReadingPassage,
    expanded: Boolean,
    selectedParagraph: String,
    onToggleExpanded: () -> Unit,
    onSelectParagraph: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rangeLabel = if (passage.paragraphs.size >= 2) {
        "Passage Pane ${passage.paragraphs.first().label} to ${passage.paragraphs.last().label}"
    } else {
        "Passage Pane A to G"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$testLabel: ${passage.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rangeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) "Hide" else "Show",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        passage.paragraphs.forEachIndexed { index, paragraph ->
                            FilterChip(
                                selected = selectedParagraph == paragraph.label,
                                onClick = {
                                    onSelectParagraph(paragraph.label)
                                    scope.launch {
                                        listState.animateScrollToItem(index)
                                    }
                                },
                                label = { Text("Para ${paragraph.label}") }
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = passage.paragraphs, key = { it.label }) { paragraph ->
                            ParagraphItem(
                                paragraph = paragraph,
                                highlighted = paragraph.label == selectedParagraph,
                                onClick = { onSelectParagraph(paragraph.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphItem(
    paragraph: PassageParagraph,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) BrandBlueSoft.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else NeutralBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = paragraph.label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(
                text = paragraph.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ResultBanner(
    skill: QuizSkillMode,
    sectionLabel: String,
    sectionCorrect: Int,
    sectionTotal: Int,
    totalCorrect: Int,
    totalCount: Int,
    estimatedBand: Double,
    typeBreakdown: Map<QuestionTypeCategory, Pair<Int, Int>>
) {
    Surface(
        color = SuccessGreenBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen
                )
                Column {
                    Text(
                        text = "${skill.label} Score: $totalCorrect / $totalCount ($sectionLabel: $sectionCorrect / $sectionTotal)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Text(
                        text = "Estimated ${skill.label} Band: ${IeltsScoring.formatBand(estimatedBand)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
                    )
                }
            }

            if (typeBreakdown.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    typeBreakdown.forEach { (category, pair) ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "${category.tag}: ${pair.first}/${pair.second}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionHeader(
    number: Int,
    typeLabel: String,
    paragraphHint: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Question $number ($typeLabel)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = paragraphHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun McqQuestionCard(
    question: QuizQuestion.Mcq,
    selectedIndices: Set<Int>,
    checked: Boolean,
    onToggleOption: (Int) -> Unit
) {
    val isMulti = question.requiredSelections > 1
    val isCorrect = selectedIndices == question.correctIndices
    val overSelected = isMulti && selectedIndices.size > question.requiredSelections

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
            QuestionHeader(
                number = question.number,
                typeLabel = if (isMulti) "MCQ - Pick ${question.requiredSelections}" else "MCQ",
                paragraphHint = question.paragraphHint
            )

            if (question.instructions.isNotBlank()) {
                Text(
                    text = question.instructions,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (isMulti) {
                Text(
                    text = "Selected ${selectedIndices.size} of ${question.requiredSelections} required option(s).",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overSelected) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            question.options.forEachIndexed { index, option ->
                val label = question.labelForIndex(index)
                val isSelected = index in selectedIndices
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleOption(index) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isMulti) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleOption(index) }
                        )
                    } else {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onToggleOption(index) }
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$label. $option",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (checked) {
                val correctLabels = question.correctIndices
                    .sorted()
                    .joinToString(", ") { question.labelForIndex(it) }
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct ($correctLabels)"
                    } else {
                        "Correct answer: $correctLabels"
                    }
                )
            }
        }
    }
}

@Composable
private fun TfngQuestionCard(
    question: QuizQuestion.TrueFalseNotGiven,
    selectedOption: TfngOption?,
    checked: Boolean,
    onSelect: (TfngOption) -> Unit
) {
    val isCorrect = selectedOption == question.correctAnswer

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
            QuestionHeader(
                number = question.number,
                typeLabel = "T/F/NG",
                paragraphHint = question.paragraphHint
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TfngOption.entries.forEach { option ->
                    val selected = selectedOption == option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(option) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else NeutralBorder
                        )
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (checked) {
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct (${question.correctAnswer.label})"
                    } else {
                        "Correct answer: ${question.correctAnswer.label}"
                    }
                )
            }
        }
    }
}

@Composable
private fun YnngQuestionCard(
    question: QuizQuestion.YesNoNotGiven,
    selectedOption: YnngOption?,
    checked: Boolean,
    onSelect: (YnngOption) -> Unit
) {
    val isCorrect = selectedOption == question.correctAnswer

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
            QuestionHeader(
                number = question.number,
                typeLabel = "Y/N/NG",
                paragraphHint = question.paragraphHint
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YnngOption.entries.forEach { option ->
                    val selected = selectedOption == option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(option) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) BrandTeal else NeutralBorder
                        )
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (checked) {
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct (${question.correctAnswer.label})"
                    } else {
                        "Correct answer: ${question.correctAnswer.label}"
                    }
                )
            }
        }
    }
}

@Composable
private fun FillBlankQuestionCard(
    question: QuizQuestion.FillBlank,
    answerText: String,
    checked: Boolean,
    onValueChange: (String) -> Unit
) {
    val wordCount = IeltsScoring.countWords(answerText)
    val overWordLimit = wordCount > question.maxWords
    val isCorrect = isFillBlankCorrect(question, answerText)

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
            QuestionHeader(
                number = question.number,
                typeLabel = "Fill-Blank",
                paragraphHint = question.paragraphHint
            )

            Surface(
                color = WarningAmberBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Word hint: ${question.wordLimitHint}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = WarningAmber
                )
            }

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = answerText,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Type your answer") },
                supportingText = {
                    val limitLabel = if (question.maxWords == 1) "1 word hint" else "${question.maxWords} words hint"
                    Text(
                        text = if (overWordLimit) {
                            "Word warning: $wordCount words typed ($limitLabel). Submission still allowed."
                        } else {
                            "$wordCount word(s) typed ($limitLabel)"
                        },
                        color = if (overWordLimit) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            if (overWordLimit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.height(16.dp)
                    )
                    Text(
                        text = "Warning: your answer exceeds the word hint. You can still submit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }
            }

            if (checked) {
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct (${question.acceptedAnswers.firstOrNull().orEmpty()})"
                    } else {
                        "Correct answer: ${question.rawAnswerKey.ifBlank { question.acceptedAnswers.firstOrNull().orEmpty() }}"
                    }
                )
            }
        }
    }
}

@Composable
private fun AnswerFeedbackChip(
    isCorrect: Boolean,
    message: String
) {
    Surface(
        color = if (isCorrect) SuccessGreenBg else ErrorRedBg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isCorrect) SuccessGreen else ErrorRed
        )
    }
}

private fun isFillBlankCorrect(
    question: QuizQuestion.FillBlank,
    answerText: String
): Boolean {
    val trimmed = answerText.trim()
    if (trimmed.isEmpty()) return false
    if (question.rawAnswerKey.isNotBlank() && AnswerNormalizer.matches(question.rawAnswerKey, trimmed)) {
        return true
    }
    return question.acceptedAnswers.any { accepted ->
        AnswerNormalizer.matches(accepted, trimmed) || accepted.equals(trimmed, ignoreCase = true)
    }
}

private fun isQuestionCorrect(
    question: QuizQuestion,
    mcqAnswers: Map<String, Set<Int>>,
    tfngAnswers: Map<String, TfngOption>,
    ynngAnswers: Map<String, YnngOption>,
    fillAnswers: Map<String, String>
): Boolean {
    return when (question) {
        is QuizQuestion.Mcq -> mcqAnswers[question.id].orEmpty() == question.correctIndices
        is QuizQuestion.TrueFalseNotGiven -> tfngAnswers[question.id] == question.correctAnswer
        is QuizQuestion.YesNoNotGiven -> ynngAnswers[question.id] == question.correctAnswer
        is QuizQuestion.FillBlank -> isFillBlankCorrect(question, fillAnswers[question.id].orEmpty())
    }
}

private fun computeTypeBreakdown(
    questions: List<QuizQuestion>,
    mcqAnswers: Map<String, Set<Int>>,
    tfngAnswers: Map<String, TfngOption>,
    ynngAnswers: Map<String, YnngOption>,
    fillAnswers: Map<String, String>
): Map<QuestionTypeCategory, Pair<Int, Int>> {
    val result = linkedMapOf<QuestionTypeCategory, Pair<Int, Int>>()
    QuestionTypeCategory.entries.forEach { category ->
        val matching = questions.filter { it.category == category }
        if (matching.isNotEmpty()) {
            val correct = matching.count { q ->
                isQuestionCorrect(q, mcqAnswers, tfngAnswers, ynngAnswers, fillAnswers)
            }
            result[category] = correct to matching.size
        }
    }
    return result
}
