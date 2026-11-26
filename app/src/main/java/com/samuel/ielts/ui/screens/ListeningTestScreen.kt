package com.samuel.ielts.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.samuel.ielts.model.IeltsBook
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.ListeningAudioManifest
import com.samuel.ielts.model.ListeningAudioTrack
import com.samuel.ielts.model.ListeningExamEngine
import com.samuel.ielts.model.ListeningExamPhase
import com.samuel.ielts.model.ListeningExamResult
import com.samuel.ielts.model.QuizAttemptSummary
import com.samuel.ielts.model.QuizQuestion
import com.samuel.ielts.model.QuizQuestionGroup
import com.samuel.ielts.model.QuizSectionBundle
import com.samuel.ielts.model.QuizSkillMode
import com.samuel.ielts.model.TfngOption
import com.samuel.ielts.model.YnngOption
import com.samuel.ielts.ui.components.AssetImageState
import com.samuel.ielts.ui.components.AssetImageThumbnailCard
import com.samuel.ielts.ui.components.AssetImageZoomDialog
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.components.MissingAssetImagePlaceholder
import com.samuel.ielts.ui.components.decodeAssetImageState
import com.samuel.ielts.ui.theme.IeltsSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ListeningTestScreen"
private const val SEEK_STEP_MS = 15_000L

@Composable
fun ListeningTestScreen(
    testId: String,
    book: IeltsBook,
    testNumber: Int,
    testLabel: String,
    track: ListeningAudioTrack,
    sections: List<QuizSectionBundle>,
    onQuizSubmitted: (QuizAttemptSummary) -> Unit,
    onBack: () -> Unit,
    onOpenSectionDrill: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Fail loudly if any displayed question lacks a non-blank answer key.
    remember(testId, sections) {
        try {
            ListeningExamEngine.validateQuestionsKeyBacked(sections)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Listening key validation failed for $testId: ${e.message}", e)
            throw e
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val orderedSections = remember(sections) {
        sections.sortedBy { it.sectionNumber }
    }
    val allQuestions = remember(orderedSections) {
        orderedSections.flatMap { it.questions }
    }

    var phase by rememberSaveable(testId) { mutableStateOf(ListeningExamPhase.READY) }
    var audioDurationSec by rememberSaveable(testId) { mutableIntStateOf(track.durationSeconds) }
    var audioPositionSec by rememberSaveable(testId) { mutableIntStateOf(0) }
    var transferRemainingSec by rememberSaveable(testId) { mutableIntStateOf(track.transferTimeSeconds) }
    var practiceOverride by rememberSaveable(testId) { mutableStateOf(false) }
    var activeCheckSection by rememberSaveable(testId) { mutableStateOf<Int?>(null) }
    var checkPauseRemainingSec by rememberSaveable(testId) { mutableIntStateOf(0) }
    var playerErrorMessage by remember(testId) { mutableStateOf<String?>(null) }
    var examResult by remember(testId) { mutableStateOf<ListeningExamResult?>(null) }

    val mcqAnswers = remember(testId) { mutableStateMapOf<String, Set<Int>>() }
    val tfngAnswers = remember(testId) { mutableStateMapOf<String, TfngOption>() }
    val ynngAnswers = remember(testId) { mutableStateMapOf<String, YnngOption>() }
    val fillAnswers = remember(testId) { mutableStateMapOf<String, String>() }

    val player = remember(testId, track.assetPath) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            setPlaybackSpeed(1.0f)
            setMediaItem(MediaItem.fromUri(track.assetUri))
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val reportedDurationSec = (player.duration / 1000L).toInt()
                    if (reportedDurationSec > 0) {
                        audioDurationSec = reportedDurationSec
                    }
                    playerErrorMessage = null
                } else if (playbackState == Player.STATE_ENDED) {
                    audioPositionSec = audioDurationSec
                    activeCheckSection = null
                    checkPauseRemainingSec = 0
                    if (phase != ListeningExamPhase.CHECKED) {
                        phase = ListeningExamPhase.TRANSFER_TIME
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    activeCheckSection = null
                    checkPauseRemainingSec = 0
                    if (phase != ListeningExamPhase.CHECKED && phase != ListeningExamPhase.TRANSFER_TIME) {
                        phase = ListeningExamPhase.AUDIO_PLAYING
                    }
                } else if (phase == ListeningExamPhase.AUDIO_PLAYING) {
                    phase = ListeningExamPhase.AUDIO_PAUSED
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Audio playback failed for ${track.assetPath}: ${error.message}", error)
                playerErrorMessage = "Audio playback failed. Tap Retry to reload ${track.assetPath}."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && player.isPlaying) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(player, phase) {
        while (phase == ListeningExamPhase.AUDIO_PLAYING) {
            if (player.playbackParameters.speed != 1.0f) {
                player.setPlaybackSpeed(1.0f)
            }
            if (player.repeatMode != Player.REPEAT_MODE_OFF) {
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
            audioPositionSec = (player.currentPosition / 1000L).toInt().coerceIn(0, audioDurationSec)
            delay(500L)
        }
    }

    LaunchedEffect(phase) {
        while (phase == ListeningExamPhase.TRANSFER_TIME && transferRemainingSec > 0) {
            delay(1000L)
            transferRemainingSec = (transferRemainingSec - 1).coerceAtLeast(0)
        }
    }

    LaunchedEffect(activeCheckSection, checkPauseRemainingSec) {
        if (activeCheckSection != null && checkPauseRemainingSec > 0) {
            delay(1000L)
            checkPauseRemainingSec = (checkPauseRemainingSec - 1).coerceAtLeast(0)
        }
    }

    val allImagePaths = remember(orderedSections) {
        orderedSections
            .flatMap { it.groups }
            .mapNotNull { it.imageAsset?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
    }
    val groupImageStates = remember(testId) {
        mutableStateMapOf<String, AssetImageState>()
    }
    LaunchedEffect(context, testId, allImagePaths) {
        allImagePaths.forEach { assetPath ->
            if (groupImageStates[assetPath] !is AssetImageState.Ready) {
                groupImageStates[assetPath] = AssetImageState.Loading
                groupImageStates[assetPath] = decodeAssetImageState(context, assetPath)
            }
        }
    }
    var zoomedImage by remember(testId) { mutableStateOf<ListeningZoomedImage?>(null) }

    val checked = phase == ListeningExamPhase.CHECKED
    val wholeRemainingSec = ListeningExamEngine.wholeTestRemainingSeconds(
        phase = phase,
        audioDurationSeconds = audioDurationSec,
        audioPositionSeconds = audioPositionSec,
        transferRemainingSeconds = transferRemainingSec
    )
    val timerIsRed = ListeningExamEngine.isRedWarning(wholeRemainingSec)
    val inFinalAudio30Sec = (phase == ListeningExamPhase.AUDIO_PLAYING || phase == ListeningExamPhase.AUDIO_PAUSED) &&
        (audioDurationSec - audioPositionSec) in 1..ListeningAudioManifest.SECTION_CHECK_PAUSE_SECONDS
    val canCheckAnswers = ListeningExamEngine.canCheckAnswers(phase, practiceOverride)

    val answeredCount = allQuestions.count { q ->
        isQuestionAnswered(q, mcqAnswers, tfngAnswers, ynngAnswers, fillAnswers)
    }

    val sectionScrollIndices = remember(orderedSections, checked) {
        buildSectionScrollIndexMap(orderedSections, hasResultBanner = checked)
    }

    val onTogglePlayPause: () -> Unit = {
        if (phase != ListeningExamPhase.TRANSFER_TIME && phase != ListeningExamPhase.CHECKED) {
            if (player.isPlaying) {
                player.pause()
            } else {
                activeCheckSection = null
                checkPauseRemainingSec = 0
                player.setPlaybackSpeed(1.0f)
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.play()
            }
        }
    }

    val onRetryAudio: () -> Unit = {
        playerErrorMessage = null
        player.setMediaItem(MediaItem.fromUri(track.assetUri))
        player.prepare()
        player.seekTo(audioPositionSec * 1000L)
        player.setPlaybackSpeed(1.0f)
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    val onSeekBy: (Long) -> Unit = { deltaMs ->
        if (practiceOverride && phase != ListeningExamPhase.CHECKED) {
            val targetMs = (player.currentPosition + deltaMs)
                .coerceIn(0L, audioDurationSec * 1000L)
            player.seekTo(targetMs)
            audioPositionSec = (targetMs / 1000L).toInt()
            if (phase == ListeningExamPhase.TRANSFER_TIME && audioPositionSec < audioDurationSec) {
                phase = ListeningExamPhase.AUDIO_PAUSED
            }
        }
    }

    val onStartSectionCheckPause: (Int) -> Unit = { sectionNumber ->
        if (player.isPlaying) {
            player.pause()
        }
        activeCheckSection = sectionNumber
        checkPauseRemainingSec = ListeningAudioManifest.SECTION_CHECK_PAUSE_SECONDS
    }

    val onResumeFromCheckPause: () -> Unit = {
        activeCheckSection = null
        checkPauseRemainingSec = 0
        if (phase == ListeningExamPhase.AUDIO_PAUSED || phase == ListeningExamPhase.READY) {
            player.setPlaybackSpeed(1.0f)
            player.play()
        }
    }

    val onCheckAnswers: () -> Unit = {
        if (player.isPlaying) {
            player.pause()
        }
        activeCheckSection = null
        checkPauseRemainingSec = 0
        val result = ListeningExamEngine.scoreExam(
            sections = orderedSections,
            mcqAnswers = mcqAnswers,
            tfngAnswers = tfngAnswers,
            ynngAnswers = ynngAnswers,
            fillAnswers = fillAnswers
        )
        examResult = result
        phase = ListeningExamPhase.CHECKED
        onQuizSubmitted(
            QuizAttemptSummary(
                testId = testId,
                book = book,
                testNumber = testNumber,
                skill = QuizSkillMode.LISTENING,
                correctCount = result.totalCorrect,
                totalCount = result.totalQuestions,
                band = result.band,
                typeBreakdown = result.typeBreakdown
            )
        )
        scope.launch {
            listState.animateScrollToItem(0)
        }
    }

    val onResetExam: () -> Unit = {
        player.pause()
        player.seekTo(0L)
        player.setPlaybackSpeed(1.0f)
        player.repeatMode = Player.REPEAT_MODE_OFF
        phase = ListeningExamPhase.READY
        audioPositionSec = 0
        transferRemainingSec = track.transferTimeSeconds
        activeCheckSection = null
        checkPauseRemainingSec = 0
        examResult = null
        mcqAnswers.clear()
        tfngAnswers.clear()
        ynngAnswers.clear()
        fillAnswers.clear()
        scope.launch {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ListeningTopBar(
            testLabel = testLabel,
            answeredCount = answeredCount,
            totalCount = allQuestions.size,
            onBack = onBack,
            onOpenSectionDrill = onOpenSectionDrill
        )

        ListeningAudioControlCard(
            phase = phase,
            audioPositionSec = audioPositionSec,
            audioDurationSec = audioDurationSec,
            wholeRemainingSec = wholeRemainingSec,
            transferRemainingSec = transferRemainingSec,
            timerIsRed = timerIsRed,
            practiceOverride = practiceOverride,
            activeCheckSection = activeCheckSection,
            checkPauseRemainingSec = checkPauseRemainingSec,
            inFinalAudio30Sec = inFinalAudio30Sec,
            errorMessage = playerErrorMessage,
            onTogglePlayPause = onTogglePlayPause,
            onTogglePracticeOverride = { practiceOverride = !practiceOverride },
            onSeekBack = { onSeekBy(-SEEK_STEP_MS) },
            onSeekForward = { onSeekBy(SEEK_STEP_MS) },
            onResumeFromCheckPause = onResumeFromCheckPause,
            onDismissCheckPause = {
                activeCheckSection = null
                checkPauseRemainingSec = 0
            },
            onRetryAudio = onRetryAudio
        )

        SectionJumpChipRow(
            sections = orderedSections,
            mcqAnswers = mcqAnswers,
            tfngAnswers = tfngAnswers,
            ynngAnswers = ynngAnswers,
            fillAnswers = fillAnswers,
            onJumpToSection = { sectionNum ->
                val targetIndex = sectionScrollIndices[sectionNum] ?: 0
                scope.launch {
                    listState.animateScrollToItem(targetIndex)
                }
            }
        )

        HorizontalDivider(color = NeutralBorder)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
        ) {
            val result = examResult
            if (checked && result != null) {
                item(key = "listening_result_summary") {
                    ListeningResultSummaryCard(result = result)
                }
            }

            orderedSections.forEach { section ->
                item(key = "section_header_${section.id}") {
                    ListeningSectionHeaderCard(
                        section = section,
                        showCheckButton = !checked && phase != ListeningExamPhase.TRANSFER_TIME,
                        isCheckingThisSection = activeCheckSection == section.sectionNumber,
                        onCheckSection = { onStartSectionCheckPause(section.sectionNumber) }
                    )
                }

                val displayGroups = section.groups.ifEmpty {
                    listOf(
                        QuizQuestionGroup(
                            id = "${section.id}_default",
                            instructions = section.instructions,
                            questions = section.questions
                        )
                    )
                }

                displayGroups.forEach { group ->
                    if (group.instructions.isNotBlank()) {
                        item(key = "${group.id}_instructions") {
                            GroupInstructionBanner(
                                group = group
                            )
                        }
                    }

                    val normalizedImageAsset = group.imageAsset?.trim()?.takeIf(String::isNotEmpty)
                    val showMissingPlaceholder = normalizedImageAsset == null && isMapOrPlanGroup(group)
                    if (normalizedImageAsset != null || showMissingPlaceholder) {
                        item(key = "${group.id}_visual") {
                            val sectionLabel = section.shortLabel
                            if (normalizedImageAsset != null) {
                                when (val imageState = groupImageStates[normalizedImageAsset] ?: AssetImageState.Loading) {
                                    is AssetImageState.Ready -> {
                                        AssetImageThumbnailCard(
                                            imageBitmap = imageState.imageBitmap,
                                            contentDescription = groupThumbnailContentDescription(group, sectionLabel),
                                            caption = groupImageCaption(group),
                                            onClick = {
                                                zoomedImage = ListeningZoomedImage(
                                                    imageBitmap = imageState.imageBitmap,
                                                    title = groupZoomDialogTitle(group),
                                                    contentDescription = groupZoomImageContentDescription(group, sectionLabel)
                                                )
                                            }
                                        )
                                    }

                                    AssetImageState.Missing -> {
                                        MissingAssetImagePlaceholder(
                                            message = missingGroupImageMessage(group)
                                        )
                                    }

                                    AssetImageState.Loading -> Unit
                                }
                            } else {
                                MissingAssetImagePlaceholder(
                                    message = missingGroupImageMessage(group)
                                )
                            }
                        }
                    }

                    items(items = group.questions, key = { it.id }) { question ->
                        val isCorrectOverride = if (checked) {
                            ListeningExamEngine.isQuestionCorrect(
                                question = question,
                                mcqAnswers = mcqAnswers,
                                tfngAnswers = tfngAnswers,
                                ynngAnswers = ynngAnswers,
                                fillAnswers = fillAnswers
                            )
                        } else {
                            null
                        }

                        when (question) {
                            is QuizQuestion.Mcq -> McqQuestionCard(
                                question = question,
                                selectedIndices = mcqAnswers[question.id].orEmpty(),
                                checked = checked,
                                isCorrectOverride = isCorrectOverride,
                                interactive = !checked,
                                onToggleOption = { index ->
                                    if (question.requiredSelections <= 1) {
                                        mcqAnswers[question.id] = setOf(index)
                                    } else {
                                        val current = mcqAnswers[question.id].orEmpty().toMutableSet()
                                        if (index in current) current.remove(index) else current.add(index)
                                        mcqAnswers[question.id] = current
                                    }
                                }
                            )

                            is QuizQuestion.TrueFalseNotGiven -> TfngQuestionCard(
                                question = question,
                                selectedOption = tfngAnswers[question.id],
                                checked = checked,
                                isCorrectOverride = isCorrectOverride,
                                interactive = !checked,
                                onSelect = { tfngAnswers[question.id] = it }
                            )

                            is QuizQuestion.YesNoNotGiven -> YnngQuestionCard(
                                question = question,
                                selectedOption = ynngAnswers[question.id],
                                checked = checked,
                                isCorrectOverride = isCorrectOverride,
                                interactive = !checked,
                                onSelect = { ynngAnswers[question.id] = it }
                            )

                            is QuizQuestion.FillBlank -> FillBlankQuestionCard(
                                question = question,
                                answerText = fillAnswers[question.id].orEmpty(),
                                checked = checked,
                                isCorrectOverride = isCorrectOverride,
                                interactive = !checked,
                                onValueChange = { fillAnswers[question.id] = it }
                            )
                        }
                    }
                }
            }

            item(key = "listening_exam_actions") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = IeltsSpacing.Xs, bottom = IeltsSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    if (!canCheckAnswers) {
                        Text(
                            text = "Check Answers unlocks when the audio finishes and transfer time starts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
                    ) {
                        Button(
                            onClick = onCheckAnswers,
                            enabled = canCheckAnswers && !checked,
                            modifier = Modifier
                                .weight(1f)
                                .height(IeltsSpacing.TouchTarget)
                        ) {
                            Text(if (checked) "Answers Checked" else "Check Answers")
                        }
                        OutlinedButton(
                            onClick = onResetExam,
                            modifier = Modifier.height(IeltsSpacing.TouchTarget)
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }

    val activeZoom = zoomedImage
    if (activeZoom != null) {
        AssetImageZoomDialog(
            imageBitmap = activeZoom.imageBitmap,
            title = activeZoom.title,
            imageContentDescription = activeZoom.contentDescription,
            closeContentDescription = "Close image preview",
            onDismiss = { zoomedImage = null }
        )
    }
}

private data class ListeningZoomedImage(
    val imageBitmap: ImageBitmap,
    val title: String,
    val contentDescription: String
)

@Composable
private fun ListeningTopBar(
    testLabel: String,
    answeredCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onOpenSectionDrill: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(IeltsSpacing.TouchTarget)
        ) {
            Icon(
                imageVector = IeltsIcons.Back,
                contentDescription = "Back",
                modifier = Modifier.size(IeltsSpacing.ActionIcon)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$testLabel Listening",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Answered $answeredCount/$totalCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onOpenSectionDrill) {
            Text("Drill mode")
        }
    }
}

@Composable
private fun ListeningAudioControlCard(
    phase: ListeningExamPhase,
    audioPositionSec: Int,
    audioDurationSec: Int,
    wholeRemainingSec: Int,
    transferRemainingSec: Int,
    timerIsRed: Boolean,
    practiceOverride: Boolean,
    activeCheckSection: Int?,
    checkPauseRemainingSec: Int,
    inFinalAudio30Sec: Boolean,
    errorMessage: String?,
    onTogglePlayPause: () -> Unit,
    onTogglePracticeOverride: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onResumeFromCheckPause: () -> Unit,
    onDismissCheckPause: () -> Unit,
    onRetryAudio: () -> Unit
) {
    val progress = if (audioDurationSec > 0) {
        (audioPositionSec.toFloat() / audioDurationSec.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val timerContainerColor = if (timerIsRed) ErrorRedBg else BrandBlueSoft
    val timerContentColor = if (timerIsRed) ErrorRed else BrandBlue
    val phaseLabel = when {
        phase == ListeningExamPhase.CHECKED -> "Test checked"
        phase == ListeningExamPhase.TRANSFER_TIME -> "Transfer time (10:00)"
        practiceOverride -> "Practice override"
        else -> "Exam mode: single play, 1.0x"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.Xs),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, NeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (practiceOverride && phase != ListeningExamPhase.CHECKED) {
                        WarningAmber
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Surface(
                    color = timerContainerColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.Timer,
                            contentDescription = "Time remaining",
                            tint = timerContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = ListeningExamEngine.formatRemainingLabel(wholeRemainingSec),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = timerContentColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
            ) {
                val isPlaying = phase == ListeningExamPhase.AUDIO_PLAYING
                val canControlAudio = phase != ListeningExamPhase.TRANSFER_TIME && phase != ListeningExamPhase.CHECKED
                Button(
                    onClick = onTogglePlayPause,
                    enabled = canControlAudio,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) IeltsIcons.PauseAudio else IeltsIcons.PlayAudio,
                        contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !canControlAudio -> "Ended"
                            isPlaying -> "Pause"
                            phase == ListeningExamPhase.AUDIO_PAUSED -> "Resume"
                            else -> "Play"
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio ${ListeningExamEngine.formatClock(audioPositionSec)} / ${ListeningExamEngine.formatClock(audioDurationSec)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val audioRem = (audioDurationSec - audioPositionSec).coerceAtLeast(0)
                    Text(
                        text = "${ListeningExamEngine.formatClock(audioRem)} audio left, 1.0x fixed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (phase != ListeningExamPhase.CHECKED) {
                    FilterChip(
                        selected = practiceOverride,
                        onClick = onTogglePracticeOverride,
                        label = { Text("Practice seek") }
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (practiceOverride && phase != ListeningExamPhase.CHECKED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    OutlinedButton(
                        onClick = onSeekBack,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.SeekBack,
                            contentDescription = "Back 15 seconds",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("-15s")
                    }
                    OutlinedButton(
                        onClick = onSeekForward,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.SeekForward,
                            contentDescription = "Forward 15 seconds",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+15s")
                    }
                    Text(
                        text = "Practice override: seeking and early check enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (activeCheckSection != null) {
                Surface(
                    color = WarningAmberBg,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (checkPauseRemainingSec > 0) {
                                "Checking Section $activeCheckSection: ${ListeningExamEngine.formatClock(checkPauseRemainingSec)} left (answers hidden)"
                            } else {
                                "30s check complete for Section $activeCheckSection. Resume when ready."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WarningAmber,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            TextButton(onClick = onResumeFromCheckPause) {
                                Text("Resume", color = WarningAmber)
                            }
                            TextButton(onClick = onDismissCheckPause) {
                                Text("Hide", color = WarningAmber)
                            }
                        }
                    }
                }
            }

            if (inFinalAudio30Sec && activeCheckSection == null) {
                Surface(
                    color = BrandBlueSoft,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Final 30 seconds of audio. 10:00 transfer time starts next.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandBlue,
                        modifier = Modifier.padding(IeltsSpacing.Sm)
                    )
                }
            }

            if (phase == ListeningExamPhase.TRANSFER_TIME) {
                val transferBg = if (timerIsRed) ErrorRedBg else BrandTealSoft
                val transferFg = if (timerIsRed) ErrorRed else BrandTeal
                Surface(
                    color = transferBg,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (transferRemainingSec > 0) {
                            "Audio finished. Transfer time active: review your answers, then tap Check Answers below."
                        } else {
                            "Transfer time complete. Tap Check Answers below to score your test."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = transferFg,
                        modifier = Modifier.padding(IeltsSpacing.Sm)
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = ErrorRedBg,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetryAudio) {
                            Text("Retry", color = ErrorRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionJumpChipRow(
    sections: List<QuizSectionBundle>,
    mcqAnswers: Map<String, Set<Int>>,
    tfngAnswers: Map<String, TfngOption>,
    ynngAnswers: Map<String, YnngOption>,
    fillAnswers: Map<String, String>,
    onJumpToSection: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
    ) {
        sections.forEach { section ->
            val answered = section.questions.count { q ->
                isQuestionAnswered(q, mcqAnswers, tfngAnswers, ynngAnswers, fillAnswers)
            }
            Surface(
                onClick = { onJumpToSection(section.sectionNumber) },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, NeutralBorder)
            ) {
                Text(
                    text = "${section.shortLabel} ($answered/${section.questions.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ListeningSectionHeaderCard(
    section: QuizSectionBundle,
    showCheckButton: Boolean,
    isCheckingThisSection: Boolean,
    onCheckSection: () -> Unit
) {
    Surface(
        color = BrandBlueSoft.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue,
                    modifier = Modifier.weight(1f)
                )
                if (showCheckButton) {
                    OutlinedButton(
                        onClick = onCheckSection,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isCheckingThisSection) "Checking..." else "Check section",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Text(
                text = ListeningExamEngine.sectionCheckHint(section.sectionNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupInstructionBanner(
    group: QuizQuestionGroup
) {
    val rangeLabel = groupQuestionRangeLabel(group)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (rangeLabel != null) {
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = group.instructions,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ListeningResultSummaryCard(
    result: ListeningExamResult
) {
    var showTypeBreakdown by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = SuccessGreenBg,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = IeltsIcons.Completed,
                        contentDescription = "Listening test checked",
                        tint = SuccessGreen,
                        modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Band ${IeltsScoring.formatBand(result.band)} (${result.totalCorrect}/${result.totalQuestions})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            text = "Listening full test result",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen
                        )
                    }
                }
                if (result.typeBreakdown.isNotEmpty()) {
                    TextButton(onClick = { showTypeBreakdown = !showTypeBreakdown }) {
                        Text(
                            text = if (showTypeBreakdown) "Hide" else "Types",
                            color = SuccessGreen
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
            ) {
                result.sectionScores.forEach { sec ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "S${sec.sectionNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${sec.correct}/${sec.total}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }

            if (result.typeBreakdown.isNotEmpty()) {
                AnimatedVisibility(visible = showTypeBreakdown) {
                    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                        result.typeBreakdown.forEach { (category, pair) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = plainQuestionTypeName(category),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen
                                )
                                Text(
                                    text = "${pair.first}/${pair.second}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isQuestionAnswered(
    question: QuizQuestion,
    mcqAnswers: Map<String, Set<Int>>,
    tfngAnswers: Map<String, TfngOption>,
    ynngAnswers: Map<String, YnngOption>,
    fillAnswers: Map<String, String>
): Boolean {
    return when (question) {
        is QuizQuestion.Mcq -> mcqAnswers[question.id].orEmpty().isNotEmpty()
        is QuizQuestion.TrueFalseNotGiven -> tfngAnswers[question.id] != null
        is QuizQuestion.YesNoNotGiven -> ynngAnswers[question.id] != null
        is QuizQuestion.FillBlank -> !fillAnswers[question.id].isNullOrBlank()
    }
}

private fun buildSectionScrollIndexMap(
    sections: List<QuizSectionBundle>,
    hasResultBanner: Boolean
): Map<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    var index = if (hasResultBanner) 1 else 0
    sections.forEach { section ->
        map[section.sectionNumber] = index
        index += 1 // section header
        val groups = section.groups.ifEmpty {
            listOf(
                QuizQuestionGroup(
                    id = "${section.id}_default",
                    instructions = section.instructions,
                    questions = section.questions
                )
            )
        }
        groups.forEach { group ->
            if (group.instructions.isNotBlank()) index += 1
            val hasVisual = !group.imageAsset.isNullOrBlank() || isMapOrPlanGroup(group)
            if (hasVisual) index += 1
            index += group.questions.size
        }
    }
    return map
}
