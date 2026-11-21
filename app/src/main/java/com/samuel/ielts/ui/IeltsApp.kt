package com.samuel.ielts.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuel.ielts.data.Attempt
import com.samuel.ielts.data.IeltsDb
import com.samuel.ielts.model.CriterionScore
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.IeltsSkill
import com.samuel.ielts.model.NextStepCardData
import com.samuel.ielts.model.NextStepDestination
import com.samuel.ielts.model.NextStepRecommender
import com.samuel.ielts.model.PracticeTest
import com.samuel.ielts.model.QuestionTypeAccuracy
import com.samuel.ielts.model.QuestionTypeCategory
import com.samuel.ielts.model.QuizAttemptSummary
import com.samuel.ielts.model.QuizSkillMode
import com.samuel.ielts.model.SectionStatus
import com.samuel.ielts.model.SkillProgress
import com.samuel.ielts.model.SpeakingPromptData
import com.samuel.ielts.model.StudyProgressSnapshot
import com.samuel.ielts.model.StudyStreakStore
import com.samuel.ielts.model.TestProgressRecord
import com.samuel.ielts.model.WritingCriterion
import com.samuel.ielts.model.WritingTaskData
import com.samuel.ielts.model.WritingTaskType
import com.samuel.ielts.ui.components.ConfettiBurst
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.components.NewBestBandBanner
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
import com.samuel.ielts.ui.screens.TestDetailScreen
import com.samuel.ielts.ui.screens.WritingScreen
import com.samuel.ielts.ui.screens.parseWritingMarkJson
import com.samuel.ielts.ui.screens.toFriendlyNetworkError
import com.samuel.ielts.ui.theme.IeltsSpacing
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
    const val TEST_DETAIL = "test_detail"
    const val QUIZ = "quiz"
    const val WRITING = "writing"
    const val SPEAKING = "speaking"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TopLevelDestinations = listOf(
    NavDestination(IeltsRoutes.LIBRARY, "Library", IeltsIcons.Library),
    NavDestination(IeltsRoutes.QUIZ, "Quiz", IeltsIcons.Quiz),
    NavDestination(IeltsRoutes.WRITING, "Writing", IeltsIcons.Writing),
    NavDestination(IeltsRoutes.SPEAKING, "Speaking", IeltsIcons.Speaking),
    NavDestination(IeltsRoutes.PROGRESS, "Progress", IeltsIcons.Progress),
    NavDestination(IeltsRoutes.SETTINGS, "Settings", IeltsIcons.Settings)
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
    var navBackStack by rememberSaveable { mutableStateOf(listOf(IeltsRoutes.LIBRARY)) }
    val currentRoute = navBackStack.lastOrNull() ?: IeltsRoutes.LIBRARY

    var selectedTestId by rememberSaveable { mutableStateOf(practiceTests.first().id) }
    var selectedQuizSkill by rememberSaveable { mutableStateOf(QuizSkillMode.READING) }

    var quizAttempts by remember { mutableStateOf<List<QuizAttemptSummary>>(emptyList()) }
    var recentRecords by remember { mutableStateOf<List<TestProgressRecord>>(emptyList()) }
    var writingMarkHistory by remember { mutableStateOf<List<MarkResult>>(emptyList()) }

    val streakSnapshot by remember(context) {
        StudyStreakStore.observeSnapshot(context)
    }.collectAsState(initial = StudyProgressSnapshot())

    var showConfetti by remember { mutableStateOf(false) }
    var celebratedBestBand by remember { mutableStateOf<Double?>(null) }

    var baseUrl by rememberSaveable { mutableStateOf(DEFAULT_API_BASE_URL) }
    // Pre-filled from gitignored meta.env at build time; user can still
    // replace or clear it in Settings. Never logged.
    var apiKey by rememberSaveable { mutableStateOf(com.samuel.ielts.BuildConfig.META_API_KEY_DEFAULT) }
    var model by rememberSaveable { mutableStateOf(DEFAULT_API_MODEL) }
    var settingsTestState by remember { mutableStateOf<SettingsTestState>(SettingsTestState.Idle) }

    fun navigateTo(route: String) {
        if (route == currentRoute) return
        navBackStack = if (route == IeltsRoutes.LIBRARY) {
            listOf(IeltsRoutes.LIBRARY)
        } else {
            val withoutTarget = navBackStack.filterNot { it == route }
            val withRoot = if (withoutTarget.firstOrNull() == IeltsRoutes.LIBRARY) {
                withoutTarget
            } else {
                listOf(IeltsRoutes.LIBRARY) + withoutTarget
            }
            withRoot + route
        }
    }

    fun navigateBack() {
        navBackStack = if (navBackStack.size > 1) {
            navBackStack.dropLast(1)
        } else {
            listOf(IeltsRoutes.LIBRARY)
        }
    }

    BackHandler(enabled = currentRoute != IeltsRoutes.LIBRARY) {
        navigateBack()
    }

    fun recordStudyAndCheckBest(updatedTests: List<PracticeTest>) {
        val overallBand = computeOverallBand(updatedTests)
        scope.launch {
            val outcome = StudyStreakStore.recordStudyActivity(
                context = context,
                candidateOverallBand = overallBand
            )
            if (outcome.isNewBestBand) {
                celebratedBestBand = outcome.snapshot.bestOverallBand
                showConfetti = true
            }
        }
    }

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

    val activeWritingTest = remember(selectedTest, practiceTests, catalog) {
        if (catalog.bundlesById[selectedTest.id]?.writingTask1 != null) {
            selectedTest
        } else {
            practiceTests.firstOrNull { catalog.bundlesById[it.id]?.writingTask1 != null } ?: selectedTest
        }
    }
    val activeWritingBundle = catalog.bundlesById[activeWritingTest.id]
    val activeWritingTestLabel = "${activeWritingTest.book.code} Test ${activeWritingTest.testNumber}"
    val activeWritingQuestionId = remember(activeWritingTest.id, practiceTests.size) {
        (practiceTests.indexOfFirst { it.id == activeWritingTest.id }.coerceAtLeast(0) + 1).toLong()
    }

    val task1Data = remember(activeWritingTest.id, activeWritingBundle) {
        activeWritingBundle?.writingTask1 ?: buildFallbackTask1Data(activeWritingTest)
    }
    val task2Data = remember(activeWritingTest.id, activeWritingBundle) {
        activeWritingBundle?.writingTask2 ?: buildFallbackTask2Data(activeWritingTest)
    }

    var task1ChartBase64 by remember(task1Data.chartAssetPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(task1Data.chartAssetPath) {
        task1ChartBase64 = withContext(Dispatchers.IO) {
            SeedCatalogLoader.loadChartBase64(context, task1Data.chartAssetPath)
        }
    }

    val availableSpeakingTests = remember(practiceTests, catalog) {
        practiceTests.filter { catalog.bundlesById[it.id]?.speaking != null }
    }
    val activeSpeakingTest = remember(selectedTest, availableSpeakingTests, catalog) {
        if (catalog.bundlesById[selectedTest.id]?.speaking != null) {
            selectedTest
        } else {
            availableSpeakingTests.firstOrNull() ?: selectedTest
        }
    }
    val activeSpeakingData = catalog.bundlesById[activeSpeakingTest.id]?.speaking
        ?: buildFallbackSpeakingData(activeSpeakingTest)
    val activeSpeakingTestLabel = "${activeSpeakingTest.book.code} Test ${activeSpeakingTest.testNumber}"

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
    val nextSteps = remember(practiceTests, quizAttempts, typeStats, writingCriteria, recentRecords) {
        NextStepRecommender.buildNextSteps(
            tests = practiceTests,
            quizAttempts = quizAttempts,
            questionTypeStats = typeStats,
            writingCriteria = writingCriteria,
            recentRecords = recentRecords
        )
    }

    fun handleNextStep(step: NextStepCardData) {
        step.targetTestId?.let { targetId ->
            if (practiceTests.any { it.id == targetId }) {
                selectedTestId = targetId
            }
        }
        when (step.destination) {
            NextStepDestination.LIBRARY -> navigateTo(IeltsRoutes.LIBRARY)
            NextStepDestination.QUIZ_LISTENING -> {
                selectedQuizSkill = QuizSkillMode.LISTENING
                navigateTo(IeltsRoutes.QUIZ)
            }
            NextStepDestination.QUIZ_READING -> {
                selectedQuizSkill = QuizSkillMode.READING
                navigateTo(IeltsRoutes.QUIZ)
            }
            NextStepDestination.WRITING -> navigateTo(IeltsRoutes.WRITING)
            NextStepDestination.SPEAKING -> navigateTo(IeltsRoutes.SPEAKING)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                IeltsBottomNavigationBar(
                    destinations = TopLevelDestinations,
                    currentRoute = currentRoute,
                    onSelectRoute = { route -> navigateTo(route) }
                )
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
                            onOpenTest = { test ->
                                selectedTestId = test.id
                                navigateTo(IeltsRoutes.TEST_DETAIL)
                            },
                            streakSnapshot = streakSnapshot,
                            topNextStep = nextSteps.firstOrNull(),
                            onSelectNextStep = ::handleNextStep,
                            modifier = contentModifier
                        )
                    }

                    IeltsRoutes.TEST_DETAIL -> {
                        TestDetailScreen(
                            test = selectedTest,
                            onBack = ::navigateBack,
                            onOpenSkill = { skill ->
                                when (skill) {
                                    IeltsSkill.LISTENING -> {
                                        selectedQuizSkill = QuizSkillMode.LISTENING
                                        navigateTo(IeltsRoutes.QUIZ)
                                    }
                                    IeltsSkill.READING -> {
                                        selectedQuizSkill = QuizSkillMode.READING
                                        navigateTo(IeltsRoutes.QUIZ)
                                    }
                                    IeltsSkill.WRITING -> navigateTo(IeltsRoutes.WRITING)
                                    IeltsSkill.SPEAKING -> navigateTo(IeltsRoutes.SPEAKING)
                                }
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
                                val updatedTests = practiceTests.map { item ->
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
                                practiceTests = updatedTests
                                recordStudyAndCheckBest(updatedTests)
                            },
                            onOpenLibrary = { navigateTo(IeltsRoutes.LIBRARY) },
                            modifier = contentModifier
                        )
                    }

                    IeltsRoutes.WRITING -> {
                        WritingScreen(
                            testLabel = activeWritingTestLabel,
                            task1Data = task1Data,
                            task2Data = task2Data,
                            onOverallBandUpdated = { band ->
                                val recordLabel = "${activeWritingTest.book.code} T${activeWritingTest.testNumber} W"
                                recentRecords = (recentRecords + TestProgressRecord(recordLabel, band, activeWritingTest.book)).takeLast(12)
                                val updatedTests = practiceTests.map { item ->
                                    if (item.id == activeWritingTest.id) {
                                        item.copy(
                                            writingStatus = SectionStatus.COMPLETED,
                                            lastWritingBand = band
                                        )
                                    } else {
                                        item
                                    }
                                }
                                practiceTests = updatedTests
                                recordStudyAndCheckBest(updatedTests)
                            },
                            questionId = activeWritingQuestionId,
                            chartBase64 = task1ChartBase64,
                            chartCaptionLines = activeWritingBundle?.writingTask1ChartCaption.orEmpty(),
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
                        SpeakingPracticeScreen(
                            testLabel = activeSpeakingTestLabel,
                            speakingData = activeSpeakingData,
                            initialBand = activeSpeakingTest.lastSpeakingBand,
                            onSaveBand = { band ->
                                val recordLabel = "${activeSpeakingTest.book.code} T${activeSpeakingTest.testNumber} S"
                                recentRecords = (recentRecords + TestProgressRecord(recordLabel, band, activeSpeakingTest.book)).takeLast(12)
                                val updatedTests = practiceTests.map { item ->
                                    if (item.id == activeSpeakingTest.id) {
                                        item.copy(
                                            speakingStatus = SectionStatus.COMPLETED,
                                            lastSpeakingBand = band
                                        )
                                    } else {
                                        item
                                    }
                                }
                                practiceTests = updatedTests
                                recordStudyAndCheckBest(updatedTests)
                            },
                            onBack = { navigateTo(IeltsRoutes.LIBRARY) },
                            availableTests = availableSpeakingTests.map { test ->
                                test.id to "${test.book.code} T${test.testNumber}"
                            },
                            selectedTestId = activeSpeakingTest.id,
                            onSelectTest = { id -> selectedTestId = id },
                            modifier = contentModifier
                        )
                    }

                    IeltsRoutes.PROGRESS -> {
                        ProgressScreen(
                            skillProgress = skillProgress,
                            recentTests = recentRecords,
                            writingCriteria = writingCriteria,
                            bookSummaries = bookSummaries,
                            questionTypeStats = typeStats,
                            streakSnapshot = streakSnapshot,
                            nextSteps = nextSteps,
                            onSelectNextStep = ::handleNextStep,
                            onStartPractice = {
                                val top = nextSteps.firstOrNull()
                                if (top != null) {
                                    handleNextStep(top)
                                } else {
                                    navigateTo(IeltsRoutes.LIBRARY)
                                }
                            },
                            onOpenQuiz = { navigateTo(IeltsRoutes.QUIZ) },
                            onOpenWriting = { navigateTo(IeltsRoutes.WRITING) },
                            onOpenLibrary = { navigateTo(IeltsRoutes.LIBRARY) },
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

        val bannerBand = celebratedBestBand
        if (bannerBand != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.Md)
            ) {
                NewBestBandBanner(
                    bestBand = bannerBand,
                    onDismiss = { celebratedBestBand = null }
                )
            }
        }

        ConfettiBurst(
            show = showConfetti,
            onFinished = { showConfetti = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun IeltsBottomNavigationBar(
    destinations: List<NavDestination>,
    currentRoute: String,
    onSelectRoute: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.Xs, vertical = IeltsSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                val isSelected = currentRoute == destination.route ||
                    (currentRoute == IeltsRoutes.TEST_DETAIL && destination.route == IeltsRoutes.LIBRARY)
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val indicatorColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
                val interactionSource = remember(destination.route) { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(
                            minWidth = IeltsSpacing.TouchTarget,
                            minHeight = 64.dp
                        )
                        .clip(MaterialTheme.shapes.medium)
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelectRoute(destination.route) }
                        )
                        .padding(vertical = IeltsSpacing.Xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(indicatorColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = contentColor,
                            modifier = Modifier.size(IeltsSpacing.NavIcon)
                        )
                    }
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            letterSpacing = (-0.1).sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun computeOverallBand(tests: List<PracticeTest>): Double {
    val activeSkills = buildSkillProgress(tests).filter { it.band > 0.0 }
    if (activeSkills.isEmpty()) return 0.0
    return IeltsScoring.roundToHalfBand(activeSkills.map { it.band }.average())
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
        SkillProgress("Task Achievement", IeltsScoring.roundToHalfBand(history.map { it.ta_tr }.average())),
        SkillProgress("Coherence and Cohesion", IeltsScoring.roundToHalfBand(history.map { it.cc }.average())),
        SkillProgress("Lexical Resource", IeltsScoring.roundToHalfBand(history.map { it.lr }.average())),
        SkillProgress("Grammatical Range and Accuracy", IeltsScoring.roundToHalfBand(history.map { it.gra }.average()))
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
    scores = emptyList(),
    inlineComments = emptyList(),
    band75Rewrite = "",
    band8Model = ""
)

private fun buildFallbackTask2Data(test: PracticeTest): WritingTaskData = WritingTaskData(
    taskType = WritingTaskType.TASK_2,
    prompt = "Topic: ${test.writingTopic}. Discuss both views and give your own opinion in at least 250 words.",
    initialDraft = "",
    scores = emptyList(),
    inlineComments = emptyList(),
    band75Rewrite = "",
    band8Model = ""
)

private fun buildFallbackSpeakingData(test: PracticeTest): SpeakingPromptData = SpeakingPromptData(
    testId = test.id,
    topicSummary = "Daily life and study goals",
    part1 = "Tell me about your hometown and what you enjoy studying or working on right now.",
    part2 = "Describe a memorable learning experience you had recently. You should say what you learned, how you practiced, and why it was useful.",
    part3 = "How has technology changed the way people prepare for exams? What skills matter most for lifelong learning?"
)
