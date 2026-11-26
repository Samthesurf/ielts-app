package com.samuel.ielts.ui

import android.content.Context
import android.util.Base64
import com.samuel.ielts.model.CriterionScore
import com.samuel.ielts.model.IeltsBook
import com.samuel.ielts.model.ListeningAudioManifest
import com.samuel.ielts.model.PassageParagraph
import com.samuel.ielts.model.PracticeTest
import com.samuel.ielts.model.QuizQuestion
import com.samuel.ielts.model.QuizQuestionGroup
import com.samuel.ielts.model.QuizSectionBundle
import com.samuel.ielts.model.QuizSkillMode
import com.samuel.ielts.model.ReadingPassage
import com.samuel.ielts.model.SectionStatus
import com.samuel.ielts.model.SpeakingPromptData
import com.samuel.ielts.model.TfngOption
import com.samuel.ielts.model.WritingCriterion
import com.samuel.ielts.model.WritingTaskData
import com.samuel.ielts.model.WritingTaskType
import com.samuel.ielts.model.YnngOption
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class SeededTestBundle(
    val practiceTest: PracticeTest,
    val listeningSections: List<QuizSectionBundle> = emptyList(),
    val readingSections: List<QuizSectionBundle> = emptyList(),
    val writingTask1: WritingTaskData? = null,
    val writingTask1ChartCaption: List<String> = emptyList(),
    val writingTask2: WritingTaskData? = null,
    val speaking: SpeakingPromptData? = null
)

data class SeedCatalog(
    val tests: List<PracticeTest>,
    val bundlesById: Map<String, SeededTestBundle>
)

object SeedCatalogLoader {
    private val romanNumerals = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")
    private val paragraphLabels = listOf("A", "B", "C", "D", "E", "F", "G")

    fun createEmptySkeleton(): SeedCatalog {
        val bundles = mutableListOf<SeededTestBundle>()
        IeltsBook.entries.forEach { book ->
            for (testNo in 1..4) {
                val id = testId(book, testNo)
                bundles.add(
                    SeededTestBundle(
                        practiceTest = PracticeTest(
                            id = id,
                            book = book,
                            testNumber = testNo,
                            readingTitle = "Not available yet",
                            writingTopic = "Not available yet",
                            listeningTitle = "Not available yet",
                            speakingTopic = "Not available yet",
                            listeningStatus = SectionStatus.LOCKED,
                            readingStatus = SectionStatus.LOCKED,
                            writingStatus = SectionStatus.LOCKED,
                            speakingStatus = SectionStatus.LOCKED
                        )
                    )
                )
            }
        }
        return SeedCatalog(
            tests = bundles.map { it.practiceTest },
            bundlesById = bundles.associateBy { it.practiceTest.id }
        )
    }

    fun load(context: Context): SeedCatalog {
        val availableSeedFiles = runCatching {
            context.assets.list("seed")?.toSet().orEmpty()
        }.getOrDefault(emptySet())

        val bundles = mutableListOf<SeededTestBundle>()

        IeltsBook.entries.forEach { book ->
            val codeLower = book.code.lowercase(Locale.US)
            val lrFile = listOf("$codeLower.json", "${codeLower}_full.json")
                .firstOrNull { it in availableSeedFiles }
            val wsFile = "${codeLower}_writing.json"

            val lrByTestNo = if (lrFile != null) {
                parseLrSeedFile(readAssetText(context, "seed/$lrFile"), book)
            } else {
                emptyMap()
            }

            val wsByTestNo = if (wsFile in availableSeedFiles) {
                parseWritingSpeakingSeedFile(readAssetText(context, "seed/$wsFile"), book)
            } else {
                emptyMap()
            }

            val testNumbers = (setOf(1, 2, 3, 4) + lrByTestNo.keys + wsByTestNo.keys).sorted()
            testNumbers.forEach { testNo ->
                val id = testId(book, testNo)
                val lrData = lrByTestNo[testNo]
                val wsData = wsByTestNo[testNo]

                val listeningSections = lrData?.listeningSections.orEmpty()
                val readingSections = lrData?.readingSections.orEmpty()
                val hasListening = listeningSections.any { it.questions.isNotEmpty() }
                val hasReading = readingSections.any { it.questions.isNotEmpty() }
                val hasWriting = wsData?.task1 != null && wsData.task2 != null
                val hasSpeaking = wsData?.speaking != null

                val readingTitle = readingSections
                    .mapNotNull { it.passage?.title?.takeIf(String::isNotBlank) }
                    .firstOrNull()
                    ?: if (hasReading) "Reading Passages 1 to 3" else "Not available yet"

                val writingTopic = wsData?.writingSummary
                    ?.takeIf(String::isNotBlank)
                    ?: if (hasWriting) "Writing Task 1 and Task 2" else "Not available yet"

                val hasFullListeningAudio = hasListening &&
                    ListeningAudioManifest.findAvailableTrack(context, book, testNo) != null

                val listeningTitle = if (hasListening) {
                    val totalQ = listeningSections.sumOf { it.questions.size }
                    if (hasFullListeningAudio) {
                        "Full test with audio ($totalQ questions)"
                    } else {
                        "Sections 1 to 4 ($totalQ questions)"
                    }
                } else {
                    "Not available yet"
                }

                val speakingTopic = wsData?.speaking?.topicSummary
                    ?.takeIf(String::isNotBlank)
                    ?: if (hasSpeaking) "Parts 1 to 3" else "Not available yet"

                val practiceTest = PracticeTest(
                    id = id,
                    book = book,
                    testNumber = testNo,
                    readingTitle = readingTitle,
                    writingTopic = writingTopic,
                    listeningTitle = listeningTitle,
                    speakingTopic = speakingTopic,
                    listeningStatus = if (hasListening) SectionStatus.AVAILABLE else SectionStatus.LOCKED,
                    readingStatus = if (hasReading) SectionStatus.AVAILABLE else SectionStatus.LOCKED,
                    writingStatus = if (hasWriting) SectionStatus.AVAILABLE else SectionStatus.LOCKED,
                    speakingStatus = if (hasSpeaking) SectionStatus.AVAILABLE else SectionStatus.LOCKED,
                    hasFullListeningAudio = hasFullListeningAudio
                )

                bundles.add(
                    SeededTestBundle(
                        practiceTest = practiceTest,
                        listeningSections = listeningSections,
                        readingSections = readingSections,
                        writingTask1 = wsData?.task1,
                        writingTask1ChartCaption = wsData?.task1ChartCaption.orEmpty(),
                        writingTask2 = wsData?.task2,
                        speaking = wsData?.speaking
                    )
                )
            }
        }

        return SeedCatalog(
            tests = bundles.map { it.practiceTest },
            bundlesById = bundles.associateBy { it.practiceTest.id }
        )
    }

    fun loadChartBase64(context: Context, assetPath: String?): String? {
        if (assetPath.isNullOrBlank()) return null
        return runCatching {
            context.assets.open(assetPath).use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty()) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }.getOrNull()
    }

    private fun testId(book: IeltsBook, testNo: Int): String {
        return "${book.code.lowercase(Locale.US)}_t$testNo"
    }

    private fun readAssetText(context: Context, assetPath: String): String? {
        return runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private data class ParsedLrTest(
        val listeningSections: List<QuizSectionBundle>,
        val readingSections: List<QuizSectionBundle>
    )

    private data class ParsedWsTest(
        val task1: WritingTaskData?,
        val task1ChartCaption: List<String>,
        val task2: WritingTaskData?,
        val speaking: SpeakingPromptData?,
        val writingSummary: String
    )

    private data class RawQuestion(
        val qNo: Int,
        val prompt: String,
        val choices: List<String>,
        val answer: String
    )

    private data class RawGroup(
        val section: String,
        val instructions: String,
        val wordLimit: String,
        val imageAsset: String? = null,
        val questions: List<RawQuestion>
    )

    private fun parseLrSeedFile(jsonText: String?, book: IeltsBook): Map<Int, ParsedLrTest> {
        if (jsonText.isNullOrBlank()) return emptyMap()
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return emptyMap()
        val testsArray = root.optJSONArray("tests") ?: return emptyMap()
        val result = mutableMapOf<Int, ParsedLrTest>()

        for (i in 0 until testsArray.length()) {
            val testObj = testsArray.optJSONObject(i) ?: continue
            val testNo = testObj.optInt("testNo", i + 1)
            val id = testId(book, testNo)

            val passagesBySection = mutableMapOf<Int, ReadingPassage>()
            val passagesArr = testObj.optJSONArray("passages") ?: JSONArray()
            for (pIdx in 0 until passagesArr.length()) {
                val pObj = passagesArr.optJSONObject(pIdx) ?: continue
                val secName = pObj.optString("section", "")
                val pNum = extractTrailingNumber(secName) ?: (pIdx + 1)
                val title = cleanText(pObj.optString("title", "")).ifBlank { "Reading Passage $pNum" }
                val rawLines = jsonStringList(pObj.optJSONArray("paragraphs"))
                val paragraphs = normalizeParagraphsAtoG(rawLines, title)
                passagesBySection[pNum] = ReadingPassage(
                    testId = "${id}_r$pNum",
                    title = title,
                    subtitle = "Passage $pNum (${paragraphs.firstOrNull()?.label ?: "A"} to ${paragraphs.lastOrNull()?.label ?: "G"})",
                    paragraphs = paragraphs
                )
            }

            val rawGroups = mutableListOf<RawGroup>()
            val groupsArr = testObj.optJSONArray("groups") ?: JSONArray()
            for (gIdx in 0 until groupsArr.length()) {
                val gObj = groupsArr.optJSONObject(gIdx) ?: continue
                val section = cleanText(gObj.optString("section", ""))
                val instructions = cleanText(gObj.optString("instructions", ""))
                val wordLimit = cleanText(gObj.optString("word_limit", gObj.optString("wordLimit", "")))
                val imageAsset = optNullableString(
                    gObj,
                    "imageAsset",
                    "image_asset",
                    "imagePath",
                    "image_path",
                    "chartImage",
                    "chart_image"
                )
                val qsArr = gObj.optJSONArray("questions") ?: JSONArray()
                val rawQs = mutableListOf<RawQuestion>()
                for (qIdx in 0 until qsArr.length()) {
                    val qObj = qsArr.optJSONObject(qIdx) ?: continue
                    val qNo = qObj.optInt("qNo", qIdx + 1)
                    val prompt = cleanText(qObj.optString("prompt", ""))
                    val choices = jsonStringList(qObj.optJSONArray("choices")).map(::cleanText)
                    val answer = cleanText(qObj.optString("answer", ""))
                    if (answer.isNotEmpty() || prompt.isNotEmpty()) {
                        rawQs.add(RawQuestion(qNo = qNo, prompt = prompt, choices = choices, answer = answer))
                    }
                }
                if (rawQs.isNotEmpty()) {
                    rawGroups.add(
                        RawGroup(
                            section = section,
                            instructions = instructions,
                            wordLimit = wordLimit,
                            imageAsset = imageAsset,
                            questions = rawQs
                        )
                    )
                }
            }

            val listeningByNum = mutableMapOf<Int, MutableList<RawGroup>>()
            val readingByNum = mutableMapOf<Int, MutableList<RawGroup>>()

            rawGroups.forEach { group ->
                val lower = group.section.lowercase(Locale.US)
                val secNum = extractTrailingNumber(group.section) ?: 1
                if (lower.contains("listening")) {
                    listeningByNum.getOrPut(secNum) { mutableListOf() }.add(group)
                } else {
                    readingByNum.getOrPut(secNum) { mutableListOf() }.add(group)
                }
            }

            val listeningSections = (1..4).mapNotNull { secNum ->
                val secGroups = listeningByNum[secNum].orEmpty()
                if (secGroups.isEmpty()) return@mapNotNull null
                val groups = secGroups.mapIndexed { gIdx, g ->
                    val groupId = "${id}_l${secNum}_g$gIdx"
                    val groupQuestions = convertGroupQuestions(
                        prefix = groupId,
                        sectionHint = "Section $secNum",
                        group = g
                    ).sortedBy { it.number }
                    QuizQuestionGroup(
                        id = groupId,
                        instructions = g.instructions,
                        wordLimit = g.wordLimit,
                        imageAsset = g.imageAsset,
                        questions = groupQuestions
                    )
                }.filter { it.questions.isNotEmpty() }
                    .sortedBy { it.questions.first().number }
                val questions = groups.flatMap { it.questions }

                val qRange = questionRangeText(questions)
                QuizSectionBundle(
                    id = "${id}_l$secNum",
                    skill = QuizSkillMode.LISTENING,
                    sectionNumber = secNum,
                    shortLabel = "Section $secNum",
                    title = "Listening Section $secNum ($qRange)",
                    instructions = groups.firstOrNull()?.instructions.orEmpty(),
                    passage = null,
                    questions = questions,
                    groups = groups
                )
            }

            val readingNums = (readingByNum.keys + passagesBySection.keys).sorted()
            val readingSections = readingNums.mapNotNull { pNum ->
                val secGroups = readingByNum[pNum].orEmpty()
                val passage = passagesBySection[pNum] ?: ReadingPassage(
                    testId = "${id}_r$pNum",
                    title = "Reading Passage $pNum",
                    subtitle = "Passage $pNum",
                    paragraphs = listOf(
                        PassageParagraph(
                            label = "A",
                            text = "Passage text is not included for this test. You can still answer and check all questions below."
                        )
                    )
                )
                val groups = secGroups.mapIndexed { gIdx, g ->
                    val groupId = "${id}_r${pNum}_g$gIdx"
                    val groupQuestions = convertGroupQuestions(
                        prefix = groupId,
                        sectionHint = "Passage $pNum",
                        group = g
                    ).sortedBy { it.number }
                    QuizQuestionGroup(
                        id = groupId,
                        instructions = g.instructions,
                        wordLimit = g.wordLimit,
                        imageAsset = g.imageAsset,
                        questions = groupQuestions
                    )
                }.filter { it.questions.isNotEmpty() }
                    .sortedBy { it.questions.first().number }
                val questions = groups.flatMap { it.questions }

                if (questions.isEmpty()) return@mapNotNull null
                val qRange = questionRangeText(questions)
                QuizSectionBundle(
                    id = "${id}_r$pNum",
                    skill = QuizSkillMode.READING,
                    sectionNumber = pNum,
                    shortLabel = "Passage $pNum",
                    title = "Passage $pNum: ${passage.title} ($qRange)",
                    instructions = groups.firstOrNull()?.instructions.orEmpty(),
                    passage = passage,
                    questions = questions,
                    groups = groups
                )
            }

            result[testNo] = ParsedLrTest(
                listeningSections = listeningSections,
                readingSections = readingSections
            )
        }

        return result
    }

    private fun parseWritingSpeakingSeedFile(jsonText: String?, book: IeltsBook): Map<Int, ParsedWsTest> {
        if (jsonText.isNullOrBlank()) return emptyMap()
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return emptyMap()
        val testsArray = root.optJSONArray("tests") ?: return emptyMap()
        val result = mutableMapOf<Int, ParsedWsTest>()

        for (i in 0 until testsArray.length()) {
            val testObj = testsArray.optJSONObject(i) ?: continue
            val testNo = testObj.optInt("testNo", i + 1)
            val id = testId(book, testNo)

            val task1Obj = testObj.optJSONObject("writingTask1")
            val task2Obj = testObj.optJSONObject("writingTask2")
            val speakingObj = testObj.optJSONObject("speaking")

            val task1Prompt = cleanText(task1Obj?.optString("prompt", "").orEmpty())
            val chartImage = task1Obj?.optString("chartImage", "")?.trim()?.takeIf(String::isNotBlank)
            val task1ChartCaption = jsonStringList(task1Obj?.optJSONArray("chartCaption")).map(::cleanText)
            val task2Prompt = cleanText(task2Obj?.optString("prompt", "").orEmpty())

            val task1 = if (task1Prompt.isNotBlank()) {
                WritingTaskData(
                    taskType = WritingTaskType.TASK_1,
                    prompt = task1Prompt,
                    initialDraft = "",
                    scores = emptyList(),
                    inlineComments = emptyList(),
                    band75Rewrite = "",
                    band8Model = "",
                    chartAssetPath = chartImage
                )
            } else {
                null
            }

            val task2 = if (task2Prompt.isNotBlank()) {
                WritingTaskData(
                    taskType = WritingTaskType.TASK_2,
                    prompt = task2Prompt,
                    initialDraft = "",
                    scores = emptyList(),
                    inlineComments = emptyList(),
                    band75Rewrite = "",
                    band8Model = ""
                )
            } else {
                null
            }

            val speaking = if (speakingObj != null) {
                val p1 = cleanText(speakingObj.optString("part1", ""))
                val p2 = cleanText(speakingObj.optString("part2", ""))
                val p3 = cleanText(speakingObj.optString("part3", ""))
                if (p1.isNotBlank() || p2.isNotBlank() || p3.isNotBlank()) {
                    val topicLine = p1.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotBlank() && !it.lowercase(Locale.US).startsWith("familiar topics") }
                        ?: "Parts 1 to 3"
                    SpeakingPromptData(
                        testId = id,
                        topicSummary = topicLine.take(64),
                        part1 = p1,
                        part2 = p2,
                        part3 = p3
                    )
                } else {
                    null
                }
            } else {
                null
            }

            val summary = summarizeWritingTopic(task2Prompt.ifBlank { task1Prompt })
            result[testNo] = ParsedWsTest(
                task1 = task1,
                task1ChartCaption = task1ChartCaption,
                task2 = task2,
                speaking = speaking,
                writingSummary = summary
            )
        }

        return result
    }

    private fun normalizeParagraphsAtoG(rawLines: List<String>, title: String): List<PassageParagraph> {
        val cleanedLines = rawLines.map(::cleanText).filter(String::isNotBlank)
        if (cleanedLines.isEmpty()) {
            return listOf(
                PassageParagraph(
                    label = "A",
                    text = "Passage text for $title is unavailable in this seed file."
                )
            )
        }

        val explicitPattern = Regex("^([A-G])\\.\\s+(.*)$")
        val labeledMatches = cleanedLines.count { explicitPattern.matches(it) }

        if (labeledMatches >= 3) {
            val grouped = mutableListOf<Pair<String, StringBuilder>>()
            var introBuffer = StringBuilder()

            cleanedLines.forEach { line ->
                val match = explicitPattern.matchEntire(line)
                if (match != null) {
                    val letter = match.groupValues[1]
                    val rest = match.groupValues[2].trim()
                    val sb = StringBuilder()
                    if (grouped.isEmpty() && introBuffer.isNotEmpty()) {
                        sb.append(introBuffer.toString().trim()).append("\n\n")
                        introBuffer = StringBuilder()
                    }
                    sb.append(rest)
                    grouped.add(letter to sb)
                } else if (grouped.isEmpty()) {
                    if (introBuffer.isNotEmpty()) introBuffer.append(' ')
                    introBuffer.append(line)
                } else {
                    val current = grouped.last().second
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(line)
                }
            }

            if (grouped.isNotEmpty()) {
                return grouped.map { (label, sb) ->
                    PassageParagraph(label = label, text = sb.toString().trim())
                }
            }
        }

        val targetParagraphCount = when {
            cleanedLines.size <= 7 -> cleanedLines.size
            cleanedLines.size <= 28 -> 5
            else -> paragraphLabels.size
        }

        val chunkSize = ((cleanedLines.size + targetParagraphCount - 1) / targetParagraphCount).coerceAtLeast(1)
        val chunks = cleanedLines.chunked(chunkSize).take(paragraphLabels.size)
        return chunks.mapIndexed { idx, chunk ->
            PassageParagraph(
                label = paragraphLabels[idx],
                text = chunk.joinToString(" ").trim()
            )
        }
    }

    private fun convertGroupQuestions(
        prefix: String,
        sectionHint: String,
        group: RawGroup
    ): List<QuizQuestion> {
        val answers = group.questions.map { it.answer.trim() }
        val normalizedUpper = answers.map { it.uppercase(Locale.US) }

        val isTfng = normalizedUpper.isNotEmpty() &&
            normalizedUpper.all { it in setOf("T", "F", "NG", "TRUE", "FALSE", "NOT GIVEN") } &&
            normalizedUpper.any { it in setOf("T", "F", "TRUE", "FALSE") }

        val isYnng = normalizedUpper.isNotEmpty() &&
            normalizedUpper.all { it in setOf("Y", "N", "NG", "YES", "NO", "NOT GIVEN") } &&
            normalizedUpper.any { it in setOf("Y", "N", "YES", "NO") }

        if (isTfng) {
            return group.questions.mapIndexed { idx, raw ->
                QuizQuestion.TrueFalseNotGiven(
                    id = "${prefix}_q${raw.qNo}_$idx",
                    number = raw.qNo,
                    prompt = raw.prompt.ifBlank { "Statement ${raw.qNo}" },
                    paragraphHint = sectionHint,
                    correctAnswer = parseTfng(raw.answer),
                    instructions = group.instructions
                )
            }
        }

        if (isYnng) {
            return group.questions.mapIndexed { idx, raw ->
                QuizQuestion.YesNoNotGiven(
                    id = "${prefix}_q${raw.qNo}_$idx",
                    number = raw.qNo,
                    prompt = raw.prompt.ifBlank { "Statement ${raw.qNo}" },
                    paragraphHint = sectionHint,
                    correctAnswer = parseYnng(raw.answer),
                    instructions = group.instructions
                )
            }
        }

        val isFillBlank = group.wordLimit.isNotBlank() ||
            answers.any { ans ->
                !isLetterChoiceAnswer(ans) && !isRomanAnswer(ans) && !isMultiLetterAnswer(ans)
            }

        if (isFillBlank) {
            val hint = group.wordLimit.ifBlank { "ONE WORD AND/OR A NUMBER" }
            val maxWords = parseMaxWordsFromHint(hint)
            return group.questions.mapIndexed { idx, raw ->
                val accepted = raw.answer
                    .split("/")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf(raw.answer.trim()) }
                QuizQuestion.FillBlank(
                    id = "${prefix}_q${raw.qNo}_$idx",
                    number = raw.qNo,
                    prompt = raw.prompt.ifBlank { "Complete item ${raw.qNo}." },
                    paragraphHint = sectionHint,
                    wordLimitHint = hint,
                    maxWords = maxWords,
                    acceptedAnswers = accepted,
                    rawAnswerKey = raw.answer.trim(),
                    instructions = group.instructions
                )
            }
        }

        val perQuestionOptions = resolveGroupMcqOptions(group)
        return group.questions.mapIndexed { idx, raw ->
            val resolved = perQuestionOptions[idx]
            val labels = resolved.map { it.first }
            val texts = resolved.map { it.second }

            val answerTokens = raw.answer
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val correctIndices = answerTokens.mapNotNull { token ->
                labels.indexOfFirst { it.equals(token, ignoreCase = true) }.takeIf { it >= 0 }
            }.toSet()

            val requiredSelections = if (answerTokens.size > 1) {
                answerTokens.size
            } else {
                1
            }

            val hintSuffix = if (labels.size >= 2) {
                "$sectionHint (${labels.first()} to ${labels.last()})"
            } else {
                sectionHint
            }

            QuizQuestion.Mcq(
                id = "${prefix}_q${raw.qNo}_$idx",
                number = raw.qNo,
                prompt = raw.prompt.ifBlank { "Question ${raw.qNo}" },
                paragraphHint = hintSuffix,
                options = texts,
                optionLabels = labels,
                correctIndex = correctIndices.firstOrNull() ?: -1,
                correctIndices = correctIndices,
                requiredSelections = requiredSelections,
                instructions = group.instructions
            )
        }
    }

    private fun resolveGroupMcqOptions(group: RawGroup): List<List<Pair<String, String>>> {
        val answers = group.questions.map { it.answer.trim() }
        val isRoman = answers.all { isRomanAnswer(it) }
        if (isRoman) {
            val highestIndex = answers.maxOfOrNull { ans ->
                romanNumerals.indexOf(ans.lowercase(Locale.US)).coerceAtLeast(0)
            } ?: 5
            val count = (highestIndex + 1).coerceIn(5, romanNumerals.size)
            val romanOptions = romanNumerals.take(count).map { r -> r to "Heading $r" }
            return group.questions.map { romanOptions }
        }

        val allTokens = answers.flatMap { it.split(",") }
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.length == 1 && it[0] in 'A'..'J' }

        val maxAnswerLetter = allTokens.maxOfOrNull { it[0] } ?: 'C'
        val instRangeMax = detectLetterRangeMax(group.instructions)
        val defaultMaxLetter = maxOf(maxAnswerLetter, instRangeMax, 'C')

        // Case 1: Every question already has a clean complete choice list (e.g. Choose TWO A-E, or matching A-G)
        val firstChoices = group.questions.firstOrNull()?.choices.orEmpty()
        val hasSharedFullChoices = firstChoices.size >= 3 &&
            group.questions.all { it.choices == firstChoices }
        if (hasSharedFullChoices) {
            val parsed = parseLabeledOptions(firstChoices, defaultMaxLetter)
            return group.questions.map { parsed }
        }

        // Case 2: Sequential OCR accumulation (Q1 has A, Q2 has B,C, Q3 has B,C,A,B,C)
        val blocks = extractSequentialOptionBlocks(group.questions)
        if (blocks.size == group.questions.size && blocks.all { it.size >= 2 }) {
            return blocks.mapIndexed { idx, block ->
                ensureAnswerLabelsPresent(block, group.questions[idx].answer, defaultMaxLetter)
            }
        }

        // Case 3: Fallback per-question or clean letter options (A..maxLetter)
        return group.questions.map { raw ->
            val parsed = parseLabeledOptions(raw.choices, defaultMaxLetter)
            ensureAnswerLabelsPresent(parsed, raw.answer, defaultMaxLetter)
        }
    }

    private fun extractSequentialOptionBlocks(questions: List<RawQuestion>): List<List<Pair<String, String>>> {
        val seen = LinkedHashSet<String>()
        val orderedEntries = mutableListOf<Pair<String, String>>()
        val letterPrefixRegex = Regex("^([A-J])\\.\\s*(.+)$")

        questions.forEach { q ->
            q.choices.forEach { rawChoice ->
                val cleaned = rawChoice.trim()
                if (cleaned.isNotEmpty() && seen.add(cleaned)) {
                    val match = letterPrefixRegex.matchEntire(cleaned)
                    if (match != null) {
                        orderedEntries.add(match.groupValues[1] to match.groupValues[2].trim())
                    }
                }
            }
        }

        if (orderedEntries.isEmpty()) return emptyList()

        val blocks = mutableListOf<MutableList<Pair<String, String>>>()
        orderedEntries.forEach { entry ->
            val letter = entry.first
            val startNewBlock = blocks.isEmpty() ||
                letter == "A" ||
                blocks.last().any { it.first == letter }
            if (startNewBlock) {
                blocks.add(mutableListOf(entry))
            } else {
                blocks.last().add(entry)
            }
        }

        return blocks
    }

    private fun parseLabeledOptions(
        rawChoices: List<String>,
        defaultMaxLetter: Char
    ): List<Pair<String, String>> {
        val letterPrefixRegex = Regex("^([A-J])\\.\\s*(.+)$")
        val extracted = LinkedHashMap<String, String>()
        rawChoices.forEach { choice ->
            val match = letterPrefixRegex.matchEntire(choice.trim())
            if (match != null) {
                val letter = match.groupValues[1]
                val body = match.groupValues[2].trim()
                if (!extracted.containsKey(letter)) {
                    extracted[letter] = body
                }
            }
        }

        if (extracted.size >= 2) {
            return extracted.entries.map { it.key to it.value }
        }

        return ('A'..defaultMaxLetter).map { ch ->
            val label = ch.toString()
            val text = extracted[label] ?: "Option $label"
            label to text
        }
    }

    private fun ensureAnswerLabelsPresent(
        options: List<Pair<String, String>>,
        rawAnswer: String,
        defaultMaxLetter: Char
    ): List<Pair<String, String>> {
        val byLabel = LinkedHashMap<String, String>()
        options.forEach { (label, text) -> byLabel[label.uppercase(Locale.US)] = text }

        val answerLetters = rawAnswer
            .split(",")
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.length == 1 && it[0] in 'A'..'J' }

        val neededMax = maxOf(
            defaultMaxLetter,
            answerLetters.maxOfOrNull { it[0] } ?: 'A',
            byLabel.keys.mapNotNull { it.firstOrNull() }.maxOrNull() ?: 'C'
        )

        val result = mutableListOf<Pair<String, String>>()
        for (ch in 'A'..neededMax) {
            val label = ch.toString()
            val text = byLabel[label] ?: "Option $label"
            result.add(label to text)
        }
        return result
    }

    private fun detectLetterRangeMax(instructions: String): Char {
        val upper = instructions.uppercase(Locale.US)
        val rangeMatch = Regex("\\bA\\s*[-\\u2013\\u2014]?\\s*TO\\s*([C-I])\\b|\\bA-([C-I])\\b").find(upper)
        if (rangeMatch != null) {
            val found = rangeMatch.groupValues[1].ifEmpty { rangeMatch.groupValues[2] }
            if (found.length == 1) return found[0]
        }
        if (upper.contains("A, B, C OR D")) return 'D'
        if (upper.contains("A, B OR C")) return 'C'
        return 'C'
    }

    private fun parseTfng(raw: String): TfngOption {
        return when (raw.trim().uppercase(Locale.US)) {
            "T", "TRUE" -> TfngOption.TRUE
            "F", "FALSE" -> TfngOption.FALSE
            else -> TfngOption.NOT_GIVEN
        }
    }

    private fun parseYnng(raw: String): YnngOption {
        return when (raw.trim().uppercase(Locale.US)) {
            "Y", "YES" -> YnngOption.YES
            "N", "NO" -> YnngOption.NO
            else -> YnngOption.NOT_GIVEN
        }
    }

    private fun parseMaxWordsFromHint(hint: String): Int {
        val upper = hint.uppercase(Locale.US)
        return when {
            upper.contains("THREE WORDS") -> 3
            upper.contains("TWO WORDS") -> 2
            upper.contains("ONE WORD AND/OR A NUMBER") -> 2
            upper.contains("ONE WORD") -> 1
            else -> 2
        }
    }

    private fun isLetterChoiceAnswer(ans: String): Boolean {
        val trimmed = ans.trim().uppercase(Locale.US)
        return trimmed.length == 1 && trimmed[0] in 'A'..'J'
    }

    private fun isMultiLetterAnswer(ans: String): Boolean {
        val parts = ans.split(",").map { it.trim().uppercase(Locale.US) }
        return parts.size >= 2 && parts.all { it.length == 1 && it[0] in 'A'..'J' }
    }

    private fun isRomanAnswer(ans: String): Boolean {
        return ans.trim().lowercase(Locale.US) in romanNumerals
    }

    private fun extractTrailingNumber(text: String): Int? {
        return Regex("(\\d+)\\s*$").find(text.trim())?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun questionRangeText(questions: List<QuizQuestion>): String {
        if (questions.isEmpty()) return "0 Qs"
        val first = questions.first().number
        val last = questions.last().number
        return if (first == last) "Q$first" else "Q$first-$last"
    }

    private fun summarizeWritingTopic(prompt: String): String {
        val cleaned = prompt
            .replace(Regex("^You should spend about \\d+ minutes on this task\\.\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleaned.isBlank()) return "Writing Task 1 and Task 2"
        val firstSentence = cleaned.substringBefore(".").trim()
        return if (firstSentence.length <= 72) firstSentence else firstSentence.take(69) + "..."
    }

    private fun optNullableString(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun jsonStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.optString(i, "")
            if (item.isNotBlank()) list.add(item)
        }
        return list
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace('\u2014', '-')
            .replace('\u2013', '-')
            .trim()
    }
}
