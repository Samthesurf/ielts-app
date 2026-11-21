package com.samuel.ielts.model

import java.util.Locale
import kotlin.math.floor

enum class IeltsBook(val code: String, val title: String) {
    C20("C20", "Book C20"),
    C21("C21", "Book C21")
}

data class PracticeTest(
    val id: String,
    val book: IeltsBook,
    val testNumber: Int,
    val readingTitle: String,
    val writingTopic: String,
    val readingCompleted: Boolean,
    val writingCompleted: Boolean,
    val lastReadingBand: Double?,
    val lastWritingBand: Double?
)

data class PassageParagraph(
    val label: String,
    val text: String
)

data class ReadingPassage(
    val testId: String,
    val title: String,
    val subtitle: String,
    val paragraphs: List<PassageParagraph>
)

sealed interface QuizQuestion {
    val id: String
    val number: Int
    val prompt: String
    val paragraphHint: String

    data class Mcq(
        override val id: String,
        override val number: Int,
        override val prompt: String,
        override val paragraphHint: String,
        val options: List<String>,
        val correctIndex: Int
    ) : QuizQuestion

    data class TrueFalseNotGiven(
        override val id: String,
        override val number: Int,
        override val prompt: String,
        override val paragraphHint: String,
        val correctAnswer: TfngOption
    ) : QuizQuestion

    data class FillBlank(
        override val id: String,
        override val number: Int,
        override val prompt: String,
        override val paragraphHint: String,
        val wordLimitHint: String,
        val maxWords: Int,
        val acceptedAnswers: List<String>
    ) : QuizQuestion
}

enum class TfngOption(val label: String) {
    TRUE("TRUE"),
    FALSE("FALSE"),
    NOT_GIVEN("NOT GIVEN")
}

enum class WritingTaskType(
    val shortLabel: String,
    val title: String,
    val minWords: Int,
    val firstCriterionCode: String,
    val firstCriterionName: String
) {
    TASK_1(
        shortLabel = "Task 1",
        title = "Writing Task 1",
        minWords = 150,
        firstCriterionCode = "TA",
        firstCriterionName = "Task Achievement"
    ),
    TASK_2(
        shortLabel = "Task 2",
        title = "Writing Task 2",
        minWords = 250,
        firstCriterionCode = "TR",
        firstCriterionName = "Task Response"
    )
}

enum class WritingCriterion(val code: String, val fullName: String) {
    TA("TA", "Task Achievement"),
    TR("TR", "Task Response"),
    CC("CC", "Coherence and Cohesion"),
    LR("LR", "Lexical Resource"),
    GRA("GRA", "Grammatical Range and Accuracy")
}

data class CriterionScore(
    val criterion: WritingCriterion,
    val band: Double,
    val summary: String
)

data class InlineComment(
    val id: Int,
    val excerpt: String,
    val criterion: WritingCriterion,
    val note: String,
    val suggestion: String
)

data class WritingTaskData(
    val taskType: WritingTaskType,
    val prompt: String,
    val initialDraft: String,
    val scores: List<CriterionScore>,
    val inlineComments: List<InlineComment>,
    val band75Rewrite: String,
    val band8Model: String
)

data class SkillProgress(
    val label: String,
    val band: Double
)

data class TestProgressRecord(
    val label: String,
    val band: Double
)

object IeltsScoring {
    fun roundToHalfBand(raw: Double): Double {
        val clamped = raw.coerceIn(0.0, 9.0)
        return floor(clamped * 2.0 + 0.5) / 2.0
    }

    fun calculateTaskBand(scores: List<CriterionScore>): Double {
        if (scores.isEmpty()) return 0.0
        val avg = scores.map { it.band }.average()
        return roundToHalfBand(avg)
    }

    fun calculateWritingOverall(task1Band: Double, task2Band: Double): Double {
        val weighted = (task1Band + task2Band * 2.0) / 3.0
        return roundToHalfBand(weighted)
    }

    fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).size
    }

    fun formatBand(band: Double): String {
        return String.format(Locale.US, "%.1f", band)
    }
}
