package com.samuel.ielts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.samuel.ielts.data.Attempt
import com.samuel.ielts.model.CriterionScore
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.InlineComment
import com.samuel.ielts.model.WritingCriterion
import com.samuel.ielts.model.WritingTaskData
import com.samuel.ielts.model.WritingTaskType
import com.samuel.ielts.ui.components.AssetImageState
import com.samuel.ielts.ui.components.AssetImageThumbnailCard
import com.samuel.ielts.ui.components.AssetImageZoomDialog
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.components.MissingAssetImagePlaceholder
import com.samuel.ielts.ui.components.rememberAssetImageState
import com.samuel.ielts.ui.theme.IeltsSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

data class MarkResult(
    val ta_tr: Double,
    val cc: Double,
    val lr: Double,
    val gra: Double,
    val overall: Double,
    val comments: List<InlineComment>,
    val rewrite75: String,
    val model8: String,
    val rawJson: String,
    val feedback: List<String> = emptyList(),
    val wordCount: Int = 0,
    val underLength: Boolean = false
)

class ParseError(message: String) : Exception(message)

fun parseWritingMarkJson(rawJson: String): MarkResult {
    val trimmed = rawJson.trim()
    if (trimmed.isEmpty()) {
        throw ParseError("Marker returned an empty response.")
    }

    val root = try {
        JSONObject(trimmed)
    } catch (e: JSONException) {
        throw ParseError("Marker response is not valid JSON.")
    }

    val task = requireIntKey(root, "task")
    if (task != 1 && task != 2) {
        throw ParseError("Invalid value for key task: expected 1 or 2.")
    }

    val firstKey = if (task == 1) "TA" else "TR"
    val taTr = requireDoubleKey(root, firstKey)
    val cc = requireDoubleKey(root, "CC")
    val lr = requireDoubleKey(root, "LR")
    val gra = requireDoubleKey(root, "GRA")
    val overall = requireDoubleKey(root, "overall")
    val wordCount = requireIntKey(root, "wordCount")
    val underLength = requireBooleanKey(root, "underLength")

    val commentsArray = requireArrayKey(root, "comments")
    val comments = mutableListOf<InlineComment>()
    for (index in 0 until commentsArray.length()) {
        val item = try {
            commentsArray.getJSONObject(index)
        } catch (e: JSONException) {
            throw ParseError("Invalid item in comments at index $index.")
        }
        val contextLabel = "comments[$index]"
        val quote = requireStringKey(item, "quote", contextLabel)
        val criterionRaw = requireStringKey(item, "criterion", contextLabel)
        val issue = requireStringKey(item, "issue", contextLabel)
        val rewrite = requireStringKey(item, "rewrite", contextLabel)

        val criterion = when (criterionRaw.trim().uppercase(Locale.US)) {
            "TA" -> WritingCriterion.TA
            "TR" -> WritingCriterion.TR
            "CC" -> WritingCriterion.CC
            "LR" -> WritingCriterion.LR
            "GRA" -> WritingCriterion.GRA
            else -> throw ParseError("Invalid criterion in $contextLabel: $criterionRaw")
        }

        comments.add(
            InlineComment(
                id = index + 1,
                excerpt = quote,
                criterion = criterion,
                note = issue,
                suggestion = rewrite
            )
        )
    }

    val feedbackArray = requireArrayKey(root, "feedback")
    val feedback = mutableListOf<String>()
    for (index in 0 until feedbackArray.length()) {
        val entry = feedbackArray.opt(index)
        if (entry !is String) {
            throw ParseError("Invalid item in feedback at index $index.")
        }
        feedback.add(entry)
    }

    val band75Rewrite = requireStringKey(root, "band75Rewrite")
    val band8Model = requireStringKey(root, "band8Model")

    return MarkResult(
        ta_tr = IeltsScoring.roundToHalfBand(taTr),
        cc = IeltsScoring.roundToHalfBand(cc),
        lr = IeltsScoring.roundToHalfBand(lr),
        gra = IeltsScoring.roundToHalfBand(gra),
        overall = IeltsScoring.roundToHalfBand(overall),
        comments = comments,
        rewrite75 = band75Rewrite,
        model8 = band8Model,
        rawJson = trimmed,
        feedback = feedback,
        wordCount = wordCount,
        underLength = underLength
    )
}

private fun requireDoubleKey(obj: JSONObject, key: String, parent: String? = null): Double {
    if (!obj.has(key) || obj.isNull(key)) {
        throw ParseError(missingKeyMessage(key, parent))
    }
    val value = obj.get(key)
    if (value !is Number) {
        throw ParseError(invalidTypeMessage(key, "number", parent))
    }
    return value.toDouble()
}

private fun requireIntKey(obj: JSONObject, key: String, parent: String? = null): Int {
    if (!obj.has(key) || obj.isNull(key)) {
        throw ParseError(missingKeyMessage(key, parent))
    }
    val value = obj.get(key)
    if (value !is Number) {
        throw ParseError(invalidTypeMessage(key, "integer", parent))
    }
    return value.toInt()
}

private fun requireBooleanKey(obj: JSONObject, key: String, parent: String? = null): Boolean {
    if (!obj.has(key) || obj.isNull(key)) {
        throw ParseError(missingKeyMessage(key, parent))
    }
    val value = obj.get(key)
    if (value !is Boolean) {
        throw ParseError(invalidTypeMessage(key, "boolean", parent))
    }
    return value
}

private fun requireStringKey(obj: JSONObject, key: String, parent: String? = null): String {
    if (!obj.has(key) || obj.isNull(key)) {
        throw ParseError(missingKeyMessage(key, parent))
    }
    val value = obj.get(key)
    if (value !is String) {
        throw ParseError(invalidTypeMessage(key, "string", parent))
    }
    return value
}

private fun requireArrayKey(obj: JSONObject, key: String, parent: String? = null): JSONArray {
    if (!obj.has(key) || obj.isNull(key)) {
        throw ParseError(missingKeyMessage(key, parent))
    }
    val value = obj.get(key)
    if (value !is JSONArray) {
        throw ParseError(invalidTypeMessage(key, "array", parent))
    }
    return value
}

private fun missingKeyMessage(key: String, parent: String?): String {
    return if (parent == null) {
        "Missing required key: $key"
    } else {
        "Missing required key in $parent: $key"
    }
}

private fun invalidTypeMessage(key: String, expectedType: String, parent: String?): String {
    return if (parent == null) {
        "Invalid type for key $key: expected $expectedType."
    } else {
        "Invalid type for key $key in $parent: expected $expectedType."
    }
}

private fun MarkResult.toCriterionScores(taskType: WritingTaskType): List<CriterionScore> {
    val firstCriterion = if (taskType == WritingTaskType.TASK_1) {
        WritingCriterion.TA
    } else {
        WritingCriterion.TR
    }
    val summaries = feedback
    return listOf(
        CriterionScore(
            criterion = firstCriterion,
            band = ta_tr,
            summary = summaries.getOrElse(0) { "${firstCriterion.fullName} band estimate." }
        ),
        CriterionScore(
            criterion = WritingCriterion.CC,
            band = cc,
            summary = summaries.getOrElse(1) { "${WritingCriterion.CC.fullName} band estimate." }
        ),
        CriterionScore(
            criterion = WritingCriterion.LR,
            band = lr,
            summary = summaries.getOrElse(2) { "${WritingCriterion.LR.fullName} band estimate." }
        ),
        CriterionScore(
            criterion = WritingCriterion.GRA,
            band = gra,
            summary = summaries.getOrElse(3) { "${WritingCriterion.GRA.fullName} band estimate." }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingScreen(
    testLabel: String,
    task1Data: WritingTaskData,
    task2Data: WritingTaskData,
    onOverallBandUpdated: (Double) -> Unit,
    questionId: Long = 0L,
    chartBase64: String? = null,
    chartCaptionLines: List<String> = emptyList(),
    onMarkTask: suspend (taskNumber: Int, essay: String, chartBase64: String?) -> MarkResult = { _, _, _ ->
        throw IllegalStateException("AI marker is not configured.")
    },
    onSaveAttempt: (Attempt) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    val task1ChartPath = task1Data.chartAssetPath?.trim()?.takeIf { it.isNotEmpty() }
    val task1ChartState = rememberAssetImageState(task1ChartPath)
    val task1ChartCaption = remember(chartCaptionLines, task1Data.prompt) {
        extractTask1ChartCaption(chartCaptionLines, task1Data.prompt)
    }
    var showChartZoomDialog by rememberSaveable(task1ChartPath) { mutableStateOf(false) }

    var activeTask by rememberSaveable { mutableStateOf(WritingTaskType.TASK_1) }
    var task1Draft by rememberSaveable(task1Data.prompt) { mutableStateOf(task1Data.initialDraft) }
    var task2Draft by rememberSaveable(task2Data.prompt) { mutableStateOf(task2Data.initialDraft) }
    var promptExpanded by rememberSaveable(activeTask.name) { mutableStateOf(false) }
    var showScoresSheet by rememberSaveable { mutableStateOf(false) }
    var showScoreDetails by rememberSaveable(activeTask.name) { mutableStateOf(false) }
    var showInlineComments by rememberSaveable(activeTask.name) { mutableStateOf(false) }
    var showBand75Rewrite by rememberSaveable(activeTask.name) { mutableStateOf(false) }
    var showBand8Model by rememberSaveable(activeTask.name) { mutableStateOf(false) }

    var task1MarkResult by remember(task1Data.prompt) { mutableStateOf<MarkResult?>(null) }
    var task2MarkResult by remember(task2Data.prompt) { mutableStateOf<MarkResult?>(null) }
    var isMarking by remember { mutableStateOf(false) }
    var markError by remember { mutableStateOf<String?>(null) }

    val currentTaskData = if (activeTask == WritingTaskType.TASK_1) task1Data else task2Data
    val currentDraft = if (activeTask == WritingTaskType.TASK_1) task1Draft else task2Draft
    val currentMarkResult = if (activeTask == WritingTaskType.TASK_1) task1MarkResult else task2MarkResult

    val wordCount = IeltsScoring.countWords(currentDraft)
    val minWords = activeTask.minWords
    val underWordWarning = wordCount < minWords

    val task1Scores = task1MarkResult?.toCriterionScores(WritingTaskType.TASK_1)
        ?: task1Data.scores.filter { it.band > 0.0 }
    val task2Scores = task2MarkResult?.toCriterionScores(WritingTaskType.TASK_2)
        ?: task2Data.scores.filter { it.band > 0.0 }

    val hasTask1Mark = task1Scores.isNotEmpty()
    val hasTask2Mark = task2Scores.isNotEmpty()
    val task1Band: Double? = if (hasTask1Mark) IeltsScoring.calculateTaskBand(task1Scores) else null
    val task2Band: Double? = if (hasTask2Mark) IeltsScoring.calculateTaskBand(task2Scores) else null
    val overallWritingBand: Double? = if (task1Band != null && task2Band != null) {
        IeltsScoring.calculateWritingOverall(task1Band, task2Band)
    } else {
        null
    }

    val displayScores = if (activeTask == WritingTaskType.TASK_1) task1Scores else task2Scores
    val hasCurrentMark = displayScores.isNotEmpty()
    val displayComments = currentMarkResult?.comments ?: currentTaskData.inlineComments
    val displayRewrite75 = currentMarkResult?.rewrite75 ?: currentTaskData.band75Rewrite
    val displayModel8 = currentMarkResult?.model8 ?: currentTaskData.band8Model

    fun triggerMark() {
        if (isMarking) return
        val taskToMark = activeTask
        val taskNumber = if (taskToMark == WritingTaskType.TASK_1) 1 else 2
        val essayToMark = if (taskToMark == WritingTaskType.TASK_1) task1Draft else task2Draft
        // chartBase64 is used ONLY for Task 1 image_url payload.
        val imagePayload = if (taskToMark == WritingTaskType.TASK_1) chartBase64 else null

        isMarking = true
        markError = null
        scope.launch {
            try {
                val result = onMarkTask(taskNumber, essayToMark, imagePayload)
                if (taskToMark == WritingTaskType.TASK_1) {
                    task1MarkResult = result
                } else {
                    task2MarkResult = result
                }

                val nextTask1Scores = if (taskToMark == WritingTaskType.TASK_1) {
                    result.toCriterionScores(WritingTaskType.TASK_1)
                } else {
                    task1Scores
                }
                val nextTask2Scores = if (taskToMark == WritingTaskType.TASK_2) {
                    result.toCriterionScores(WritingTaskType.TASK_2)
                } else {
                    task2Scores
                }
                val nextBand = if (nextTask1Scores.isNotEmpty() && nextTask2Scores.isNotEmpty()) {
                    IeltsScoring.calculateWritingOverall(
                        task1Band = IeltsScoring.calculateTaskBand(nextTask1Scores),
                        task2Band = IeltsScoring.calculateTaskBand(nextTask2Scores)
                    )
                } else {
                    IeltsScoring.calculateTaskBand(result.toCriterionScores(taskToMark))
                }
                onOverallBandUpdated(nextBand)
                onSaveAttempt(
                    Attempt(
                        questionId = questionId,
                        userAnswer = essayToMark,
                        correct = result.overall >= 6.0,
                        aiScoresJson = result.rawJson,
                        createdAt = System.currentTimeMillis()
                    )
                )
                showScoresSheet = true
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: ParseError) {
                markError = e.message ?: "Could not parse marker response."
            } catch (e: Throwable) {
                markError = toFriendlyNetworkError(e)
            } finally {
                isMarking = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeTask.ordinal,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WritingTaskType.entries.forEach { taskType ->
                val isSelected = activeTask == taskType
                Tab(
                    selected = isSelected,
                    onClick = {
                        activeTask = taskType
                        markError = null
                    },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = taskType.shortLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                            if (isSelected) {
                                Text(
                                    text = "${taskType.minWords}+ words",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            PriorWritingScoresCompactCard(
                testLabel = testLabel,
                task1Band = task1Band,
                task2Band = task2Band,
                overallBand = overallWritingBand,
                onClick = { showScoresSheet = true }
            )

            PromptDisclosureCard(
                prompt = currentTaskData.prompt,
                expanded = promptExpanded,
                onToggle = { promptExpanded = !promptExpanded }
            )

            if (activeTask == WritingTaskType.TASK_1) {
                when (val chartState = task1ChartState) {
                    is AssetImageState.Ready -> {
                        val description = if (task1ChartCaption != null) {
                            "Task 1 chart: $task1ChartCaption. Tap to zoom."
                        } else {
                            "Task 1 chart for $testLabel. Tap to zoom."
                        }
                        AssetImageThumbnailCard(
                            imageBitmap = chartState.imageBitmap,
                            contentDescription = description,
                            caption = task1ChartCaption,
                            onClick = { showChartZoomDialog = true }
                        )
                    }
                    AssetImageState.Missing -> {
                        MissingAssetImagePlaceholder(message = "No chart for this task")
                    }
                    AssetImageState.Loading -> {
                        // Asset decode is in progress; avoid flashing the missing placeholder.
                    }
                }
            }

            OutlinedTextField(
                value = currentDraft,
                onValueChange = { updated ->
                    if (activeTask == WritingTaskType.TASK_1) {
                        task1Draft = updated
                    } else {
                        task2Draft = updated
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("${currentTaskData.taskType.shortLabel} draft") }
            )

            if (underWordWarning) {
                WordCountWarningStrip(
                    wordCount = wordCount,
                    minWords = minWords,
                    firstCriterionName = activeTask.firstCriterionName
                )
            }

            val currentError = markError
            if (currentError != null) {
                WritingMarkErrorCard(
                    message = currentError,
                    onRetry = { triggerMark() },
                    retryEnabled = !isMarking
                )
            }
        }

        HorizontalDivider(color = NeutralBorder)

        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(IeltsSpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$wordCount/$minWords",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (underWordWarning) WarningAmber else SuccessGreen
                )

                Spacer(modifier = Modifier.width(IeltsSpacing.Md))

                // Word-count warnings are advisory only and never disable submission.
                Button(
                    onClick = { triggerMark() },
                    enabled = !isMarking,
                    modifier = Modifier.height(IeltsSpacing.TouchTarget)
                ) {
                    if (isMarking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(IeltsSpacing.Sm))
                        Text("Marking...")
                    } else {
                        Text("Review")
                    }
                }
            }
        }
    }

    val readyChart = task1ChartState as? AssetImageState.Ready
    if (showChartZoomDialog && activeTask == WritingTaskType.TASK_1 && readyChart != null) {
        val dialogDescription = if (task1ChartCaption != null) {
            "Enlarged Task 1 chart: $task1ChartCaption"
        } else {
            "Enlarged Task 1 chart for $testLabel"
        }
        AssetImageZoomDialog(
            imageBitmap = readyChart.imageBitmap,
            title = task1ChartCaption ?: "$testLabel Task 1 Chart",
            imageContentDescription = dialogDescription,
            closeContentDescription = "Close chart preview",
            onDismiss = { showChartZoomDialog = false }
        )
    }

    if (showScoresSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showScoresSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IeltsSpacing.Md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$testLabel Writing Scores",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showScoresSheet = false },
                        modifier = Modifier.size(IeltsSpacing.TouchTarget)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.Close,
                            contentDescription = "Close scores",
                            modifier = Modifier.size(IeltsSpacing.ActionIcon)
                        )
                    }
                }

                HorizontalDivider(color = NeutralBorder)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(IeltsSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
                ) {
                    item {
                        OverallWritingBandCard(
                            task1Band = task1Band,
                            task2Band = task2Band,
                            overallBand = overallWritingBand,
                            isMarking = isMarking,
                            onMarkDraft = {
                                showScoresSheet = false
                                triggerMark()
                            }
                        )
                    }

                    if (hasCurrentMark) {
                        item {
                            FourScoreBarsCard(
                                taskType = currentTaskData.taskType,
                                scores = displayScores,
                                taskBand = IeltsScoring.calculateTaskBand(displayScores),
                                showDetails = showScoreDetails,
                                onToggleDetails = { showScoreDetails = !showScoreDetails }
                            )
                        }

                        if (displayComments.isNotEmpty()) {
                            item {
                                InlineCommentsCard(
                                    draftText = currentDraft,
                                    comments = displayComments,
                                    expanded = showInlineComments,
                                    onToggle = { showInlineComments = !showInlineComments }
                                )
                            }
                        }

                        if (displayRewrite75.isNotBlank()) {
                            item {
                                CollapsibleModelCard(
                                    title = "Band 7.5 Rewrite",
                                    bodyText = displayRewrite75,
                                    expanded = showBand75Rewrite,
                                    onToggle = { showBand75Rewrite = !showBand75Rewrite },
                                    accentBorder = NeutralBorder
                                )
                            }
                        }

                        if (displayModel8.isNotBlank()) {
                            item {
                                CollapsibleModelCard(
                                    title = "Band 8 Model Answer",
                                    bodyText = displayModel8,
                                    expanded = showBand8Model,
                                    onToggle = { showBand8Model = !showBand8Model },
                                    accentBorder = BrandTeal.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val OcrLegendPattern = Regex("\\b[A-Za-z]:\\s*[-=~]+[-=~oO0+*]*")
private val OcrBareDebrisToken = Regex("(?<=^|\\s)(?:VI|Il|lI|II|III|IV|-+0+-+|[-~=_*+|@]{2,})(?=\\s|$)")
private val PromptTimePrefix = Regex("^You should spend about \\d+ minutes on this task\\.\\s*", RegexOption.IGNORE_CASE)
private val PromptSummariseSuffix = Regex("\\s*Summarise the information.*$", RegexOption.IGNORE_CASE)
private val PromptWriteWordsSuffix = Regex("\\s*Write at least \\d+ words\\.?$", RegexOption.IGNORE_CASE)

private fun extractTask1ChartCaption(
    chartCaptionLines: List<String>,
    prompt: String
): String? {
    val fromChartLines = chartCaptionLines
        .asSequence()
        .flatMap { it.lineSequence() }
        .map(::cleanCaptionCandidate)
        .firstOrNull(::isMeaningfulCaption)
    if (fromChartLines != null) {
        return fromChartLines
    }

    val strippedPrompt = prompt
        .replace('\u2014', '-')
        .replace('\u2013', '-')
        .replace(PromptTimePrefix, "")
        .replace(PromptSummariseSuffix, "")
        .replace(PromptWriteWordsSuffix, "")

    return strippedPrompt
        .lineSequence()
        .map(::cleanCaptionCandidate)
        .firstOrNull(::isMeaningfulCaption)
}

private fun cleanCaptionCandidate(raw: String): String {
    return raw
        .replace('\u2014', '-')
        .replace('\u2013', '-')
        .replace(OcrLegendPattern, " ")
        .replace(OcrBareDebrisToken, " ")
        .replace(Regex("\\s+"), " ")
        .trim { ch ->
            ch.isWhitespace() || ch in charArrayOf('@', '~', '|', '*', '_', '=', '^', '`', '\u2022', '\u25A0', '\u25A1')
        }
        .trimEnd('.', ':', ';', ',')
        .trim()
}

private fun isMeaningfulCaption(cleaned: String): Boolean {
    if (cleaned.length < 6) return false
    val letters = cleaned.count { it.isLetter() }
    if (letters < 5) return false
    val words = cleaned.split(Regex("\\s+")).filter { word ->
        word.any { it.isLetter() }
    }
    if (words.size < 2) return false
    val nonSpace = cleaned.count { !it.isWhitespace() }
    val readable = cleaned.count {
        it.isLetterOrDigit() || it in charArrayOf('-', ',', '\'', '(', ')', '/', '&')
    }
    return nonSpace > 0 && readable * 10 >= nonSpace * 7
}

@Composable
private fun PriorWritingScoresCompactCard(
    testLabel: String,
    task1Band: Double?,
    task2Band: Double?,
    overallBand: Double?,
    onClick: () -> Unit
) {
    val summary = when {
        overallBand != null -> "Band ${IeltsScoring.formatBand(overallBand)}"
        task1Band != null -> "Task 1: Band ${IeltsScoring.formatBand(task1Band)}"
        task2Band != null -> "Task 2: Band ${IeltsScoring.formatBand(task2Band)}"
        else -> "Not marked yet"
    }
    val hasBand = overallBand != null || task1Band != null || task2Band != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.ItemGap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "$testLabel Scores",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (hasBand) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasBand) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = IeltsIcons.ChevronRight,
                contentDescription = "Open scores",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IeltsSpacing.ActionIcon)
            )
        }
    }
}

@Composable
private fun PromptDisclosureCard(
    prompt: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.ItemGap),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) IeltsIcons.ExpandLess else IeltsIcons.ExpandMore,
                contentDescription = if (expanded) "Collapse prompt" else "Expand prompt",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IeltsSpacing.ActionIcon)
            )
        }
    }
}

@Composable
private fun WordCountWarningStrip(
    wordCount: Int,
    minWords: Int,
    firstCriterionName: String
) {
    Surface(
        color = WarningAmberBg,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = IeltsSpacing.ItemGap, vertical = IeltsSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = IeltsIcons.Warning,
                contentDescription = "Word count warning",
                tint = WarningAmber,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Text(
                text = "Word warning: $wordCount/$minWords words may lower $firstCriterionName.",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WritingMarkErrorCard(
    message: String,
    onRetry: () -> Unit,
    retryEnabled: Boolean
) {
    Surface(
        color = ErrorRedBg,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = IeltsSpacing.ItemGap, vertical = IeltsSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = IeltsIcons.OfflineWarning,
                contentDescription = "Marking error",
                tint = ErrorRed,
                modifier = Modifier.size(IeltsSpacing.FeatureIcon)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onRetry,
                enabled = retryEnabled
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun OverallWritingBandCard(
    task1Band: Double?,
    task2Band: Double?,
    overallBand: Double?,
    isMarking: Boolean,
    onMarkDraft: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = BrandBlueSoft.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            if (task1Band == null && task2Band == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                    ) {
                        Text(
                            text = "Not marked yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Formula: round((Task 1 + Task 2 x 2) / 3 to 0.5)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(IeltsSpacing.Sm))
                    Button(
                        onClick = onMarkDraft,
                        enabled = !isMarking,
                        modifier = Modifier.height(IeltsSpacing.TouchTarget)
                    ) {
                        Text("Review")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                    ) {
                        Text(
                            text = "Overall Writing Band",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Formula: round((Task 1 + Task 2 x 2) / 3 to 0.5)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = overallBand?.let { IeltsScoring.formatBand(it) } ?: "Not marked yet",
                        style = if (overallBand != null) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
                ) {
                    Text(
                        text = "Task 1: ${task1Band?.let { IeltsScoring.formatBand(it) } ?: "Not marked yet"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Task 2 (2x): ${task2Band?.let { IeltsScoring.formatBand(it) } ?: "Not marked yet"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FourScoreBarsCard(
    taskType: WritingTaskType,
    scores: List<CriterionScore>,
    taskBand: Double,
    showDetails: Boolean,
    onToggleDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${taskType.shortLabel} Criteria",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    Text(
                        text = "Band ${IeltsScoring.formatBand(taskBand)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onToggleDetails) {
                        Text(if (showDetails) "Hide notes" else "Notes")
                    }
                }
            }

            scores.forEach { score ->
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = score.criterion.fullName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = IeltsScoring.formatBand(score.band),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (score.band / 9.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    AnimatedVisibility(visible = showDetails) {
                        Text(
                            text = score.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = IeltsSpacing.Xs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineCommentsCard(
    draftText: String,
    comments: List<InlineComment>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val annotatedDraft = buildAnnotatedDraft(draftText, comments)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                ) {
                    Text(
                        text = "Inline Comments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${comments.size} notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) IeltsIcons.ExpandLess else IeltsIcons.ExpandMore,
                    contentDescription = if (expanded) "Hide inline comments" else "Show inline comments",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
                    HorizontalDivider(color = NeutralBorder)

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = annotatedDraft,
                            modifier = Modifier.padding(IeltsSpacing.ItemGap),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    comments.forEach { comment ->
                        Surface(
                            color = WarningAmberBg.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(IeltsSpacing.ItemGap),
                                verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                            ) {
                                Text(
                                    text = "#${comment.id} ${comment.criterion.fullName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber
                                )
                                Text(
                                    text = "\"${comment.excerpt}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = comment.note,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Suggestion: ${comment.suggestion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandTeal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildAnnotatedDraft(
    draftText: String,
    comments: List<InlineComment>
): AnnotatedString {
    if (draftText.isBlank()) {
        return AnnotatedString("No draft text yet.")
    }

    data class MatchSpan(val start: Int, val end: Int, val commentId: Int)

    val matches = mutableListOf<MatchSpan>()
    comments.forEach { comment ->
        if (comment.excerpt.isNotBlank()) {
            val start = draftText.indexOf(comment.excerpt, ignoreCase = true)
            if (start >= 0) {
                val end = start + comment.excerpt.length
                val overlaps = matches.any { start < it.end && end > it.start }
                if (!overlaps) {
                    matches.add(MatchSpan(start, end, comment.id))
                }
            }
        }
    }

    matches.sortBy { it.start }

    return buildAnnotatedString {
        var cursor = 0
        matches.forEach { match ->
            if (match.start > cursor) {
                append(draftText.substring(cursor, match.start))
            }
            withStyle(
                SpanStyle(
                    background = WarningAmberBg,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(draftText.substring(match.start, match.end))
            }
            withStyle(
                SpanStyle(
                    color = WarningAmber,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(" [#${match.commentId}]")
            }
            cursor = match.end
        }
        if (cursor < draftText.length) {
            append(draftText.substring(cursor))
        }
    }
}

@Composable
private fun CollapsibleModelCard(
    title: String,
    bodyText: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    accentBorder: Color
) {
    val wordCount = IeltsScoring.countWords(bodyText)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$wordCount words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) IeltsIcons.ExpandLess else IeltsIcons.ExpandMore,
                    contentDescription = if (expanded) "Hide $title" else "Show $title",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)) {
                    HorizontalDivider(color = NeutralBorder)
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
