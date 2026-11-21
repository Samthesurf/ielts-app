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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.theme.IeltsSpacing
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
    onOpenLibrary: () -> Unit = {},
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

    // Progressive disclosure: reading passage collapsed by default.
    var passageExpanded by rememberSaveable(testId, activeSkill.name) { mutableStateOf(false) }
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
            val otherSkill = if (activeSkill == QuizSkillMode.LISTENING) {
                QuizSkillMode.READING
            } else {
                QuizSkillMode.LISTENING
            }
            val otherAvailable = if (otherSkill == QuizSkillMode.LISTENING) {
                listeningSections.isNotEmpty()
            } else {
                readingSections.isNotEmpty()
            }
            LockedQuizPlaceholder(
                testLabel = testLabel,
                skill = activeSkill,
                ctaLabel = if (otherAvailable) "Switch to ${otherSkill.label}" else "Open Library",
                onCtaClick = {
                    if (otherAvailable) {
                        onSelectSkill(otherSkill)
                    } else {
                        onOpenLibrary()
                    }
                }
            )
            return@Column
        }

        if (activeSkill == QuizSkillMode.READING && currentSection.passage != null) {
            PassagePane(
                passage = currentSection.passage,
                expanded = passageExpanded,
                selectedParagraph = selectedParagraph,
                onToggleExpanded = { passageExpanded = !passageExpanded },
                onSelectParagraph = { selectedParagraph = it }
            )
            HorizontalDivider(color = NeutralBorder)
        } else if (activeSkill == QuizSkillMode.LISTENING) {
            ListeningSectionBanner(section = currentSection)
            HorizontalDivider(color = NeutralBorder)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.SectionGap)
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
                        sectionLabel = plainSectionLabel(currentSection.shortLabel),
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
                        .padding(top = IeltsSpacing.Xs, bottom = IeltsSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
                ) {
                    // Word-count warnings never block quiz submission.
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
                        modifier = Modifier
                            .weight(1f)
                            .height(IeltsSpacing.TouchTarget)
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
                        },
                        modifier = Modifier.height(IeltsSpacing.TouchTarget)
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

private fun plainSectionLabel(shortLabel: String): String {
    return when {
        shortLabel.startsWith("S") && shortLabel.drop(1).all(Char::isDigit) ->
            "Section ${shortLabel.drop(1)}"
        shortLabel.startsWith("P") && shortLabel.drop(1).all(Char::isDigit) ->
            "Passage ${shortLabel.drop(1)}"
        else -> shortLabel
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
                .padding(horizontal = IeltsSpacing.CardPadding, vertical = IeltsSpacing.ItemGap),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = testLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)) {
                    FilterChip(
                        selected = activeSkill == QuizSkillMode.LISTENING,
                        onClick = { onSelectSkill(QuizSkillMode.LISTENING) },
                        label = {
                            Text(if (hasListening) "Listening" else "Listening (Off)")
                        }
                    )
                    FilterChip(
                        selected = activeSkill == QuizSkillMode.READING,
                        onClick = { onSelectSkill(QuizSkillMode.READING) },
                        label = {
                            Text(if (hasReading) "Reading" else "Reading (Off)")
                        }
                    )
                }
            }

            if (sections.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    sections.forEachIndexed { index, section ->
                        FilterChip(
                            selected = selectedSectionIndex == index,
                            onClick = { onSelectSection(index) },
                            label = { Text(plainSectionLabel(section.shortLabel)) }
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
    skill: QuizSkillMode,
    ctaLabel: String,
    onCtaClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(IeltsSpacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = LockedSlateBg,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, NeutralBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(IeltsSpacing.Lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
            ) {
                Icon(
                    imageVector = IeltsIcons.EmptyNoSeeds,
                    contentDescription = "Unavailable",
                    tint = LockedSlate,
                    modifier = Modifier.size(IeltsSpacing.HeroIcon)
                )
                Text(
                    text = "$testLabel ${skill.label} not available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Choose another test or skill to practice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onCtaClick,
                    modifier = Modifier.height(IeltsSpacing.TouchTarget)
                ) {
                    Text(ctaLabel)
                }
            }
        }
    }
}

@Composable
private fun ListeningSectionBanner(
    section: QuizSectionBundle
) {
    Surface(
        color = BrandTealSoft.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.CardPadding, vertical = IeltsSpacing.ItemGap),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
        ) {
            Text(
                text = section.title,
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
    passage: ReadingPassage,
    expanded: Boolean,
    selectedParagraph: String,
    onToggleExpanded: () -> Unit,
    onSelectParagraph: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rangeLabel = if (passage.paragraphs.size >= 2) {
        "Paragraphs ${passage.paragraphs.first().label} to ${passage.paragraphs.last().label}"
    } else {
        "Reading passage"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.CardPadding, vertical = IeltsSpacing.ItemGap),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = passage.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                ) {
                    Text(
                        text = if (expanded) "Hide" else "Read",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expanded) IeltsIcons.ExpandLess else IeltsIcons.ExpandMore,
                        contentDescription = if (expanded) "Hide passage" else "Show passage",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
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
                                label = { Text(paragraph.label) }
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
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
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) BrandBlueSoft.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else NeutralBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(IeltsSpacing.ItemGap),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = paragraph.label,
                    modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
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
    var showBreakdown by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = SuccessGreenBg,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
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
                        imageVector = IeltsIcons.Completed,
                        contentDescription = "Quiz checked",
                        tint = SuccessGreen,
                        modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Band ${IeltsScoring.formatBand(estimatedBand)} (${totalCorrect} / $totalCount)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            text = "$sectionLabel: $sectionCorrect / $sectionTotal",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen
                        )
                    }
                }
                if (typeBreakdown.isNotEmpty()) {
                    TextButton(onClick = { showBreakdown = !showBreakdown }) {
                        Text(
                            text = if (showBreakdown) "Hide" else "Breakdown",
                            color = SuccessGreen
                        )
                    }
                }
            }

            if (typeBreakdown.isNotEmpty()) {
                AnimatedVisibility(visible = showBreakdown) {
                    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                        typeBreakdown.forEach { (category, pair) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = plainQuestionTypeName(category),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen
                                )
                                Text(
                                    text = "${pair.first} / ${pair.second}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
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
    typeIcon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
    ) {
        Icon(
            imageVector = typeIcon,
            contentDescription = typeLabel,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IeltsSpacing.FeatureIcon)
        )
        Text(
            text = "Question $number",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelMedium,
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            QuestionHeader(
                number = question.number,
                typeLabel = if (isMulti) "Choose ${question.requiredSelections}" else "Multiple Choice",
                typeIcon = IeltsIcons.QuizMcq
            )

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (isMulti) {
                Text(
                    text = "Selected ${selectedIndices.size} of ${question.requiredSelections}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overSelected) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                question.options.forEachIndexed { index, option ->
                    val label = question.labelForIndex(index)
                    val isSelected = index in selectedIndices
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleOption(index) }
                            .padding(vertical = IeltsSpacing.Xs),
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
                        Spacer(modifier = Modifier.width(IeltsSpacing.Sm))
                        Text(
                            text = "$label. $option",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            QuestionHeader(
                number = question.number,
                typeLabel = "True / False / Not Given",
                typeIcon = IeltsIcons.QuizTfng
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
            ) {
                TfngOption.entries.forEach { option ->
                    val selected = selectedOption == option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(option) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else NeutralBorder
                        )
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.padding(vertical = IeltsSpacing.ItemGap, horizontal = IeltsSpacing.Xs),
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            QuestionHeader(
                number = question.number,
                typeLabel = "Yes / No / Not Given",
                typeIcon = IeltsIcons.QuizYnng
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
            ) {
                YnngOption.entries.forEach { option ->
                    val selected = selectedOption == option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(option) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) BrandTeal else NeutralBorder
                        )
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.padding(vertical = IeltsSpacing.ItemGap, horizontal = IeltsSpacing.Xs),
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            QuestionHeader(
                number = question.number,
                typeLabel = "Fill in the Blank",
                typeIcon = IeltsIcons.QuizFillBlank
            )

            Text(
                text = "Limit: ${question.wordLimitHint}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WarningAmber
            )

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
                label = { Text("Your answer") }
            )

            if (overWordLimit) {
                Surface(
                    color = WarningAmberBg,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(IeltsSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.Warning,
                            contentDescription = "Word limit warning",
                            tint = WarningAmber,
                            modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                        )
                        Text(
                            text = "Word warning: $wordCount words (limit ${question.maxWords}). You can still submit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber
                        )
                    }
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
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = IeltsSpacing.ItemGap, vertical = IeltsSpacing.Sm),
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
