package com.samuel.ielts.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.samuel.ielts.data.Attempt
import com.samuel.ielts.data.IeltsDb
import com.samuel.ielts.model.CriterionScore
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.PracticeTest
import com.samuel.ielts.model.QuestionTypeAccuracy
import com.samuel.ielts.model.QuestionTypeCategory
import com.samuel.ielts.model.QuizAttemptSummary
import com.samuel.ielts.model.QuizSkillMode
import com.samuel.ielts.model.SectionStatus
import com.samuel.ielts.model.SkillProgress
import com.samuel.ielts.model.TestProgressRecord
import com.samuel.ielts.model.WritingCriterion
import com.samuel.ielts.model.WritingTaskData
import com.samuel.ielts.model.WritingTaskType
import com.samuel.ielts.ui.screens.DEFAULT_API_BASE_URL
import com.samuel.ielts.ui.screens.DEFAULT_API_MODEL
import com.samuel.ielts.ui.screens.LibraryScreen
import com.samuel.ielts.ui.screens.MarkResult
import com.samuel.ielts.ui.screens.ParseError
import com.samuel.ielts.ui.screens.ProgressScreen
import com.samuel.ielts.ui.screens.QuizScreen
import com.samuel.ielts.ui.screens.SettingsScreen
import com.samuel.ielts.ui.screens.SettingsTestState
import com.samuel.ielts.ui.screens.SpeakingPracticeScreen
import com.samuel.ielts.ui.screens.WritingScreen
import com.samuel.ielts.ui.screens.parseWritingMarkJson
import com.samuel.ielts.ui.screens.toFriendlyNetworkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object IeltsRoutes {
    const val LIBRARY = "library"
    const val QUIZ = "quiz"
    const val WRITING = "writing"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val SPEAKING = "speaking"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TopLevelDestinations = listOf(
    NavDestination(IeltsRoutes.LIBRARY, "Library", Icons.Default.Home),
    NavDestination(IeltsRoutes.QUIZ, "Quiz", Icons.Default.PlayArrow),
    NavDestination(IeltsRoutes.WRITING, "Writing", Icons.Default.Edit),
    NavDestination(IeltsRoutes.PROGRESS, "Progress", Icons.Default.Star),
    NavDestination(IeltsRoutes.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun IeltsApp(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = rememberSaveableStateHolder()
    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    var catalog by remember { mutableStateOf(SeedCatalogLoader.createEmptySkeleton()) }
    var practiceTests by remember { mutableStateOf(catalog.tests) }
    var currentRoute by rememberSaveable { mutableStateOf(IeltsRoutes.LIBRARY) }
    var selectedTestId by rememberSaveable { mutableStateOf(practiceTests.first().id) }
    var selectedQuizSkill by rememberSaveable { mutableStateOf(QuizSkillMode.READING) }

    var quizAttempts by remember { mutableStateOf<List<QuizAttemptSummary>>(emptyList()) }
    var recentRecords by remember { mutableStateOf<List<TestProgressRecord>>(emptyList()) }
    var writingMarkHistory by remember { mutableStateOf<List<MarkResult>>(emptyList()) }

    var baseUrl by rememberSaveable { mutableStateOf(DEFAULT_API_BASE_URL) }
    // Pre-filled from gitignored meta.env at build time; user can still
    // replace or clear it in Settings. Never logged.
    var apiKey by rememberSaveable { mutableStateOf(com.samuel.ielts.BuildConfig.META_API_KEY_DEFAULT) }
    var model by rememberSaveable { mutableStateOf(DEFAULT_API_MODEL) }
    var settingsTestState by remember { mutableStateOf<SettingsTestState>(SettingsTestState.Idle) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            SeedCatalogLoader.load(context)
        }
        catalog = loaded
        practiceTests = loaded.tests
        if (loaded.tests.none { it.id == selectedTestId } && loaded.tests.isNotEmpty()) {
            selectedTestId = loaded.tests.first().id
        }
    }

    val selectedTest = practiceTests.firstOrNull { it.id == selectedTestId } ?: practiceTests.first()
    val selectedBundle = catalog.bundlesById[selectedTest.id]
    val selectedTestLabel = "${selectedTest.book.code} Test ${selectedTest.testNumber}"
    val selectedQuestionId = remember(selectedTest.id, practiceTests.size) {
        (practiceTests.indexOfFirst { it.id == selectedTest.id }.coerceAtLeast(0) + 1).toLong()
    }

    val task1Data = remember(selectedTest.id, selectedBundle) {
        selectedBundle?.writingTask1 ?: buildFallbackTask1Data(selectedTest)
    }
    val task2Data = remember(selectedTest.id, selectedBundle) {
        selectedBundle?.writingTask2 ?: buildFallbackTask2Data(selectedTest)
    }

    var task1ChartBase64 by remember(task1Data.chartAssetPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(task1Data.chartAssetPath) {
        task1ChartBase64 = withContext(Dispatchers.IO) {
            SeedCatalogLoader.loadChartBase64(context, task1Data.chartAssetPath)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                TopLevelDestinations.forEach { destination ->
                    val navSelected = currentRoute == destination.route ||
                        (currentRoute == IeltsRoutes.SPEAKING && destination.route == IeltsRoutes.LIBRARY)
                    NavigationBarItem(
                        selected = navSelected,
                        onClick = { currentRoute = destination.route },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        stateHolder.SaveableStateProvider(key = currentRoute) {
            when (currentRoute) {
                IeltsRoutes.LIBRARY -> {
                    LibraryScreen(
                        tests = practiceTests,
                        onOpenListening = { test ->
                            selectedTestId = test.id
                            selectedQuizSkill = QuizSkillMode.LISTENING
                            currentRoute = IeltsRoutes.QUIZ
                        },
                        onOpenReading = { test ->
                            selectedTestId = test.id
                            selectedQuizSkill = QuizSkillMode.READING
                            currentRoute = IeltsRoutes.QUIZ
                        },
                        onOpenWriting = { test ->
                            selectedTestId = test.id
                            currentRoute = IeltsRoutes.WRITING
                        },
                        onOpenSpeaking = { test ->
                            selectedTestId = test.id
                            currentRoute = IeltsRoutes.SPEAKING
                        },
                        modifier = contentModifier
                    )
                }

                IeltsRoutes.QUIZ -> {
                    QuizScreen(
                        testId = selectedTest.id,
                        book = selectedTest.book,
                        testNumber = selectedTest.testNumber,
                        testLabel = selectedTestLabel,
                        activeSkill = selectedQuizSkill,
                        onSelectSkill = { selectedQuizSkill = it },
                        listeningSections = selectedBundle?.listeningSections.orEmpty(),
                        readingSections = selectedBundle?.readingSections.orEmpty(),
                        onQuizSubmitted = { summary ->
                            quizAttempts = quizAttempts + summary
                            val recordLabel = "${summary.book.code} T${summary.testNumber} ${if (summary.skill == QuizSkillMode.LISTENING) "L" else "R"}"
                            recentRecords = (recentRecords + TestProgressRecord(recordLabel, summary.band, summary.book)).takeLast(12)
                            practiceTests = practiceTests.map { item ->
                                if (item.id == summary.testId) {
                                    if (summary.skill == QuizSkillMode.LISTENING) {
                                        item.copy(
                                            listeningStatus = SectionStatus.COMPLETED,
                                            lastListeningBand = summary.band
                                        )
                                    } else {
                                        item.copy(
                                            readingStatus = SectionStatus.COMPLETED,
                                            lastReadingBand = summary.band
                                        )
                                    }
                                } else {
                                    item
                                }
                            }
                        },
                        modifier = contentModifier
                    )
                }

                IeltsRoutes.WRITING -> {
                    WritingScreen(
                        testLabel = selectedTestLabel,
                        task1Data = task1Data,
                        task2Data = task2Data,
                        onOverallBandUpdated = { band ->
                            val recordLabel = "${selectedTest.book.code} T${selectedTest.testNumber} W"
                            recentRecords = (recentRecords + TestProgressRecord(recordLabel, band, selectedTest.book)).takeLast(12)
                            practiceTests = practiceTests.map { item ->
                                if (item.id == selectedTest.id) {
                                    item.copy(
                                        writingStatus = SectionStatus.COMPLETED,
                                        lastWritingBand = band
                                    )
                                } else {
                                    item
                                }
                            }
                        },
                        questionId = selectedQuestionId,
                        chartBase64 = task1ChartBase64,
                        onMarkTask = { taskNumber, essay, chartBase64 ->
                            val promptText = if (taskNumber == 1) task1Data.prompt else task2Data.prompt
                            val result = requestWritingMark(
                                context = context,
                                client = httpClient,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                taskNumber = taskNumber,
                                taskPrompt = promptText,
                                essay = essay,
                                chartBase64 = if (taskNumber == 1) chartBase64 else null
                            )
                            writingMarkHistory = writingMarkHistory + result
                            result
                        },
                        onSaveAttempt = { attempt ->
                            scope.launch(Dispatchers.IO) {
                                saveAttemptBestEffort(context, attempt)
                            }
                        },
                        modifier = contentModifier
                    )
                }

                IeltsRoutes.SPEAKING -> {
                    val speakingData = selectedBundle?.speaking
                    if (speakingData != null) {
                        SpeakingPracticeScreen(
                            testLabel = selectedTestLabel,
                            speakingData = speakingData,
                            initialBand = selectedTest.lastSpeakingBand,
                            onSaveBand = { band ->
                                val recordLabel = "${selectedTest.book.code} T${selectedTest.testNumber} S"
                                recentRecords = (recentRecords + TestProgressRecord(recordLabel, band, selectedTest.book)).takeLast(12)
                                practiceTests = practiceTests.map { item ->
                                    if (item.id == selectedTest.id) {
                                        item.copy(
                                            speakingStatus = SectionStatus.COMPLETED,
                                            lastSpeakingBand = band
                                        )
                                    } else {
                                        item
                                    }
                                }
                                currentRoute = IeltsRoutes.LIBRARY
                            },
                            onBack = { currentRoute = IeltsRoutes.LIBRARY },
                            modifier = contentModifier
                        )
                    } else {
                        currentRoute = IeltsRoutes.LIBRARY
                    }
                }

                IeltsRoutes.PROGRESS -> {
                    val bookSummaries = remember(practiceTests) {
                        IeltsScoring.aggregateBooks(practiceTests)
                    }
                    val skillProgress = remember(practiceTests) {
                        buildSkillProgress(practiceTests)
                    }
                    val typeStats = remember(quizAttempts) {
                        buildQuestionTypeStats(quizAttempts)
                    }
                    val writingCriteria = remember(writingMarkHistory) {
                        buildWritingCriteriaProgress(writingMarkHistory)
                    }
                    ProgressScreen(
                        skillProgress = skillProgress,
                        recentTests = recentRecords,
                        writingCriteria = writingCriteria,
                        bookSummaries = bookSummaries,
                        questionTypeStats = typeStats,
                        modifier = contentModifier
                    )
                }

                IeltsRoutes.SETTINGS -> {
                    SettingsScreen(
                        baseUrl = baseUrl,
                        onBaseUrlChange = { baseUrl = it },
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it },
                        model = model,
                        onModelChange = { model = it },
                        onTestConnection = {
                            if (settingsTestState !is SettingsTestState.Loading) {
                                settingsTestState = SettingsTestState.Loading
                                scope.launch {
                                    settingsTestState = testEndpointConnection(
                                        client = httpClient,
                                        baseUrl = baseUrl,
                                        apiKey = apiKey
                                    )
                                }
                            }
                        },
                        testState = settingsTestState,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}

private fun buildSkillProgress(tests: List<PracticeTest>): List<SkillProgress> {
    val listeningBands = tests.mapNotNull { it.lastListeningBand }
    val readingBands = tests.mapNotNull { it.lastReadingBand }
    val writingBands = tests.mapNotNull { it.lastWritingBand }
    val speakingBands = tests.mapNotNull { it.lastSpeakingBand }

    val result = mutableListOf<SkillProgress>()
    if (listeningBands.isNotEmpty()) {
        result.add(SkillProgress("Listening", IeltsScoring.roundToHalfBand(listeningBands.average())))
    }
    if (readingBands.isNotEmpty()) {
        result.add(SkillProgress("Reading", IeltsScoring.roundToHalfBand(readingBands.average())))
    }
    if (writingBands.isNotEmpty()) {
        result.add(SkillProgress("Writing", IeltsScoring.roundToHalfBand(writingBands.average())))
    }
    if (speakingBands.isNotEmpty()) {
        result.add(SkillProgress("Speaking", IeltsScoring.roundToHalfBand(speakingBands.average())))
    }
    return result
}

private fun buildQuestionTypeStats(attempts: List<QuizAttemptSummary>): List<QuestionTypeAccuracy> {
    val totals = linkedMapOf<QuestionTypeCategory, Pair<Int, Int>>()
    QuestionTypeCategory.entries.forEach { totals[it] = 0 to 0 }

    attempts.forEach { attempt ->
        attempt.typeBreakdown.forEach { (category, pair) ->
            val current = totals[category] ?: (0 to 0)
            totals[category] = (current.first + pair.first) to (current.second + pair.second)
        }
    }

    return QuestionTypeCategory.entries.map { category ->
        val pair = totals[category] ?: (0 to 0)
        QuestionTypeAccuracy(
            category = category,
            correct = pair.first,
            total = pair.second
        )
    }
}

private fun buildWritingCriteriaProgress(history: List<MarkResult>): List<SkillProgress> {
    if (history.isEmpty()) return emptyList()
    return listOf(
        SkillProgress("TA / TR", IeltsScoring.roundToHalfBand(history.map { it.ta_tr }.average())),
        SkillProgress("CC", IeltsScoring.roundToHalfBand(history.map { it.cc }.average())),
        SkillProgress("LR", IeltsScoring.roundToHalfBand(history.map { it.lr }.average())),
        SkillProgress("GRA", IeltsScoring.roundToHalfBand(history.map { it.gra }.average()))
    )
}

private suspend fun saveAttemptBestEffort(context: Context, attempt: Attempt) {
    runCatching {
        IeltsDb.get(context).dao().saveAttempt(attempt)
    }
}

private suspend fun testEndpointConnection(
    client: OkHttpClient,
    baseUrl: String,
    apiKey: String
): SettingsTestState = withContext(Dispatchers.IO) {
    val cleanBase = baseUrl.trim().trimEnd('/')
    if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
        return@withContext SettingsTestState.Error("Base URL must start with http:// or https://.")
    }

    val requestBuilder = Request.Builder()
        .url("$cleanBase/models")
        .get()
    if (apiKey.isNotBlank()) {
        requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
    }

    val startNanos = System.nanoTime()
    try {
        client.newCall(requestBuilder.build()).execute().use { response ->
            val latencyMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
            when {
                response.isSuccessful -> SettingsTestState.Success(
                    latencyMs = latencyMs,
                    detail = "Endpoint responded with HTTP ${response.code}."
                )
                response.code == 401 || response.code == 403 -> SettingsTestState.Error(
                    "Authentication failed (HTTP ${response.code}). Check your API key."
                )
                response.code == 404 -> SettingsTestState.Error(
                    "Endpoint not found (HTTP 404). Check your base URL."
                )
                else -> SettingsTestState.Error(
                    "Endpoint returned HTTP ${response.code}. Try again later."
                )
            }
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Throwable) {
        SettingsTestState.Error(toFriendlyNetworkError(e))
    }
}

private suspend fun requestWritingMark(
    context: Context,
    client: OkHttpClient,
    baseUrl: String,
    apiKey: String,
    model: String,
    taskNumber: Int,
    taskPrompt: String,
    essay: String,
    chartBase64: String?
): MarkResult = withContext(Dispatchers.IO) {
    val cleanBase = baseUrl.trim().trimEnd('/')
    if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
        throw IOException("Invalid base URL. Open Settings and enter a valid endpoint.")
    }
    if (model.isBlank()) {
        throw IOException("Model name is empty. Open Settings and enter a model name.")
    }

    val assetPath = if (taskNumber == 1) "ai/task1_system.txt" else "ai/task2_system.txt"
    val systemPrompt = runCatching {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }.getOrElse {
        "You are an IELTS Writing examiner. Return strict JSON matching the required schema."
    }

    val userText = buildString {
        append("Task ").append(taskNumber).append(" prompt:\n")
        append(taskPrompt.trim())
        append("\n\nStudent answer:\n")
        append(essay.trim())
    }

    val userMessage = JSONObject().put("role", "user")
    if (taskNumber == 1 && !chartBase64.isNullOrBlank()) {
        val imageUrlValue = if (chartBase64.startsWith("data:")) {
            chartBase64
        } else {
            "data:image/png;base64,$chartBase64"
        }
        val contentParts = JSONArray()
            .put(JSONObject().put("type", "text").put("text", userText))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", imageUrlValue))
            )
        userMessage.put("content", contentParts)
    } else {
        userMessage.put("content", userText)
    }

    val payload = JSONObject()
        .put("model", model.trim())
        .put("temperature", 0.2)
        .put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(userMessage)
        )

    val mediaType = "application/json; charset=utf-8".toMediaType()
    val requestBuilder = Request.Builder()
        .url("$cleanBase/chat/completions")
        .post(payload.toString().toRequestBody(mediaType))
    if (apiKey.isNotBlank()) {
        requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
    }

    val responseBodyText = client.newCall(requestBuilder.build()).execute().use { response ->
        val bodyStr = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            when (response.code) {
                401, 403 -> throw IOException("Authentication failed (HTTP ${response.code}). Check your API key in Settings.")
                404 -> throw IOException("Endpoint not found (HTTP 404). Check your base URL in Settings.")
                else -> throw IOException("Marking service returned HTTP ${response.code}.")
            }
        }
        bodyStr
    }

    val assistantContent = extractAssistantContent(responseBodyText)
    parseWritingMarkJson(stripCodeFence(assistantContent))
}

private fun extractAssistantContent(responseJson: String): String {
    val root = try {
        JSONObject(responseJson)
    } catch (e: JSONException) {
        throw ParseError("Marker returned a malformed API response.")
    }
    val choices = root.optJSONArray("choices")
        ?: throw ParseError("API response is missing choices.")
    if (choices.length() == 0) {
        throw ParseError("API response contained no choices.")
    }
    val firstChoice = choices.optJSONObject(0)
        ?: throw ParseError("Invalid choice item in API response.")
    val message = firstChoice.optJSONObject("message")
        ?: throw ParseError("API response is missing message.")
    val content = message.opt("content")
        ?: throw ParseError("API response is missing message content.")

    return when (content) {
        is String -> content
        is JSONArray -> buildString {
            for (index in 0 until content.length()) {
                val part = content.optJSONObject(index)
                val text = part?.optString("text").orEmpty()
                if (text.isNotEmpty()) append(text)
            }
        }
        else -> throw ParseError("Unsupported message content format from API.")
    }
}

private fun stripCodeFence(text: String): String {
    val trimmed = text.trim()
    if (!trimmed.startsWith("```")) return trimmed
    val firstNewline = trimmed.indexOf('\n')
    if (firstNewline < 0) return trimmed
    val withoutStart = trimmed.substring(firstNewline + 1)
    return if (withoutStart.trimEnd().endsWith("```")) {
        withoutStart.trimEnd().removeSuffix("```").trim()
    } else {
        withoutStart.trim()
    }
}

private fun buildFallbackTask1Data(test: PracticeTest): WritingTaskData = WritingTaskData(
    taskType = WritingTaskType.TASK_1,
    prompt = "Summarise the main trends and key comparisons for ${test.book.code} Test ${test.testNumber} Writing Task 1 in at least 150 words.",
    initialDraft = "",
    scores = listOf(
        CriterionScore(WritingCriterion.TA, 0.0, "Tap Review Draft to get a Task Achievement score."),
        CriterionScore(WritingCriterion.CC, 0.0, "Tap Review Draft to get a Coherence and Cohesion score."),
        CriterionScore(WritingCriterion.LR, 0.0, "Tap Review Draft to get a Lexical Resource score."),
        CriterionScore(WritingCriterion.GRA, 0.0, "Tap Review Draft to get a Grammatical Range and Accuracy score.")
    ),
    inlineComments = emptyList(),
    band75Rewrite = "Submit your draft with Review Draft to generate a Band 7.5 rewrite.",
    band8Model = "Submit your draft with Review Draft to generate a Band 8.0 model answer."
)

private fun buildFallbackTask2Data(test: PracticeTest): WritingTaskData = WritingTaskData(
    taskType = WritingTaskType.TASK_2,
    prompt = "Topic: ${test.writingTopic}. Discuss both views and give your own opinion in at least 250 words.",
    initialDraft = "",
    scores = listOf(
        CriterionScore(WritingCriterion.TR, 0.0, "Tap Review Draft to get a Task Response score."),
        CriterionScore(WritingCriterion.CC, 0.0, "Tap Review Draft to get a Coherence and Cohesion score."),
        CriterionScore(WritingCriterion.LR, 0.0, "Tap Review Draft to get a Lexical Resource score."),
        CriterionScore(WritingCriterion.GRA, 0.0, "Tap Review Draft to get a Grammatical Range and Accuracy score.")
    ),
    inlineComments = emptyList(),
    band75Rewrite = "Submit your draft with Review Draft to generate a Band 7.5 rewrite.",
    band8Model = "Submit your draft with Review Draft to generate a Band 8.0 model answer."
)
