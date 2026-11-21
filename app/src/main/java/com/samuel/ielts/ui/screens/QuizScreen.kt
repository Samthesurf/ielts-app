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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.PassageParagraph
import com.samuel.ielts.model.QuizQuestion
import com.samuel.ielts.model.ReadingPassage
import com.samuel.ielts.model.TfngOption
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(
    testLabel: String,
    passage: ReadingPassage,
    questions: List<QuizQuestion>,
    onScoreSubmitted: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var passageExpanded by rememberSaveable { mutableStateOf(true) }
    var selectedParagraph by rememberSaveable(passage.testId) {
        mutableStateOf(passage.paragraphs.firstOrNull()?.label ?: "A")
    }
    var checked by rememberSaveable(passage.testId) { mutableStateOf(false) }

    val mcqAnswers = remember(passage.testId) { mutableStateMapOf<String, Int>() }
    val tfngAnswers = remember(passage.testId) { mutableStateMapOf<String, TfngOption>() }
    val fillAnswers = remember(passage.testId) { mutableStateMapOf<String, String>() }

    val correctCount = questions.count { question ->
        isQuestionCorrect(
            question = question,
            mcqAnswers = mcqAnswers,
            tfngAnswers = tfngAnswers,
            fillAnswers = fillAnswers
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        PassagePane(
            testLabel = testLabel,
            passage = passage,
            expanded = passageExpanded,
            selectedParagraph = selectedParagraph,
            onToggleExpanded = { passageExpanded = !passageExpanded },
            onSelectParagraph = { selectedParagraph = it }
        )

        HorizontalDivider(color = NeutralBorder)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (checked) {
                item {
                    val estimatedBand = IeltsScoring.roundToHalfBand(
                        4.0 + (correctCount.toDouble() / questions.size.coerceAtLeast(1)) * 5.0
                    )
                    ResultBanner(
                        correctCount = correctCount,
                        totalCount = questions.size,
                        estimatedBand = estimatedBand
                    )
                }
            }

            items(items = questions, key = { it.id }) { question ->
                when (question) {
                    is QuizQuestion.Mcq -> McqQuestionCard(
                        question = question,
                        selectedIndex = mcqAnswers[question.id],
                        checked = checked,
                        onSelect = { mcqAnswers[question.id] = it }
                    )

                    is QuizQuestion.TrueFalseNotGiven -> TfngQuestionCard(
                        question = question,
                        selectedOption = tfngAnswers[question.id],
                        checked = checked,
                        onSelect = { tfngAnswers[question.id] = it }
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
                            val band = IeltsScoring.roundToHalfBand(
                                4.0 + (correctCount.toDouble() / questions.size.coerceAtLeast(1)) * 5.0
                            )
                            onScoreSubmitted(band)
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        style = MaterialTheme.typography.titleMedium,
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
    correctCount: Int,
    totalCount: Int,
    estimatedBand: Double
) {
    Surface(
        color = SuccessGreenBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                        text = "Score: $correctCount / $totalCount correct",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Text(
                        text = "Estimated Reading Band: ${IeltsScoring.formatBand(estimatedBand)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
                    )
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
    selectedIndex: Int?,
    checked: Boolean,
    onSelect: (Int) -> Unit
) {
    val isCorrect = selectedIndex == question.correctIndex

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
                typeLabel = "Multiple Choice",
                paragraphHint = question.paragraphHint
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            question.options.forEachIndexed { index, option ->
                val letter = ('A' + index).toString()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$letter. $option",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (checked) {
                val correctLetter = ('A' + question.correctIndex).toString()
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct ($correctLetter)"
                    } else {
                        "Correct answer: $correctLetter"
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
                typeLabel = "TRUE / FALSE / NOT GIVEN",
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
    val isCorrect = question.acceptedAnswers.any {
        it.equals(answerText.trim(), ignoreCase = true)
    }

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
                typeLabel = "Fill in the Blank",
                paragraphHint = question.paragraphHint
            )

            Surface(
                color = WarningAmberBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Word limit: ${question.wordLimitHint}",
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
                    val limitLabel = if (question.maxWords == 1) "1 word max" else "${question.maxWords} words max"
                    Text(
                        text = if (overWordLimit) {
                            "Too many words ($wordCount typed, $limitLabel)"
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
                        text = "Your answer goes over the word limit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }
            }

            if (checked) {
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct (${question.acceptedAnswers.first()})"
                    } else {
                        "Correct answer: ${question.acceptedAnswers.first()}"
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

private fun isQuestionCorrect(
    question: QuizQuestion,
    mcqAnswers: Map<String, Int>,
    tfngAnswers: Map<String, TfngOption>,
    fillAnswers: Map<String, String>
): Boolean {
    return when (question) {
        is QuizQuestion.Mcq -> mcqAnswers[question.id] == question.correctIndex
        is QuizQuestion.TrueFalseNotGiven -> tfngAnswers[question.id] == question.correctAnswer
        is QuizQuestion.FillBlank -> {
            val typed = fillAnswers[question.id].orEmpty().trim()
            question.acceptedAnswers.any { it.equals(typed, ignoreCase = true) }
        }
    }
}
