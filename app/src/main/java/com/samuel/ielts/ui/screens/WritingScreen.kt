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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.samuel.ielts.data.Attempt
import com.samuel.ielts.model.CriterionScore
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.InlineComment
import com.samuel.ielts.model.WritingCriterion
import com.samuel.ielts.model.WritingTaskData
import com.samuel.ielts.model.WritingTaskType
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

@Composable
fun WritingScreen(
    testLabel: String,
    task1Data: WritingTaskData,
    task2Data: WritingTaskData,
    onOverallBandUpdated: (Double) -> Unit,
    questionId: Long = 0L,
    chartBase64: String? = null,
    onMarkTask: suspend (taskNumber: Int, essay: String, chartBase64: String?) -> MarkResult = { _, _, _ ->
        throw IllegalStateException("AI marker is not configured.")
    },
    onSaveAttempt: (Attempt) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var activeTask by rememberSaveable { mutableStateOf(WritingTaskType.TASK_1) }
    var task1Draft by rememberSaveable(task1Data.prompt) { mutableStateOf(task1Data.initialDraft) }
    var task2Draft by rememberSaveable(task2Data.prompt) { mutableStateOf(task2Data.initialDraft) }
    var showFeedback by rememberSaveable { mutableStateOf(true) }
    var showBand75Rewrite by rememberSaveable { mutableStateOf(false) }

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

    val task1Scores = task1MarkResult?.toCriterionScores(WritingTaskType.TASK_1) ?: task1Data.scores
    val task2Scores = task2MarkResult?.toCriterionScores(WritingTaskType.TASK_2) ?: task2Data.scores
    val task1Band = IeltsScoring.calculateTaskBand(task1Scores)
    val task2Band = IeltsScoring.calculateTaskBand(task2Scores)
    val overallWritingBand = IeltsScoring.calculateWritingOverall(task1Band, task2Band)

    val displayScores = if (activeTask == WritingTaskType.TASK_1) task1Scores else task2Scores
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
                showFeedback = true

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
                val nextOverall = IeltsScoring.calculateWritingOverall(
                    task1Band = IeltsScoring.calculateTaskBand(nextTask1Scores),
                    task2Band = IeltsScoring.calculateTaskBand(nextTask2Scores)
                )
                onOverallBandUpdated(nextOverall)
                onSaveAttempt(
                    Attempt(
                        questionId = questionId,
                        userAnswer = essayToMark,
                        correct = result.overall >= 6.0,
                        aiScoresJson = result.rawJson,
                        createdAt = System.currentTimeMillis()
                    )
                )
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(IeltsSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$testLabel Writing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Switch between Task 1 (150 words) and Task 2 (250 words).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OverallWritingBandCard(
                task1Band = task1Band,
                task2Band = task2Band,
                overallBand = overallWritingBand
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WritingTaskType.entries.forEach { taskType ->
                    FilterChip(
                        selected = activeTask == taskType,
                        onClick = {
                            activeTask = taskType
                            markError = null
                        },
                        label = {
                            Text("${taskType.shortLabel} (${taskType.minWords}+ words)")
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NeutralBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = currentTaskData.taskType.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentTaskData.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

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
                            .height(200.dp),
                        label = { Text("Your ${currentTaskData.taskType.shortLabel} draft") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Words: $wordCount / $minWords target",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (underWordWarning) WarningAmber else SuccessGreen
                        )
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
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Marking...")
                            } else {
                                Text("Review Draft")
                            }
                        }
                    }

                    if (underWordWarning) {
                        WordCountWarningBanner(
                            wordCount = wordCount,
                            minWords = minWords,
                            firstCriterionName = "${activeTask.firstCriterionName} (${activeTask.firstCriterionCode})"
                        )
                    } else {
                        Surface(
                            color = SuccessGreenBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Word count target met ($wordCount words).",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }

        val currentError = markError
        if (currentError != null) {
            item {
                WritingMarkErrorCard(
                    message = currentError,
                    onRetry = { triggerMark() },
                    retryEnabled = !isMarking
                )
            }
        }

        if (showFeedback) {
            item {
                FourScoreBarsCard(
                    taskType = currentTaskData.taskType,
                    scores = displayScores,
                    taskBand = IeltsScoring.calculateTaskBand(displayScores)
                )
            }

            item {
                InlineCommentsCard(
                    draftText = currentDraft,
                    comments = displayComments
                )
            }

            item {
                Band75RewriteCollapsibleCard(
                    expanded = showBand75Rewrite,
                    rewriteText = displayRewrite75,
                    onToggle = { showBand75Rewrite = !showBand75Rewrite }
                )
            }

            item {
                Band8ModelCard(
                    modelText = displayModel8
                )
            }
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Marking error warning",
                    tint = ErrorRed,
                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Could not mark draft",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
            }
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
    task1Band: Double,
    task2Band: Double,
    overallBand: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BrandBlueSoft.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Overall Writing Band",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Formula: round((T1 + T2 x 2) / 3 to 0.5)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = IeltsScoring.formatBand(overallBand),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Task 1: ${IeltsScoring.formatBand(task1Band)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Task 2 (2x weight): ${IeltsScoring.formatBand(task2Band)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun WordCountWarningBanner(
    wordCount: Int,
    minWords: Int,
    firstCriterionName: String
) {
    Surface(
        color = WarningAmberBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Word count warning",
                tint = WarningAmber,
                modifier = Modifier.size(IeltsSpacing.ActionIcon)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Word count warning ($wordCount / $minWords words)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
                Text(
                    text = "You can still review and submit this draft. Writing under $minWords words may lower your $firstCriterionName score.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber
                )
            }
        }
    }
}

@Composable
private fun FourScoreBarsCard(
    taskType: WritingTaskType,
    scores: List<CriterionScore>,
    taskBand: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${taskType.shortLabel} Scores (${taskType.firstCriterionCode}, CC, LR, GRA)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Band ${IeltsScoring.formatBand(taskBand)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            scores.forEach { score ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${score.criterion.code}: ${score.criterion.fullName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
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
                            .height(10.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = score.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineCommentsCard(
    draftText: String,
    comments: List<InlineComment>
) {
    val annotatedDraft = buildAnnotatedDraft(draftText, comments)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Inline Comments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = annotatedDraft,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider(color = NeutralBorder)

            comments.forEach { comment ->
                Surface(
                    color = WarningAmberBg.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = WarningAmber,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "#${comment.id} ${comment.criterion.code}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "\"${comment.excerpt}\"",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = comment.note,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Fix: ${comment.suggestion}",
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
private fun Band75RewriteCollapsibleCard(
    expanded: Boolean,
    rewriteText: String,
    onToggle: () -> Unit
) {
    val wordCount = IeltsScoring.countWords(rewriteText)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Band 7.5 Rewrite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (expanded) {
                            "Tap to hide ($wordCount words)"
                        } else {
                            "Tap to open improved version ($wordCount words)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(IeltsSpacing.TouchTarget)
                ) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) "Collapse Band 7.5 rewrite" else "Expand Band 7.5 rewrite",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = NeutralBorder)
                    Text(
                        text = rewriteText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun Band8ModelCard(
    modelText: String
) {
    val wordCount = IeltsScoring.countWords(modelText)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = BrandTealSoft,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Band 8",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandTeal
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Band 8 Model",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$wordCount words",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = NeutralBorder)

            Text(
                text = modelText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
