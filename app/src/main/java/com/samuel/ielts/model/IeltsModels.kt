package com.samuel.ielts.model

import java.util.Locale
import kotlin.math.floor

enum class IeltsBook(val code: String, val title: String) {
    C20("C20", "Book C20"),
    C21("C21", "Book C21"),
    C19("C19", "Book C19")
}

enum class IeltsSkill(val shortCode: String, val label: String) {
    LISTENING("L", "Listening"),
    READING("R", "Reading"),
    WRITING("W", "Writing"),
    SPEAKING("S", "Speaking")
}

enum class SectionStatus(val label: String) {
    LOCKED("Locked"),
    AVAILABLE("Ready"),
    COMPLETED("Done")
}

data class SkillSectionState(
    val skill: IeltsSkill,
    val status: SectionStatus,
    val lastBand: Double? = null,
    val subtitle: String = ""
)

data class PracticeTest(
    val id: String,
    val book: IeltsBook,
    val testNumber: Int,
    val readingTitle: String,
    val writingTopic: String,
    val listeningTitle: String = "Listening Sections 1 to 4",
    val speakingTopic: String = "",
    val listeningStatus: SectionStatus = SectionStatus.LOCKED,
    val readingStatus: SectionStatus = SectionStatus.LOCKED,
    val writingStatus: SectionStatus = SectionStatus.LOCKED,
    val speakingStatus: SectionStatus = SectionStatus.LOCKED,
    val lastListeningBand: Double? = null,
    val lastReadingBand: Double? = null,
    val lastWritingBand: Double? = null,
    val lastSpeakingBand: Double? = null
) {
    val listeningCompleted: Boolean
        get() = listeningStatus == SectionStatus.COMPLETED

    val readingCompleted: Boolean
        get() = readingStatus == SectionStatus.COMPLETED

    val writingCompleted: Boolean
        get() = writingStatus == SectionStatus.COMPLETED

    val speakingCompleted: Boolean
        get() = speakingStatus == SectionStatus.COMPLETED

    val hasAnySeededSection: Boolean
        get() = listeningStatus != SectionStatus.LOCKED ||
            readingStatus != SectionStatus.LOCKED ||
            writingStatus != SectionStatus.LOCKED ||
            speakingStatus != SectionStatus.LOCKED

    val hasAnyCompletedSection: Boolean
        get() = listeningCompleted || readingCompleted || writingCompleted || speakingCompleted

    fun sectionStates(): List<SkillSectionState> = listOf(
        SkillSectionState(
            skill = IeltsSkill.LISTENING,
            status = listeningStatus,
            lastBand = lastListeningBand,
            subtitle = if (listeningStatus == SectionStatus.LOCKED) "Seed missing" else "S1 to S4"
        ),
        SkillSectionState(
            skill = IeltsSkill.READING,
            status = readingStatus,
            lastBand = lastReadingBand,
            subtitle = if (readingStatus == SectionStatus.LOCKED) "Seed missing" else "Passages 1 to 3"
        ),
        SkillSectionState(
            skill = IeltsSkill.WRITING,
            status = writingStatus,
            lastBand = lastWritingBand,
            subtitle = if (writingStatus == SectionStatus.LOCKED) "Seed missing" else "Task 1 and 2"
        ),
        SkillSectionState(
            skill = IeltsSkill.SPEAKING,
            status = speakingStatus,
            lastBand = lastSpeakingBand,
            subtitle = if (speakingStatus == SectionStatus.LOCKED) "Seed missing" else "Parts 1 to 3"
        )
    )
}

enum class QuizSkillMode(val label: String) {
    LISTENING("Listening"),
    READING("Reading")
}

enum class QuestionTypeCategory(val tag: String, val label: String) {
    TFNG("T/F/NG", "TRUE / FALSE / NOT GIVEN"),
    YNNG("Y/N/NG", "YES / NO / NOT GIVEN"),
    MCQ("MCQ", "Multiple Choice"),
    FILL_BLANK("Fill-Blank", "Fill in the Blank")
}

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

data class QuizQuestionGroup(
    val id: String,
    val instructions: String = "",
    val wordLimit: String = "",
    val imageAsset: String? = null,
    val questions: List<QuizQuestion> = emptyList()
)

data class QuizSectionBundle(
    val id: String,
    val skill: QuizSkillMode,
    val sectionNumber: Int,
    val shortLabel: String,
    val title: String,
    val instructions: String = "",
    val passage: ReadingPassage? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val groups: List<QuizQuestionGroup> = emptyList()
)

sealed interface QuizQuestion {
    val id: String
    val number: Int
    val prompt: String
    val paragraphHint: String
    val instructions: String
    val category: QuestionTypeCategory

    data class Mcq(
        override val id: String,
        override val number: Int,
        override val prompt: String,
        override val paragraphHint: String,
        val options: List<String>,
        val correctIndex: Int,
        val optionLabels: List<String> = emptyList(),
        val correctIndices: Set<Int> = setOf(correctIndex),
        val requiredSelections: Int = 1,
        override val instructions: String = ""
    ) : QuizQuestion {
        override val category: QuestionTypeCategory = QuestionTypeCategory.MCQ

        fun labelForIndex(index: Int): String {
            return optionLabels.getOrNull(index)
                ?: ('A' + index).toString()
        }
    }

    data class TrueFalseNotGiven(
        override val id: String,
        override val number: Int,
        override val prompt: String,
        override val paragraphHint: String,
        val correctAnswer: TfngOption,
        override val instructions: String = ""
    ) : QuizQuestion {
        override val category: QuestionTypeCategory = QuestionTypeCategory.TFNG
    }

    data class YesNoNotGiven(
        override val id: String,
        override val number: Int,
        override val prompt: String,
        override val paragraphHint: String,
        val correctAnswer: YnngOption,
        override val instructions: String = ""
    ) : QuizQuestion {
        override val category: QuestionTypeCategory = QuestionTypeCategory.YNNG
    }

    data class FillBlank(
        override val id: String,
        override val number: Int,
        override val prompt: String,
        override val paragraphHint: String,
        val wordLimitHint: String,
        val maxWords: Int,
        val acceptedAnswers: List<String>,
        val rawAnswerKey: String = acceptedAnswers.joinToString("/"),
        override val instructions: String = ""
    ) : QuizQuestion {
        override val category: QuestionTypeCategory = QuestionTypeCategory.FILL_BLANK
    }
}

enum class TfngOption(val label: String) {
    TRUE("TRUE"),
    FALSE("FALSE"),
    NOT_GIVEN("NOT GIVEN")
}

enum class YnngOption(val label: String) {
    YES("YES"),
    NO("NO"),
    NOT_GIVEN("NOT GIVEN")
}

data class SpeakingPromptData(
    val testId: String,
    val topicSummary: String,
    val part1: String,
    val part2: String,
    val part3: String
)

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
    val band8Model: String,
    val chartAssetPath: String? = null
)

data class SkillProgress(
    val label: String,
    val band: Double
)

data class TestProgressRecord(
    val label: String,
    val band: Double,
    val book: IeltsBook? = null
)

data class QuestionTypeAccuracy(
    val category: QuestionTypeCategory,
    val correct: Int,
    val total: Int
) {
    val accuracyRatio: Double
        get() = if (total <= 0) 0.0 else correct.toDouble() / total.toDouble()

    val accuracyPercent: Int
        get() = if (total <= 0) 0 else floor(accuracyRatio * 100.0 + 0.5).toInt()
}

data class BookProgressSummary(
    val book: IeltsBook,
    val listeningBand: Double?,
    val readingBand: Double?,
    val writingBand: Double?,
    val speakingBand: Double?,
    val completedSections: Int,
    val availableSections: Int,
    val totalSections: Int,
    val hasSeed: Boolean
) {
    val averageBand: Double?
        get() {
            val bands = listOfNotNull(listeningBand, readingBand, writingBand, speakingBand)
            return if (bands.isEmpty()) null else IeltsScoring.roundToHalfBand(bands.average())
        }
}

data class QuizAttemptSummary(
    val testId: String,
    val book: IeltsBook,
    val testNumber: Int,
    val skill: QuizSkillMode,
    val correctCount: Int,
    val totalCount: Int,
    val band: Double,
    val typeBreakdown: Map<QuestionTypeCategory, Pair<Int, Int>>
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

    fun bandFromRawScore(correct: Int, total: Int): Double {
        if (total <= 0) return 0.0
        val scaled40 = (correct.toDouble() / total.toDouble()) * 40.0
        val rawBand = when {
            scaled40 >= 39.0 -> 9.0
            scaled40 >= 37.0 -> 8.5
            scaled40 >= 35.0 -> 8.0
            scaled40 >= 32.0 -> 7.5
            scaled40 >= 30.0 -> 7.0
            scaled40 >= 26.0 -> 6.5
            scaled40 >= 23.0 -> 6.0
            scaled40 >= 18.0 -> 5.5
            scaled40 >= 16.0 -> 5.0
            scaled40 >= 13.0 -> 4.5
            scaled40 >= 10.0 -> 4.0
            scaled40 >= 6.0 -> 3.5
            scaled40 >= 4.0 -> 3.0
            scaled40 >= 1.0 -> 2.5
            else -> 0.0
        }
        return roundToHalfBand(rawBand)
    }

    fun findWeakestQuestionType(stats: List<QuestionTypeAccuracy>): QuestionTypeAccuracy? {
        return stats
            .filter { it.total > 0 }
            .minWithOrNull(
                compareBy<QuestionTypeAccuracy> { it.accuracyRatio }
                    .thenByDescending { it.total }
                    .thenBy { it.category.ordinal }
            )
    }

    fun aggregateBooks(tests: List<PracticeTest>): List<BookProgressSummary> {
        return IeltsBook.entries.map { book ->
            val bookTests = tests.filter { it.book == book }
            val listeningBands = bookTests.mapNotNull { it.lastListeningBand }
            val readingBands = bookTests.mapNotNull { it.lastReadingBand }
            val writingBands = bookTests.mapNotNull { it.lastWritingBand }
            val speakingBands = bookTests.mapNotNull { it.lastSpeakingBand }

            val allStates = bookTests.flatMap { it.sectionStates() }
            val completedSections = allStates.count { it.status == SectionStatus.COMPLETED }
            val availableSections = allStates.count { it.status != SectionStatus.LOCKED }
            val totalSections = allStates.size

            BookProgressSummary(
                book = book,
                listeningBand = listeningBands.takeIf { it.isNotEmpty() }?.let { roundToHalfBand(it.average()) },
                readingBand = readingBands.takeIf { it.isNotEmpty() }?.let { roundToHalfBand(it.average()) },
                writingBand = writingBands.takeIf { it.isNotEmpty() }?.let { roundToHalfBand(it.average()) },
                speakingBand = speakingBands.takeIf { it.isNotEmpty() }?.let { roundToHalfBand(it.average()) },
                completedSections = completedSections,
                availableSections = availableSections,
                totalSections = totalSections,
                hasSeed = availableSections > 0
            )
        }
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
