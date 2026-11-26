package com.samuel.ielts.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ListeningExamTest {

    @Test
    fun manifestMapsOnlyC21Test1AndTest2() {
        val t1 = ListeningAudioManifest.findTrack(IeltsBook.C21, 1)
        val t2 = ListeningAudioManifest.findTrack(IeltsBook.C21, 2)

        assertNotNull(t1)
        assertEquals("audio/c21_t1.mp3", t1!!.assetPath)
        assertEquals("asset:///audio/c21_t1.mp3", t1.assetUri)
        assertEquals(1856, t1.durationSeconds)
        assertEquals(600, t1.transferTimeSeconds)
        assertEquals(2456, t1.totalExamDurationSeconds)

        assertNotNull(t2)
        assertEquals("audio/c21_t2.mp3", t2!!.assetPath)
        assertEquals("asset:///audio/c21_t2.mp3", t2.assetUri)
        assertEquals(1761, t2.durationSeconds)
        assertEquals(600, t2.transferTimeSeconds)
        assertEquals(2361, t2.totalExamDurationSeconds)

        assertNull(ListeningAudioManifest.findTrack(IeltsBook.C21, 3))
        assertNull(ListeningAudioManifest.findTrack(IeltsBook.C21, 4))
        assertNull(ListeningAudioManifest.findTrack(IeltsBook.C20, 1))
        assertNull(ListeningAudioManifest.findTrack(IeltsBook.C19, 1))
    }

    @Test
    fun timerFormattingRedThresholdAndCheckUnlockFollowExamRules() {
        assertEquals("40:56 remaining", ListeningExamEngine.formatRemainingLabel(2456))
        assertEquals("05:00 remaining", ListeningExamEngine.formatRemainingLabel(300))
        assertEquals("04:59 remaining", ListeningExamEngine.formatRemainingLabel(299))
        assertEquals("00:00 remaining", ListeningExamEngine.formatRemainingLabel(-5))

        assertFalse(ListeningExamEngine.isRedWarning(300))
        assertTrue(ListeningExamEngine.isRedWarning(299))
        assertTrue(ListeningExamEngine.isRedWarning(0))

        assertEquals(
            2456,
            ListeningExamEngine.wholeTestRemainingSeconds(
                phase = ListeningExamPhase.READY,
                audioDurationSeconds = 1856,
                audioPositionSeconds = 0,
                transferRemainingSeconds = 600
            )
        )
        assertEquals(
            1456,
            ListeningExamEngine.wholeTestRemainingSeconds(
                phase = ListeningExamPhase.AUDIO_PLAYING,
                audioDurationSeconds = 1856,
                audioPositionSeconds = 1000,
                transferRemainingSeconds = 600
            )
        )
        assertEquals(
            420,
            ListeningExamEngine.wholeTestRemainingSeconds(
                phase = ListeningExamPhase.TRANSFER_TIME,
                audioDurationSeconds = 1856,
                audioPositionSeconds = 1856,
                transferRemainingSeconds = 420
            )
        )

        assertFalse(ListeningExamEngine.canCheckAnswers(ListeningExamPhase.READY, practiceOverrideEnabled = false))
        assertFalse(ListeningExamEngine.canCheckAnswers(ListeningExamPhase.AUDIO_PLAYING, practiceOverrideEnabled = false))
        assertFalse(ListeningExamEngine.canCheckAnswers(ListeningExamPhase.AUDIO_PAUSED, practiceOverrideEnabled = false))
        assertTrue(ListeningExamEngine.canCheckAnswers(ListeningExamPhase.TRANSFER_TIME, practiceOverrideEnabled = false))
        assertTrue(ListeningExamEngine.canCheckAnswers(ListeningExamPhase.AUDIO_PLAYING, practiceOverrideEnabled = true))
    }

    @Test
    fun blankAnswerKeysFailLoudly() {
        val blankFill = QuizQuestion.FillBlank(
            id = "q1",
            number = 1,
            prompt = "Test blank",
            paragraphHint = "Section 1",
            wordLimitHint = "ONE WORD",
            maxWords = 1,
            acceptedAnswers = emptyList(),
            rawAnswerKey = ""
        )
        val blankMcq = QuizQuestion.Mcq(
            id = "q2",
            number = 2,
            prompt = "Test mcq",
            paragraphHint = "Section 2",
            options = listOf("Alpha", "Beta"),
            correctIndex = -1,
            correctIndices = emptySet()
        )

        val fillSection = QuizSectionBundle(
            id = "s1",
            skill = QuizSkillMode.LISTENING,
            sectionNumber = 1,
            shortLabel = "Section 1",
            title = "Listening Section 1",
            questions = listOf(blankFill)
        )
        val mcqSection = QuizSectionBundle(
            id = "s2",
            skill = QuizSkillMode.LISTENING,
            sectionNumber = 2,
            shortLabel = "Section 2",
            title = "Listening Section 2",
            questions = listOf(blankMcq)
        )

        try {
            ListeningExamEngine.validateQuestionsKeyBacked(listOf(fillSection))
            fail("Expected blank FillBlank answer key to throw IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Question 1"))
        }

        try {
            ListeningExamEngine.validateQuestionsKeyBacked(listOf(mcqSection))
            fail("Expected empty Mcq answer key to throw IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Question 2"))
        }

        try {
            ListeningExamEngine.validateQuestionsKeyBacked(emptyList())
            fail("Expected empty listening sections to throw IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("no questions"))
        }
    }

    @Test
    fun scoringUsesAnswerNormalizerAndComputesSectionScoresAndBand() {
        val q1 = QuizQuestion.FillBlank(
            id = "q1",
            number = 1,
            prompt = "Number of people",
            paragraphHint = "Section 1",
            wordLimitHint = "ONE WORD AND/OR A NUMBER",
            maxWords = 2,
            acceptedAnswers = listOf("10", "ten"),
            rawAnswerKey = "10/ten"
        )
        val q2 = QuizQuestion.FillBlank(
            id = "q2",
            number = 2,
            prompt = "Material",
            paragraphHint = "Section 1",
            wordLimitHint = "ONE WORD ONLY",
            maxWords = 1,
            acceptedAnswers = listOf("metal(s)"),
            rawAnswerKey = "metal(s)"
        )
        val q3 = QuizQuestion.Mcq(
            id = "q3",
            number = 11,
            prompt = "Choose A, B or C",
            paragraphHint = "Section 2",
            options = listOf("First", "Second", "Third"),
            optionLabels = listOf("A", "B", "C"),
            correctIndex = 1,
            correctIndices = setOf(1),
            requiredSelections = 1
        )
        val q4 = QuizQuestion.Mcq(
            id = "q4",
            number = 21,
            prompt = "Choose TWO letters, A-E",
            paragraphHint = "Section 3",
            options = listOf("A opt", "B opt", "C opt", "D opt", "E opt"),
            optionLabels = listOf("A", "B", "C", "D", "E"),
            correctIndex = 1,
            correctIndices = setOf(1, 3),
            requiredSelections = 2
        )

        val sections = listOf(
            QuizSectionBundle(
                id = "s1",
                skill = QuizSkillMode.LISTENING,
                sectionNumber = 1,
                shortLabel = "Section 1",
                title = "Listening Section 1",
                questions = listOf(q1, q2)
            ),
            QuizSectionBundle(
                id = "s2",
                skill = QuizSkillMode.LISTENING,
                sectionNumber = 2,
                shortLabel = "Section 2",
                title = "Listening Section 2",
                questions = listOf(q3)
            ),
            QuizSectionBundle(
                id = "s3",
                skill = QuizSkillMode.LISTENING,
                sectionNumber = 3,
                shortLabel = "Section 3",
                title = "Listening Section 3",
                questions = listOf(q4)
            )
        )

        val result = ListeningExamEngine.scoreExam(
            sections = sections,
            mcqAnswers = mapOf(
                "q3" to setOf(1),
                "q4" to setOf(3, 1)
            ),
            tfngAnswers = emptyMap(),
            ynngAnswers = emptyMap(),
            fillAnswers = mapOf(
                "q1" to "TEN",
                "q2" to "metals"
            )
        )

        assertEquals(4, result.totalCorrect)
        assertEquals(4, result.totalQuestions)
        assertEquals(9.0, result.band, 0.001)
        assertEquals(2, result.sectionScores[0].correct)
        assertEquals(1, result.sectionScores[1].correct)
        assertEquals(1, result.sectionScores[2].correct)

        val partial = ListeningExamEngine.scoreExam(
            sections = sections,
            mcqAnswers = mapOf("q3" to setOf(0), "q4" to setOf(1)),
            tfngAnswers = emptyMap(),
            ynngAnswers = emptyMap(),
            fillAnswers = mapOf("q1" to "10", "q2" to "wrong")
        )
        assertEquals(1, partial.totalCorrect)
        assertEquals(4.0, partial.band, 0.001)
    }

    @Test
    fun bundledAudioAssetsAndC21SeedKeysExistAndHaveNoEmDashesInCopy() {
        val projectRoot = resolveAppDir()
        val audioDir = File(projectRoot, "src/main/assets/audio")
        val t1 = File(audioDir, "c21_t1.mp3")
        val t2 = File(audioDir, "c21_t2.mp3")

        assertTrue("Expected bundled audio ${t1.path}", t1.isFile && t1.length() > 1_000_000L)
        assertTrue("Expected bundled audio ${t2.path}", t2.isFile && t2.length() > 1_000_000L)
        assertTrue("Compressed c21_t1.mp3 should stay under 16MB", t1.length() < 16L * 1024L * 1024L)
        assertTrue("Compressed c21_t2.mp3 should stay under 16MB", t2.length() < 16L * 1024L * 1024L)

        val listeningScreenSrc = File(projectRoot, "src/main/java/com/samuel/ielts/ui/screens/ListeningTestScreen.kt")
            .readText()
        assertFalse("No em dash allowed in ListeningTestScreen", listeningScreenSrc.contains('\u2014'))
        assertFalse("No en dash allowed in ListeningTestScreen", listeningScreenSrc.contains('\u2013'))

        val engineSrc = File(projectRoot, "src/main/java/com/samuel/ielts/model/ListeningExamEngine.kt")
            .readText()
        assertFalse("No em dash allowed in ListeningExamEngine", engineSrc.contains('\u2014'))
        assertFalse("No en dash allowed in ListeningExamEngine", engineSrc.contains('\u2013'))
    }

    private fun resolveAppDir(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "src/main/assets").isDirectory) cwd else File(cwd, "app")
    }
}
