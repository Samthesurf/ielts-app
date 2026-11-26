package com.samuel.ielts.model

import com.samuel.ielts.data.AnswerNormalizer
import java.util.Locale

enum class ListeningExamPhase {
    READY,
    AUDIO_PLAYING,
    AUDIO_PAUSED,
    TRANSFER_TIME,
    CHECKED
}

data class ListeningSectionScore(
    val sectionNumber: Int,
    val shortLabel: String,
    val correct: Int,
    val total: Int
)

data class ListeningExamResult(
    val sectionScores: List<ListeningSectionScore>,
    val totalCorrect: Int,
    val totalQuestions: Int,
    val band: Double,
    val typeBreakdown: Map<QuestionTypeCategory, Pair<Int, Int>>
)

/**
 * Pure Kotlin exam timer, key validation, and scoring rules for full-test Listening mode.
 * Unit-testable on the JVM without Android dependencies.
 */
object ListeningExamEngine {

    fun formatClock(totalSeconds: Int): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val minutes = clamped / 60
        val seconds = clamped % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatRemainingLabel(totalSeconds: Int): String {
        return "${formatClock(totalSeconds)} remaining"
    }

    fun isRedWarning(remainingSeconds: Int): Boolean {
        return remainingSeconds.coerceAtLeast(0) < ListeningAudioManifest.RED_WARNING_THRESHOLD_SECONDS
    }

    fun wholeTestRemainingSeconds(
        phase: ListeningExamPhase,
        audioDurationSeconds: Int,
        audioPositionSeconds: Int,
        transferRemainingSeconds: Int
    ): Int {
        val safeAudioRemaining = (audioDurationSeconds - audioPositionSeconds).coerceAtLeast(0)
        val safeTransfer = transferRemainingSeconds.coerceAtLeast(0)
        return when (phase) {
            ListeningExamPhase.READY -> audioDurationSeconds.coerceAtLeast(0) + safeTransfer
            ListeningExamPhase.AUDIO_PLAYING,
            ListeningExamPhase.AUDIO_PAUSED -> safeAudioRemaining + safeTransfer
            ListeningExamPhase.TRANSFER_TIME,
            ListeningExamPhase.CHECKED -> safeTransfer
        }
    }

    fun canCheckAnswers(
        phase: ListeningExamPhase,
        practiceOverrideEnabled: Boolean
    ): Boolean {
        return phase == ListeningExamPhase.TRANSFER_TIME ||
            phase == ListeningExamPhase.CHECKED ||
            practiceOverrideEnabled
    }

    fun sectionCheckHint(sectionNumber: Int): String {
        return "You have 30 seconds to check Section $sectionNumber"
    }

    /**
     * Returns the non-blank canonical answer key for a question.
     * Throws [IllegalStateException] if the question is missing a valid answer key.
     */
    fun expectedAnswerKey(question: QuizQuestion): String {
        return when (question) {
            is QuizQuestion.FillBlank -> {
                val key = question.rawAnswerKey.trim().ifBlank {
                    question.acceptedAnswers
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString("/")
                }
                if (key.isBlank()) {
                    throw IllegalStateException("Missing answer key for Question ${question.number}")
                }
                key
            }

            is QuizQuestion.Mcq -> {
                if (question.correctIndices.isEmpty()) {
                    throw IllegalStateException("Missing answer key for Question ${question.number}")
                }
                val invalidIndex = question.correctIndices.firstOrNull { it < 0 || it >= question.options.size }
                if (invalidIndex != null) {
                    throw IllegalStateException(
                        "Invalid answer key index $invalidIndex for Question ${question.number}"
                    )
                }
                val key = question.correctIndices
                    .sorted()
                    .joinToString(",") { question.labelForIndex(it).trim() }
                if (key.isBlank()) {
                    throw IllegalStateException("Missing answer key for Question ${question.number}")
                }
                key
            }

            is QuizQuestion.TrueFalseNotGiven -> question.correctAnswer.label
            is QuizQuestion.YesNoNotGiven -> question.correctAnswer.label
        }
    }

    /**
     * Fails loudly if sections are empty or any displayed question lacks a non-blank answer key.
     */
    fun validateQuestionsKeyBacked(sections: List<QuizSectionBundle>) {
        val allQuestions = sections.flatMap { it.questions }
        if (allQuestions.isEmpty()) {
            throw IllegalStateException("Listening test has no questions loaded.")
        }
        allQuestions.forEach { question ->
            expectedAnswerKey(question)
        }
    }

    /**
     * Evaluates a single question strictly through [AnswerNormalizer.matches].
     * Never uses raw exact string equality and fails loudly on missing keys.
     */
    fun isQuestionCorrect(
        question: QuizQuestion,
        mcqAnswers: Map<String, Set<Int>>,
        tfngAnswers: Map<String, TfngOption>,
        ynngAnswers: Map<String, YnngOption>,
        fillAnswers: Map<String, String>
    ): Boolean {
        val expectedKey = expectedAnswerKey(question)
        return when (question) {
            is QuizQuestion.FillBlank -> {
                val given = fillAnswers[question.id].orEmpty().trim()
                if (given.isEmpty()) false else AnswerNormalizer.matches(expectedKey, given)
            }

            is QuizQuestion.Mcq -> {
                val selected = mcqAnswers[question.id].orEmpty()
                if (selected.isEmpty()) return false
                val givenLabels = selected
                    .sorted()
                    .map { question.labelForIndex(it).trim() }
                val expectedLabels = question.correctIndices
                    .sorted()
                    .map { question.labelForIndex(it).trim() }
                if (givenLabels.size != expectedLabels.size) return false
                givenLabels.zip(expectedLabels).all { (given, expected) ->
                    AnswerNormalizer.matches(expected, given)
                }
            }

            is QuizQuestion.TrueFalseNotGiven -> {
                val selected = tfngAnswers[question.id] ?: return false
                AnswerNormalizer.matches(expectedKey, selected.label)
            }

            is QuizQuestion.YesNoNotGiven -> {
                val selected = ynngAnswers[question.id] ?: return false
                AnswerNormalizer.matches(expectedKey, selected.label)
            }
        }
    }

    fun scoreExam(
        sections: List<QuizSectionBundle>,
        mcqAnswers: Map<String, Set<Int>>,
        tfngAnswers: Map<String, TfngOption>,
        ynngAnswers: Map<String, YnngOption>,
        fillAnswers: Map<String, String>
    ): ListeningExamResult {
        validateQuestionsKeyBacked(sections)

        val sectionScores = sections.map { section ->
            val correct = section.questions.count { q ->
                isQuestionCorrect(q, mcqAnswers, tfngAnswers, ynngAnswers, fillAnswers)
            }
            ListeningSectionScore(
                sectionNumber = section.sectionNumber,
                shortLabel = section.shortLabel,
                correct = correct,
                total = section.questions.size
            )
        }

        val allQuestions = sections.flatMap { it.questions }
        val totalCorrect = sectionScores.sumOf { it.correct }
        val totalQuestions = allQuestions.size
        val band = IeltsScoring.bandFromRawScore(totalCorrect, totalQuestions)

        val breakdown = linkedMapOf<QuestionTypeCategory, Pair<Int, Int>>()
        QuestionTypeCategory.entries.forEach { category ->
            val matching = allQuestions.filter { it.category == category }
            if (matching.isNotEmpty()) {
                val correct = matching.count { q ->
                    isQuestionCorrect(q, mcqAnswers, tfngAnswers, ynngAnswers, fillAnswers)
                }
                breakdown[category] = correct to matching.size
            }
        }

        return ListeningExamResult(
            sectionScores = sectionScores,
            totalCorrect = totalCorrect,
            totalQuestions = totalQuestions,
            band = band,
            typeBreakdown = breakdown
        )
    }
}
