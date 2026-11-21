package com.samuel.ielts.model

enum class NextStepDestination {
    LIBRARY,
    QUIZ_LISTENING,
    QUIZ_READING,
    WRITING,
    SPEAKING
}

data class NextStepCardData(
    val id: String,
    val skill: IeltsSkill,
    val title: String,
    val subtitle: String,
    val reasonTag: String,
    val actionLabel: String,
    val destination: NextStepDestination,
    val targetTestId: String? = null,
    val isTopPriority: Boolean = false
)

object NextStepRecommender {

    fun buildNextSteps(
        tests: List<PracticeTest>,
        quizAttempts: List<QuizAttemptSummary>,
        questionTypeStats: List<QuestionTypeAccuracy>,
        writingCriteria: List<SkillProgress>,
        recentRecords: List<TestProgressRecord>
    ): List<NextStepCardData> {
        val weakestType = IeltsScoring.findWeakestQuestionType(questionTypeStats)
        val weakestWritingCriterion = writingCriteria
            .filter { it.band > 0.0 }
            .minByOrNull { it.band }

        val listeningAvg = averageBandFor(tests.mapNotNull { it.lastListeningBand })
        val readingAvg = averageBandFor(tests.mapNotNull { it.lastReadingBand })
        val writingAvg = averageBandFor(tests.mapNotNull { it.lastWritingBand })
        val speakingAvg = averageBandFor(tests.mapNotNull { it.lastSpeakingBand })

        val readingTarget = pickTargetTest(tests, IeltsSkill.READING)
        val writingTarget = pickTargetTest(tests, IeltsSkill.WRITING)
        val speakingTarget = pickTargetTest(tests, IeltsSkill.SPEAKING)
        val listeningTarget = pickTargetTest(tests, IeltsSkill.LISTENING)

        val readingCard = buildReadingNextStep(
            targetTest = readingTarget,
            weakestType = weakestType,
            readingAvg = readingAvg,
            recentReadingAttempts = quizAttempts.count { it.skill == QuizSkillMode.READING }
        )

        val writingCard = buildWritingNextStep(
            targetTest = writingTarget,
            weakestCriterion = weakestWritingCriterion,
            writingAvg = writingAvg
        )

        val speakingCard = buildSpeakingNextStep(
            targetTest = speakingTarget,
            speakingAvg = speakingAvg,
            recentRecords = recentRecords
        )

        val listeningCard = buildListeningNextStep(
            targetTest = listeningTarget,
            weakestType = weakestType,
            listeningAvg = listeningAvg,
            recentListeningAttempts = quizAttempts.count { it.skill == QuizSkillMode.LISTENING }
        )

        val scored = listOf(
            readingCard to skillUrgencyScore(
                band = readingAvg,
                weakestBonus = if (weakestType != null && weakestType.accuracyPercent < 75) 2.5 else 1.2,
                hasSeed = readingTarget != null
            ),
            writingCard to skillUrgencyScore(
                band = writingAvg,
                weakestBonus = if (weakestWritingCriterion != null && weakestWritingCriterion.band < 7.0) 2.4 else 1.3,
                hasSeed = writingTarget != null
            ),
            speakingCard to skillUrgencyScore(
                band = speakingAvg,
                weakestBonus = if (speakingAvg != null && speakingAvg < 6.5) 2.2 else 1.0,
                hasSeed = speakingTarget != null
            ),
            listeningCard to skillUrgencyScore(
                band = listeningAvg,
                weakestBonus = if (weakestType?.category == QuestionTypeCategory.FILL_BLANK) 2.0 else 0.9,
                hasSeed = listeningTarget != null
            )
        )

        return scored
            .sortedByDescending { it.second }
            .mapIndexed { index, pair ->
                pair.first.copy(isTopPriority = index == 0)
            }
    }

    private fun buildReadingNextStep(
        targetTest: PracticeTest?,
        weakestType: QuestionTypeAccuracy?,
        readingAvg: Double?,
        recentReadingAttempts: Int
    ): NextStepCardData {
        val targetLabel = targetTest?.let { "${it.book.code} Test ${it.testNumber}" } ?: "Library"
        val drillTitle = when (weakestType?.category) {
            QuestionTypeCategory.TFNG -> "Reading T/F/NG Drill"
            QuestionTypeCategory.YNNG -> "Reading Y/N/NG Drill"
            QuestionTypeCategory.MCQ -> "Reading MCQ Elimination Drill"
            QuestionTypeCategory.FILL_BLANK -> "Reading Fill-Blank Word Drill"
            null -> "Reading T/F/NG Starter Drill"
        }
        val reason = when {
            weakestType != null -> "Weakest: ${weakestType.category.tag} (${weakestType.accuracyPercent}%)"
            readingAvg != null -> "Avg Band ${IeltsScoring.formatBand(readingAvg)}"
            else -> "High impact skill"
        }
        val subtitle = when {
            weakestType != null -> "Practice ${weakestType.category.label} in $targetLabel to lift ${weakestType.correct}/${weakestType.total} accuracy."
            recentReadingAttempts == 0 -> "Start $targetLabel Reading Passage 1 to calibrate your T/F/NG and Fill-Blank baseline."
            else -> "Continue $targetLabel Reading passages and check paragraph evidence."
        }
        return NextStepCardData(
            id = "next_reading",
            skill = IeltsSkill.READING,
            title = drillTitle,
            subtitle = subtitle,
            reasonTag = reason,
            actionLabel = if (targetTest != null) "Open Quiz" else "Library",
            destination = if (targetTest != null) NextStepDestination.QUIZ_READING else NextStepDestination.LIBRARY,
            targetTestId = targetTest?.id
        )
    }

    private fun buildWritingNextStep(
        targetTest: PracticeTest?,
        weakestCriterion: SkillProgress?,
        writingAvg: Double?
    ): NextStepCardData {
        val targetLabel = targetTest?.let { "${it.book.code} Test ${it.testNumber}" } ?: "Writing"
        val title = when {
            weakestCriterion != null -> "Writing Task 2 Rewrite (${weakestCriterion.label})"
            else -> "Writing Task 2 Rewrite"
        }
        val reason = when {
            weakestCriterion != null -> "Lowest: ${weakestCriterion.label} ${IeltsScoring.formatBand(weakestCriterion.band)}"
            writingAvg != null -> "Avg Band ${IeltsScoring.formatBand(writingAvg)}"
            else -> "2x weight in Writing"
        }
        val subtitle = when {
            weakestCriterion != null -> "Revise your draft in $targetLabel targeting ${weakestCriterion.label} and compare with the Band 7.5 rewrite."
            else -> "Draft Task 2 (250+ words) in $targetLabel for examiner scoring on TR, CC, LR, and GRA."
        }
        return NextStepCardData(
            id = "next_writing",
            skill = IeltsSkill.WRITING,
            title = title,
            subtitle = subtitle,
            reasonTag = reason,
            actionLabel = "Open Writing",
            destination = NextStepDestination.WRITING,
            targetTestId = targetTest?.id
        )
    }

    private fun buildSpeakingNextStep(
        targetTest: PracticeTest?,
        speakingAvg: Double?,
        recentRecords: List<TestProgressRecord>
    ): NextStepCardData {
        val targetLabel = targetTest?.let { "${it.book.code} Test ${it.testNumber}" } ?: "Speaking"
        val hasRecentSpeaking = recentRecords.any { it.label.endsWith(" S") }
        val reason = when {
            speakingAvg != null -> "Avg Band ${IeltsScoring.formatBand(speakingAvg)}"
            !hasRecentSpeaking -> "No speaking log yet"
            else -> "Part 2 fluency focus"
        }
        val subtitle = if (targetTest != null) {
            "Rehearse your 2-minute Part 2 cue card story in $targetLabel and log your self-rated band."
        } else {
            "Review Part 1 to 3 speaking prompts and prepare your universal Part 2 story."
        }
        return NextStepCardData(
            id = "next_speaking",
            skill = IeltsSkill.SPEAKING,
            title = "Speaking Part 2 Story",
            subtitle = subtitle,
            reasonTag = reason,
            actionLabel = "Open Speaking",
            destination = NextStepDestination.SPEAKING,
            targetTestId = targetTest?.id
        )
    }

    private fun buildListeningNextStep(
        targetTest: PracticeTest?,
        weakestType: QuestionTypeAccuracy?,
        listeningAvg: Double?,
        recentListeningAttempts: Int
    ): NextStepCardData {
        val targetLabel = targetTest?.let { "${it.book.code} Test ${it.testNumber}" } ?: "Library"
        val reason = when {
            listeningAvg != null -> "Avg Band ${IeltsScoring.formatBand(listeningAvg)}"
            weakestType?.category == QuestionTypeCategory.FILL_BLANK -> "Spelling and word limit focus"
            recentListeningAttempts == 0 -> "Not attempted yet"
            else -> "Steady practice"
        }
        val subtitle = if (targetTest != null) {
            "Drill $targetLabel Listening Sections 1 to 4 with focus on exact spelling and word hints."
        } else {
            "Pick an unlocked Listening test in Library to build section accuracy."
        }
        return NextStepCardData(
            id = "next_listening",
            skill = IeltsSkill.LISTENING,
            title = "Listening Section Drill",
            subtitle = subtitle,
            reasonTag = reason,
            actionLabel = if (targetTest != null) "Open Quiz" else "Library",
            destination = if (targetTest != null) NextStepDestination.QUIZ_LISTENING else NextStepDestination.LIBRARY,
            targetTestId = targetTest?.id
        )
    }

    private fun pickTargetTest(tests: List<PracticeTest>, skill: IeltsSkill): PracticeTest? {
        val statusOf: (PracticeTest) -> SectionStatus = { test ->
            when (skill) {
                IeltsSkill.LISTENING -> test.listeningStatus
                IeltsSkill.READING -> test.readingStatus
                IeltsSkill.WRITING -> test.writingStatus
                IeltsSkill.SPEAKING -> test.speakingStatus
            }
        }
        val bandOf: (PracticeTest) -> Double? = { test ->
            when (skill) {
                IeltsSkill.LISTENING -> test.lastListeningBand
                IeltsSkill.READING -> test.lastReadingBand
                IeltsSkill.WRITING -> test.lastWritingBand
                IeltsSkill.SPEAKING -> test.lastSpeakingBand
            }
        }

        return tests.firstOrNull { statusOf(it) == SectionStatus.AVAILABLE }
            ?: tests.filter { statusOf(it) == SectionStatus.COMPLETED }.minByOrNull { bandOf(it) ?: 9.0 }
            ?: tests.firstOrNull { statusOf(it) != SectionStatus.LOCKED }
    }

    private fun averageBandFor(bands: List<Double>): Double? {
        if (bands.isEmpty()) return null
        return IeltsScoring.roundToHalfBand(bands.average())
    }

    private fun skillUrgencyScore(
        band: Double?,
        weakestBonus: Double,
        hasSeed: Boolean
    ): Double {
        val seedAdjustment = if (hasSeed) 0.0 else -5.0
        val bandPenalty = if (band != null) (9.5 - band) else 3.2
        return bandPenalty + weakestBonus + seedAdjustment
    }
}
