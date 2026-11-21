package com.samuel.ielts.ui.screens

import com.samuel.ielts.model.QuizQuestion
import com.samuel.ielts.model.QuizQuestionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizGroupVisualTest {

    private fun sampleMcq(number: Int, instructions: String = ""): QuizQuestion.Mcq {
        return QuizQuestion.Mcq(
            id = "q_$number",
            number = number,
            prompt = "Location $number",
            paragraphHint = "Section 2",
            options = listOf("Option A", "Option B", "Option C"),
            correctIndex = 0,
            instructions = instructions
        )
    }

    @Test
    fun detectsMapPlanAndDiagramRubrics() {
        val mapGroup = QuizQuestionGroup(
            id = "g1",
            instructions = "Label the map below. Drag the correct letter, A-G, next to Questions 17-20.",
            questions = (17..20).map { sampleMcq(it) }
        )
        val planGroup = QuizQuestionGroup(
            id = "g2",
            instructions = "Label the plan below. Write the correct letter, A-H, next to Questions 15-18.",
            questions = (15..18).map { sampleMcq(it) }
        )
        val floorPlanGroup = QuizQuestionGroup(
            id = "g3",
            instructions = "Label the floor plan below.",
            questions = listOf(sampleMcq(11), sampleMcq(12))
        )
        val diagramGroup = QuizQuestionGroup(
            id = "g4",
            instructions = "Complete the diagram below. Write ONE WORD ONLY for each answer.",
            questions = listOf(sampleMcq(8), sampleMcq(9))
        )

        assertTrue(isMapOrPlanGroup(mapGroup))
        assertTrue(isMapOrPlanGroup(planGroup))
        assertTrue(isMapOrPlanGroup(floorPlanGroup))
        assertTrue(isMapOrPlanGroup(diagramGroup))
    }

    @Test
    fun ignoresNonVisualPlanWordsAndNormalGroups() {
        val recoveryPlanGroup = QuizQuestionGroup(
            id = "g1",
            instructions = "Do the following statements agree with the information given in Reading Passage 1?",
            questions = listOf(
                sampleMcq(6, instructions = "Do the following statements agree with the information given?")
            )
        )
        val verbPlanGroup = QuizQuestionGroup(
            id = "g2",
            instructions = "Choose the correct letter, A, B or C.",
            questions = listOf(sampleMcq(16))
        )
        val treePlantingGroup = QuizQuestionGroup(
            id = "g3",
            instructions = "Complete the notes below. Write ONE WORD ONLY. Tree planting.",
            questions = listOf(sampleMcq(31))
        )
        val summaryWithPlanWord = QuizQuestionGroup(
            id = "g4",
            instructions = "Complete the summary below. Peter Bellerby's plan to make a globe.",
            questions = listOf(sampleMcq(27))
        )

        assertFalse(isMapOrPlanGroup(recoveryPlanGroup))
        assertFalse(isMapOrPlanGroup(verbPlanGroup))
        assertFalse(isMapOrPlanGroup(treePlantingGroup))
        assertFalse(isMapOrPlanGroup(summaryWithPlanWord))
    }

    @Test
    fun formatsCaptionsDescriptionsAndPlaceholdersWithoutEmDashes() {
        val mapGroup = QuizQuestionGroup(
            id = "g1",
            instructions = "Label the map below. Write the correct letter, A-G, next to Questions 17-20.",
            imageAsset = "images/c20/t3_l2_map.png",
            questions = (17..20).map { sampleMcq(it) }
        )
        val genericGroup = QuizQuestionGroup(
            id = "g2",
            instructions = "Answer the questions below.",
            imageAsset = "images/c20/t1_r1_fig.png",
            questions = listOf(sampleMcq(5))
        )

        val caption = groupImageCaption(mapGroup)
        val thumbDesc = groupThumbnailContentDescription(mapGroup, "Section 2")
        val dialogTitle = groupZoomDialogTitle(mapGroup)
        val zoomDesc = groupZoomImageContentDescription(mapGroup, "Section 2")
        val placeholder = missingGroupImageMessage(mapGroup)

        assertEquals("Map for Questions 17-20", caption)
        assertEquals("Map for Questions 17-20 in Section 2. Tap to zoom.", thumbDesc)
        assertEquals("Map (Questions 17-20)", dialogTitle)
        assertEquals("Enlarged map for Questions 17-20 in Section 2", zoomDesc)
        assertEquals("Map image not available yet (Questions 17-20)", placeholder)

        assertEquals("Image for Question 5", groupImageCaption(genericGroup))
        assertEquals("Image not available yet (Question 5)", missingGroupImageMessage(genericGroup))

        listOf(caption, thumbDesc, dialogTitle, zoomDesc, placeholder).forEach { text ->
            assertFalse("Unexpected em dash in: $text", text.contains('\u2014'))
        }
    }
}
