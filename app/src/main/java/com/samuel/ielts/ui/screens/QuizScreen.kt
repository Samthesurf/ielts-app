package com.samuel.ielts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuel.ielts.data.AnswerNormalizer
import com.samuel.ielts.model.IeltsBook
import com.samuel.ielts.model.IeltsScoring
import com.samuel.ielts.model.PassageParagraph
import com.samuel.ielts.model.QuestionTypeCategory
import com.samuel.ielts.model.QuizAttemptSummary
import com.samuel.ielts.model.QuizQuestion
import com.samuel.ielts.model.QuizQuestionGroup
import com.samuel.ielts.model.QuizSectionBundle
import com.samuel.ielts.model.QuizSkillMode
import com.samuel.ielts.model.ReadingPassage
import com.samuel.ielts.model.TfngOption
import com.samuel.ielts.model.YnngOption
import com.samuel.ielts.ui.components.AssetImageState
import com.samuel.ielts.ui.components.AssetImageThumbnailCard
import com.samuel.ielts.ui.components.AssetImageZoomDialog
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.components.MissingAssetImagePlaceholder
import com.samuel.ielts.ui.components.decodeAssetImageState
import com.samuel.ielts.ui.theme.IeltsSpacing
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    testId: String,
    book: IeltsBook,
    testNumber: Int,
    testLabel: String,
    activeSkill: QuizSkillMode,
    onSelectSkill: (QuizSkillMode) -> Unit,
    listeningSections: List<QuizSectionBundle>,
    readingSections: List<QuizSectionBundle>,
    onQuizSubmitted: (QuizAttemptSummary) -> Unit,
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sections = if (activeSkill == QuizSkillMode.LISTENING) {
        listeningSections
    } else {
        readingSections
    }

    var selectedSectionIndex by rememberSaveable(testId, activeSkill.name) { mutableIntStateOf(0) }
    val safeSectionIndex = selectedSectionIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
    val currentSection = sections.getOrNull(safeSectionIndex)

    var selectedParagraph by rememberSaveable(currentSection?.id ?: testId) {
        mutableStateOf(currentSection?.passage?.paragraphs?.firstOrNull()?.label ?: "A")
    }
    var checked by rememberSaveable(testId, activeSkill.name) { mutableStateOf(false) }
    var showQuestionsSheet by rememberSaveable(testId, activeSkill.name) { mutableStateOf(false) }

    val passageListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentSection?.id) {
        selectedParagraph = currentSection?.passage?.paragraphs?.firstOrNull()?.label ?: "A"
        passageListState.scrollToItem(0)
    }

    val mcqAnswers = remember(testId, activeSkill) { mutableStateMapOf<String, Set<Int>>() }
    val tfngAnswers = remember(testId, activeSkill) { mutableStateMapOf<String, TfngOption>() }
    val ynngAnswers = remember(testId, activeSkill) { mutableStateMapOf<String, YnngOption>() }
    val fillAnswers = remember(testId, activeSkill) { mutableStateMapOf<String, String>() }

    val allSkillQuestions = remember(sections) { sections.flatMap { it.questions } }
    val sectionQuestions = currentSection?.questions.orEmpty()

    val totalCorrectCount = allSkillQuestions.count { question ->
        isQuestionCorrect(
            question = question,
            mcqAnswers = mcqAnswers,
            tfngAnswers = tfngAnswers,
            ynngAnswers = ynngAnswers,
            fillAnswers = fillAnswers
        )
    }

    val sectionCorrectCount = sectionQuestions.count { question ->
        isQuestionCorrect(
            question = question,
            mcqAnswers = mcqAnswers,
            tfngAnswers = tfngAnswers,
            ynngAnswers = ynngAnswers,
            fillAnswers = fillAnswers
        )
    }

    val sectionAnsweredCount = sectionQuestions.count { question ->
        when (question) {
            is QuizQuestion.Mcq -> mcqAnswers[question.id].orEmpty().isNotEmpty()
            is QuizQuestion.TrueFalseNotGiven -> tfngAnswers[question.id] != null
            is QuizQuestion.YesNoNotGiven -> ynngAnswers[question.id] != null
            is QuizQuestion.FillBlank -> !fillAnswers[question.id].isNullOrBlank()
        }
    }

    val onSubmitQuiz: () -> Unit = {
        checked = true
        val breakdown = computeTypeBreakdown(
            questions = allSkillQuestions,
            mcqAnswers = mcqAnswers,
            tfngAnswers = tfngAnswers,
            ynngAnswers = ynngAnswers,
            fillAnswers = fillAnswers
        )
        val band = IeltsScoring.bandFromRawScore(
            correct = totalCorrectCount,
            total = allSkillQuestions.size
        )
        onQuizSubmitted(
            QuizAttemptSummary(
                testId = testId,
                book = book,
                testNumber = testNumber,
                skill = activeSkill,
                correctCount = totalCorrectCount,
                totalCount = allSkillQuestions.size,
                band = band,
                typeBreakdown = breakdown
            )
        )
    }

    val onResetQuiz: () -> Unit = {
        checked = false
        mcqAnswers.clear()
        tfngAnswers.clear()
        ynngAnswers.clear()
        fillAnswers.clear()
    }

    Column(modifier = modifier.fillMaxSize()) {
        SlimQuizNavBar(
            activeSkill = activeSkill,
            hasListening = listeningSections.isNotEmpty(),
            hasReading = readingSections.isNotEmpty(),
            sections = sections,
            selectedSectionIndex = safeSectionIndex,
            paragraphs = if (activeSkill == QuizSkillMode.READING) {
                currentSection?.passage?.paragraphs.orEmpty()
            } else {
                emptyList()
            },
            selectedParagraph = selectedParagraph,
            onSelectSkill = { newSkill ->
                showQuestionsSheet = false
                onSelectSkill(newSkill)
            },
            onSelectSection = { index ->
                selectedSectionIndex = index
            },
            onSelectParagraph = { label, index ->
                selectedParagraph = label
                scope.launch {
                    passageListState.animateScrollToItem(index + 1)
                }
            }
        )

        HorizontalDivider(color = NeutralBorder)

        if (currentSection == null) {
            val otherSkill = if (activeSkill == QuizSkillMode.LISTENING) {
                QuizSkillMode.READING
            } else {
                QuizSkillMode.LISTENING
            }
            val otherAvailable = if (otherSkill == QuizSkillMode.LISTENING) {
                listeningSections.isNotEmpty()
            } else {
                readingSections.isNotEmpty()
            }
            LockedQuizPlaceholder(
                testLabel = testLabel,
                skill = activeSkill,
                ctaLabel = if (otherAvailable) "Switch to ${otherSkill.label}" else "Open Library",
                onCtaClick = {
                    if (otherAvailable) {
                        onSelectSkill(otherSkill)
                    } else {
                        onOpenLibrary()
                    }
                }
            )
            return@Column
        }

        val readingPassage = currentSection.passage
        if (activeSkill == QuizSkillMode.READING && readingPassage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ReadingPassageHero(
                    passage = readingPassage,
                    selectedParagraph = selectedParagraph,
                    listState = passageListState,
                    onSelectParagraph = { selectedParagraph = it },
                    modifier = Modifier.fillMaxSize()
                )

                ExtendedFloatingActionButton(
                    onClick = { showQuestionsSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(IeltsSpacing.Md)
                ) {
                    Icon(
                        imageVector = IeltsIcons.Quiz,
                        contentDescription = "Questions",
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                    Spacer(modifier = Modifier.width(IeltsSpacing.Sm))
                    Text(
                        text = if (checked) {
                            "Score $sectionCorrectCount/${sectionQuestions.size}"
                        } else {
                            "Questions $sectionAnsweredCount/${sectionQuestions.size}"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (showQuestionsSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showQuestionsSheet = false },
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
                                text = "${plainSectionLabel(currentSection.shortLabel)} Questions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showQuestionsSheet = false },
                                modifier = Modifier.size(IeltsSpacing.TouchTarget)
                            ) {
                                Icon(
                                    imageVector = IeltsIcons.Close,
                                    contentDescription = "Close questions",
                                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                                )
                            }
                        }

                        HorizontalDivider(color = NeutralBorder)

                        QuizQuestionsList(
                            activeSkill = activeSkill,
                            currentSection = currentSection,
                            sectionQuestions = sectionQuestions,
                            allSkillQuestions = allSkillQuestions,
                            sectionCorrectCount = sectionCorrectCount,
                            totalCorrectCount = totalCorrectCount,
                            checked = checked,
                            mcqAnswers = mcqAnswers,
                            tfngAnswers = tfngAnswers,
                            ynngAnswers = ynngAnswers,
                            fillAnswers = fillAnswers,
                            onSubmit = onSubmitQuiz,
                            onReset = onResetQuiz,
                            showSectionHeader = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            QuizQuestionsList(
                activeSkill = activeSkill,
                currentSection = currentSection,
                sectionQuestions = sectionQuestions,
                allSkillQuestions = allSkillQuestions,
                sectionCorrectCount = sectionCorrectCount,
                totalCorrectCount = totalCorrectCount,
                checked = checked,
                mcqAnswers = mcqAnswers,
                tfngAnswers = tfngAnswers,
                ynngAnswers = ynngAnswers,
                fillAnswers = fillAnswers,
                onSubmit = onSubmitQuiz,
                onReset = onResetQuiz,
                showSectionHeader = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun SlimQuizNavBar(
    activeSkill: QuizSkillMode,
    hasListening: Boolean,
    hasReading: Boolean,
    sections: List<QuizSectionBundle>,
    selectedSectionIndex: Int,
    paragraphs: List<PassageParagraph>,
    selectedParagraph: String,
    onSelectSkill: (QuizSkillMode) -> Unit,
    onSelectSection: (Int) -> Unit,
    onSelectParagraph: (String, Int) -> Unit
) {
    var sectionMenuExpanded by remember { mutableStateOf(false) }
    val currentSection = sections.getOrNull(selectedSectionIndex)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = IeltsSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
        ) {
            Box {
                val anchorText = currentSection?.shortLabel ?: activeSkill.label
                Surface(
                    onClick = { sectionMenuExpanded = true },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${activeSkill.label} $anchorText",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = IeltsIcons.ExpandMore,
                            contentDescription = "Choose section",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = sectionMenuExpanded,
                    onDismissRequest = { sectionMenuExpanded = false }
                ) {
                    sections.forEachIndexed { index, section ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = plainSectionLabel(section.shortLabel),
                                    fontWeight = if (index == selectedSectionIndex) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                sectionMenuExpanded = false
                                onSelectSection(index)
                            }
                        )
                    }
                    val otherSkill = if (activeSkill == QuizSkillMode.READING) {
                        QuizSkillMode.LISTENING
                    } else {
                        QuizSkillMode.READING
                    }
                    val otherAvailable = if (otherSkill == QuizSkillMode.LISTENING) hasListening else hasReading
                    if (otherAvailable) {
                        HorizontalDivider(color = NeutralBorder)
                        DropdownMenuItem(
                            text = { Text("Switch to ${otherSkill.label}") },
                            onClick = {
                                sectionMenuExpanded = false
                                onSelectSkill(otherSkill)
                            }
                        )
                    }
                }
            }

            if (paragraphs.isNotEmpty()) {
                paragraphs.forEachIndexed { index, paragraph ->
                    val selected = selectedParagraph == paragraph.label
                    Surface(
                        onClick = { onSelectParagraph(paragraph.label, index) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else NeutralBorder
                        )
                    ) {
                        Text(
                            text = paragraph.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            } else if (sections.size > 1) {
                sections.forEachIndexed { index, section ->
                    val selected = index == selectedSectionIndex
                    Surface(
                        onClick = { onSelectSection(index) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else NeutralBorder
                        )
                    ) {
                        Text(
                            text = section.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingPassageHero(
    passage: ReadingPassage,
    selectedParagraph: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectParagraph: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(
            start = IeltsSpacing.Md,
            top = IeltsSpacing.Md,
            end = IeltsSpacing.Md,
            bottom = 92.dp
        ),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
    ) {
        item(key = "passage_title") {
            Text(
                text = passage.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(items = passage.paragraphs, key = { it.label }) { paragraph ->
            val highlighted = paragraph.label == selectedParagraph
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (highlighted) BrandBlueSoft.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium
                    )
                    .clickable { onSelectParagraph(paragraph.label) }
                    .padding(vertical = IeltsSpacing.Sm, horizontal = IeltsSpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = paragraph.label,
                        modifier = Modifier.padding(horizontal = IeltsSpacing.Sm, vertical = IeltsSpacing.Xs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = paragraph.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 29.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuizQuestionsList(
    activeSkill: QuizSkillMode,
    currentSection: QuizSectionBundle,
    sectionQuestions: List<QuizQuestion>,
    allSkillQuestions: List<QuizQuestion>,
    sectionCorrectCount: Int,
    totalCorrectCount: Int,
    checked: Boolean,
    mcqAnswers: MutableMap<String, Set<Int>>,
    tfngAnswers: MutableMap<String, TfngOption>,
    ynngAnswers: MutableMap<String, YnngOption>,
    fillAnswers: MutableMap<String, String>,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    showSectionHeader: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sectionLabel = plainSectionLabel(currentSection.shortLabel)
    val displayGroups = remember(currentSection, sectionQuestions) {
        if (currentSection.groups.isNotEmpty()) {
            currentSection.groups
        } else if (sectionQuestions.isNotEmpty()) {
            listOf(
                QuizQuestionGroup(
                    id = "${currentSection.id}_default",
                    instructions = currentSection.instructions,
                    questions = sectionQuestions
                )
            )
        } else {
            emptyList()
        }
    }
    val groupImagePaths = remember(displayGroups) {
        displayGroups
            .mapNotNull { it.imageAsset?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
    }
    val groupImageStates = remember(currentSection.id) {
        mutableStateMapOf<String, AssetImageState>()
    }
    LaunchedEffect(context, currentSection.id, groupImagePaths) {
        groupImagePaths.forEach { assetPath ->
            if (groupImageStates[assetPath] !is AssetImageState.Ready) {
                groupImageStates[assetPath] = AssetImageState.Loading
                groupImageStates[assetPath] = decodeAssetImageState(context, assetPath)
            }
        }
    }
    var zoomedGroupImage by remember(currentSection.id) {
        mutableStateOf<ZoomedGroupImage?>(null)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(IeltsSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
    ) {
        if (showSectionHeader) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                    Text(
                        text = currentSection.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentSection.instructions.isNotBlank()) {
                        Text(
                            text = currentSection.instructions,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (checked) {
            item {
                val estimatedBand = IeltsScoring.bandFromRawScore(
                    correct = totalCorrectCount,
                    total = allSkillQuestions.size
                )
                val breakdown = computeTypeBreakdown(
                    questions = allSkillQuestions,
                    mcqAnswers = mcqAnswers,
                    tfngAnswers = tfngAnswers,
                    ynngAnswers = ynngAnswers,
                    fillAnswers = fillAnswers
                )
                ResultBanner(
                    sectionLabel = sectionLabel,
                    sectionCorrect = sectionCorrectCount,
                    sectionTotal = sectionQuestions.size,
                    totalCorrect = totalCorrectCount,
                    totalCount = allSkillQuestions.size,
                    estimatedBand = estimatedBand,
                    typeBreakdown = breakdown
                )
            }
        }

        displayGroups.forEach { group ->
            val normalizedImageAsset = group.imageAsset?.trim()?.takeIf(String::isNotEmpty)
            val showMissingPlaceholder = normalizedImageAsset == null && isMapOrPlanGroup(group)
            if (normalizedImageAsset != null || showMissingPlaceholder) {
                item(key = "${group.id}_visual") {
                    if (normalizedImageAsset != null) {
                        when (val imageState = groupImageStates[normalizedImageAsset] ?: AssetImageState.Loading) {
                            is AssetImageState.Ready -> {
                                AssetImageThumbnailCard(
                                    imageBitmap = imageState.imageBitmap,
                                    contentDescription = groupThumbnailContentDescription(group, sectionLabel),
                                    caption = groupImageCaption(group),
                                    onClick = {
                                        zoomedGroupImage = ZoomedGroupImage(
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
                            AssetImageState.Loading -> {
                                // Asset decode is in progress; avoid flashing the missing placeholder.
                            }
                        }
                    } else {
                        MissingAssetImagePlaceholder(
                            message = missingGroupImageMessage(group)
                        )
                    }
                }
            }

            items(items = group.questions, key = { it.id }) { question ->
                when (question) {
                    is QuizQuestion.Mcq -> McqQuestionCard(
                        question = question,
                        selectedIndices = mcqAnswers[question.id].orEmpty(),
                        checked = checked,
                        onToggleOption = { index ->
                            if (question.requiredSelections <= 1) {
                                mcqAnswers[question.id] = setOf(index)
                            } else {
                                val current = mcqAnswers[question.id].orEmpty().toMutableSet()
                                if (index in current) {
                                    current.remove(index)
                                } else {
                                    current.add(index)
                                }
                                mcqAnswers[question.id] = current
                            }
                        }
                    )

                    is QuizQuestion.TrueFalseNotGiven -> TfngQuestionCard(
                        question = question,
                        selectedOption = tfngAnswers[question.id],
                        checked = checked,
                        onSelect = { tfngAnswers[question.id] = it }
                    )

                    is QuizQuestion.YesNoNotGiven -> YnngQuestionCard(
                        question = question,
                        selectedOption = ynngAnswers[question.id],
                        checked = checked,
                        onSelect = { ynngAnswers[question.id] = it }
                    )

                    is QuizQuestion.FillBlank -> FillBlankQuestionCard(
                        question = question,
                        answerText = fillAnswers[question.id].orEmpty(),
                        checked = checked,
                        onValueChange = { fillAnswers[question.id] = it }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = IeltsSpacing.Xs, bottom = IeltsSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
            ) {
                // Word-count warnings never block quiz submission.
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .height(IeltsSpacing.TouchTarget)
                ) {
                    Text("Check Answers")
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.height(IeltsSpacing.TouchTarget)
                ) {
                    Text("Reset")
                }
            }
        }
    }

    val activeZoomedImage = zoomedGroupImage
    if (activeZoomedImage != null) {
        AssetImageZoomDialog(
            imageBitmap = activeZoomedImage.imageBitmap,
            title = activeZoomedImage.title,
            imageContentDescription = activeZoomedImage.contentDescription,
            closeContentDescription = "Close image preview",
            onDismiss = { zoomedGroupImage = null }
        )
    }
}

private data class ZoomedGroupImage(
    val imageBitmap: ImageBitmap,
    val title: String,
    val contentDescription: String
)

private val MapOrPlanInstructionPattern = Regex(
    pattern = "\\b(?:label|complete)\\s+the\\s+(?:floor\\s+)?(?:map|plan|diagram)\\b|\\b(?:floor\\s+)?(?:map|plan|diagram)\\s+below\\b",
    option = RegexOption.IGNORE_CASE
)
private val FloorPlanPattern = Regex("\\bfloor\\s+plan\\b", RegexOption.IGNORE_CASE)
private val MapWordPattern = Regex("\\bmap\\b", RegexOption.IGNORE_CASE)
private val PlanRubricPattern = Regex(
    pattern = "\\b(?:label|complete)\\s+the\\s+plan\\b|\\bplan\\s+below\\b",
    option = RegexOption.IGNORE_CASE
)
private val DiagramWordPattern = Regex("\\bdiagram\\b", RegexOption.IGNORE_CASE)

internal fun isMapOrPlanGroup(group: QuizQuestionGroup): Boolean {
    if (MapOrPlanInstructionPattern.containsMatchIn(group.instructions)) {
        return true
    }
    return group.questions.any { question ->
        MapOrPlanInstructionPattern.containsMatchIn(question.instructions)
    }
}

internal fun groupVisualKind(group: QuizQuestionGroup): String {
    val combinedInstructions = buildString {
        append(group.instructions)
        if (group.instructions.isBlank()) {
            group.questions.firstOrNull()?.instructions?.let { append(it) }
        }
    }
    return when {
        FloorPlanPattern.containsMatchIn(combinedInstructions) -> "Floor plan"
        MapWordPattern.containsMatchIn(combinedInstructions) -> "Map"
        PlanRubricPattern.containsMatchIn(combinedInstructions) -> "Plan"
        DiagramWordPattern.containsMatchIn(combinedInstructions) -> "Diagram"
        else -> "Image"
    }
}

internal fun groupQuestionRangeLabel(group: QuizQuestionGroup): String? {
    val numbers = group.questions.map { it.number }.sorted()
    if (numbers.isEmpty()) return null
    val first = numbers.first()
    val last = numbers.last()
    return if (first == last) "Question $first" else "Questions $first-$last"
}

internal fun groupImageCaption(group: QuizQuestionGroup): String {
    val kind = groupVisualKind(group)
    val range = groupQuestionRangeLabel(group)
    return if (range != null) "$kind for $range" else kind
}

internal fun groupThumbnailContentDescription(
    group: QuizQuestionGroup,
    sectionLabel: String
): String {
    val caption = groupImageCaption(group)
    return "$caption in $sectionLabel. Tap to zoom."
}

internal fun groupZoomDialogTitle(group: QuizQuestionGroup): String {
    val kind = groupVisualKind(group)
    val range = groupQuestionRangeLabel(group)
    return if (range != null) "$kind ($range)" else kind
}

internal fun groupZoomImageContentDescription(
    group: QuizQuestionGroup,
    sectionLabel: String
): String {
    val kindLower = groupVisualKind(group).lowercase(Locale.US)
    val range = groupQuestionRangeLabel(group)
    return if (range != null) {
        "Enlarged $kindLower for $range in $sectionLabel"
    } else {
        "Enlarged $kindLower in $sectionLabel"
    }
}

internal fun missingGroupImageMessage(group: QuizQuestionGroup): String {
    val kind = groupVisualKind(group)
    val range = groupQuestionRangeLabel(group)
    val subject = if (kind == "Image") "Image" else "$kind image"
    return if (range != null) {
        "$subject not available yet ($range)"
    } else {
        "$subject not available yet"
    }
}

private fun plainSectionLabel(shortLabel: String): String {
    return when {
        shortLabel.startsWith("S") && shortLabel.drop(1).all(Char::isDigit) ->
            "Section ${shortLabel.drop(1)}"
        shortLabel.startsWith("P") && shortLabel.drop(1).all(Char::isDigit) ->
            "Passage ${shortLabel.drop(1)}"
        else -> shortLabel
    }
}

@Composable
private fun LockedQuizPlaceholder(
    testLabel: String,
    skill: QuizSkillMode,
    ctaLabel: String,
    onCtaClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(IeltsSpacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = LockedSlateBg,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, NeutralBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(IeltsSpacing.Lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
            ) {
                Icon(
                    imageVector = IeltsIcons.EmptyNoSeeds,
                    contentDescription = "Locked",
                    tint = LockedSlate,
                    modifier = Modifier.size(IeltsSpacing.HeroIcon)
                )
                Text(
                    text = "$testLabel ${skill.label} not available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onCtaClick,
                    modifier = Modifier.height(IeltsSpacing.TouchTarget)
                ) {
                    Text(ctaLabel)
                }
            }
        }
    }
}

@Composable
private fun ResultBanner(
    sectionLabel: String,
    sectionCorrect: Int,
    sectionTotal: Int,
    totalCorrect: Int,
    totalCount: Int,
    estimatedBand: Double,
    typeBreakdown: Map<QuestionTypeCategory, Pair<Int, Int>>
) {
    var showBreakdown by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = SuccessGreenBg,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(IeltsSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.ItemGap)
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
                        contentDescription = "Quiz checked",
                        tint = SuccessGreen,
                        modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Band ${IeltsScoring.formatBand(estimatedBand)} ($totalCorrect/$totalCount)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            text = "$sectionLabel: $sectionCorrect/$sectionTotal",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen
                        )
                    }
                }
                if (typeBreakdown.isNotEmpty()) {
                    TextButton(onClick = { showBreakdown = !showBreakdown }) {
                        Text(
                            text = if (showBreakdown) "Hide" else "Breakdown",
                            color = SuccessGreen
                        )
                    }
                }
            }

            if (typeBreakdown.isNotEmpty()) {
                AnimatedVisibility(visible = showBreakdown) {
                    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                        typeBreakdown.forEach { (category, pair) ->
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

@Composable
private fun QuestionHeader(
    number: Int,
    typeLabel: String,
    typeIcon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
    ) {
        Icon(
            imageVector = typeIcon,
            contentDescription = typeLabel,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IeltsSpacing.FeatureIcon)
        )
        Text(
            text = "Question $number",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun McqQuestionCard(
    question: QuizQuestion.Mcq,
    selectedIndices: Set<Int>,
    checked: Boolean,
    onToggleOption: (Int) -> Unit
) {
    val isMulti = question.requiredSelections > 1
    val isCorrect = selectedIndices == question.correctIndices
    val overSelected = isMulti && selectedIndices.size > question.requiredSelections

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
            QuestionHeader(
                number = question.number,
                typeLabel = if (isMulti) "Choose ${question.requiredSelections}" else "Multiple Choice",
                typeIcon = IeltsIcons.QuizMcq
            )

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (isMulti) {
                Text(
                    text = "Selected ${selectedIndices.size} of ${question.requiredSelections}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overSelected) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                question.options.forEachIndexed { index, option ->
                    val label = question.labelForIndex(index)
                    val isSelected = index in selectedIndices
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleOption(index) }
                            .padding(vertical = IeltsSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isMulti) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleOption(index) }
                            )
                        } else {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onToggleOption(index) }
                            )
                        }
                        Spacer(modifier = Modifier.width(IeltsSpacing.Sm))
                        Text(
                            text = "$label. $option",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (checked) {
                val correctLabels = question.correctIndices
                    .sorted()
                    .joinToString(", ") { question.labelForIndex(it) }
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct ($correctLabels)"
                    } else {
                        "Answer: $correctLabels"
                    }
                )
            }
        }
    }
}

@Composable
private fun TfngQuestionCard(
    question: QuizQuestion.TrueFalseNotGiven,
    selectedOption: TfngOption?,
    checked: Boolean,
    onSelect: (TfngOption) -> Unit
) {
    val isCorrect = selectedOption == question.correctAnswer

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
            QuestionHeader(
                number = question.number,
                typeLabel = "True / False / Not Given",
                typeIcon = IeltsIcons.QuizTfng
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
            ) {
                TfngOption.entries.forEach { option ->
                    val selected = selectedOption == option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(option) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else NeutralBorder
                        )
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.padding(vertical = IeltsSpacing.ItemGap, horizontal = IeltsSpacing.Xs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (checked) {
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct (${question.correctAnswer.label})"
                    } else {
                        "Answer: ${question.correctAnswer.label}"
                    }
                )
            }
        }
    }
}

@Composable
private fun YnngQuestionCard(
    question: QuizQuestion.YesNoNotGiven,
    selectedOption: YnngOption?,
    checked: Boolean,
    onSelect: (YnngOption) -> Unit
) {
    val isCorrect = selectedOption == question.correctAnswer

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
            QuestionHeader(
                number = question.number,
                typeLabel = "Yes / No / Not Given",
                typeIcon = IeltsIcons.QuizYnng
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
            ) {
                YnngOption.entries.forEach { option ->
                    val selected = selectedOption == option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(option) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) BrandTeal else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) BrandTeal else NeutralBorder
                        )
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.padding(vertical = IeltsSpacing.ItemGap, horizontal = IeltsSpacing.Xs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (checked) {
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct (${question.correctAnswer.label})"
                    } else {
                        "Answer: ${question.correctAnswer.label}"
                    }
                )
            }
        }
    }
}

@Composable
private fun FillBlankQuestionCard(
    question: QuizQuestion.FillBlank,
    answerText: String,
    checked: Boolean,
    onValueChange: (String) -> Unit
) {
    val wordCount = IeltsScoring.countWords(answerText)
    val overWordLimit = wordCount > question.maxWords
    val isCorrect = isFillBlankCorrect(question, answerText)

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
            QuestionHeader(
                number = question.number,
                typeLabel = "Fill in the Blank",
                typeIcon = IeltsIcons.QuizFillBlank
            )

            Text(
                text = "Limit: ${question.wordLimitHint}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WarningAmber
            )

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = answerText,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Your answer") }
            )

            if (overWordLimit) {
                Surface(
                    color = WarningAmberBg,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(IeltsSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.Warning,
                            contentDescription = "Word limit warning",
                            tint = WarningAmber,
                            modifier = Modifier.size(IeltsSpacing.FeatureIcon)
                        )
                        Text(
                            text = "Word warning: $wordCount words (limit ${question.maxWords}). You can still submit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber
                        )
                    }
                }
            }

            if (checked) {
                AnswerFeedbackChip(
                    isCorrect = isCorrect,
                    message = if (isCorrect) {
                        "Correct (${question.acceptedAnswers.firstOrNull().orEmpty()})"
                    } else {
                        "Answer: ${question.rawAnswerKey.ifBlank { question.acceptedAnswers.firstOrNull().orEmpty() }}"
                    }
                )
            }
        }
    }
}

@Composable
private fun AnswerFeedbackChip(
    isCorrect: Boolean,
    message: String
) {
    Surface(
        color = if (isCorrect) SuccessGreenBg else ErrorRedBg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = IeltsSpacing.ItemGap, vertical = IeltsSpacing.Sm),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isCorrect) SuccessGreen else ErrorRed
        )
    }
}

private fun isFillBlankCorrect(
    question: QuizQuestion.FillBlank,
    answerText: String
): Boolean {
    val trimmed = answerText.trim()
    if (trimmed.isEmpty()) return false
    if (question.rawAnswerKey.isNotBlank() && AnswerNormalizer.matches(question.rawAnswerKey, trimmed)) {
        return true
    }
    return question.acceptedAnswers.any { accepted ->
        AnswerNormalizer.matches(accepted, trimmed) || accepted.equals(trimmed, ignoreCase = true)
    }
}

private fun isQuestionCorrect(
    question: QuizQuestion,
    mcqAnswers: Map<String, Set<Int>>,
    tfngAnswers: Map<String, TfngOption>,
    ynngAnswers: Map<String, YnngOption>,
    fillAnswers: Map<String, String>
): Boolean {
    return when (question) {
        is QuizQuestion.Mcq -> mcqAnswers[question.id].orEmpty() == question.correctIndices
        is QuizQuestion.TrueFalseNotGiven -> tfngAnswers[question.id] == question.correctAnswer
        is QuizQuestion.YesNoNotGiven -> ynngAnswers[question.id] == question.correctAnswer
        is QuizQuestion.FillBlank -> isFillBlankCorrect(question, fillAnswers[question.id].orEmpty())
    }
}

private fun computeTypeBreakdown(
    questions: List<QuizQuestion>,
    mcqAnswers: Map<String, Set<Int>>,
    tfngAnswers: Map<String, TfngOption>,
    ynngAnswers: Map<String, YnngOption>,
    fillAnswers: Map<String, String>
): Map<QuestionTypeCategory, Pair<Int, Int>> {
    val result = linkedMapOf<QuestionTypeCategory, Pair<Int, Int>>()
    QuestionTypeCategory.entries.forEach { category ->
        val matching = questions.filter { it.category == category }
        if (matching.isNotEmpty()) {
            val correct = matching.count { q ->
                isQuestionCorrect(q, mcqAnswers, tfngAnswers, ynngAnswers, fillAnswers)
            }
            result[category] = correct to matching.size
        }
    }
    return result
}
