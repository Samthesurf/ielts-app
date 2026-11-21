package com.samuel.ielts.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ThumbsUpDown
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth mapping app semantics to standard 24dp Material Extended icons.
 */
object IeltsIcons {
    // Core skills and top-level navigation
    val Listening: ImageVector get() = Icons.Rounded.Headphones
    val Reading: ImageVector get() = Icons.AutoMirrored.Rounded.MenuBook
    val Writing: ImageVector get() = Icons.Rounded.EditNote
    val WritingTask1: ImageVector get() = Icons.Rounded.BarChart
    val WritingTask2: ImageVector get() = Icons.AutoMirrored.Rounded.Article
    val Speaking: ImageVector get() = Icons.Rounded.Mic
    val SpeakingPart1: ImageVector get() = Icons.Rounded.Forum
    val SpeakingPart2: ImageVector get() = Icons.Rounded.RecordVoiceOver
    val SpeakingPart3: ImageVector get() = Icons.Rounded.QuestionAnswer
    val Library: ImageVector get() = Icons.Rounded.LocalLibrary
    val Quiz: ImageVector get() = Icons.Rounded.Quiz
    val Progress: ImageVector get() = Icons.AutoMirrored.Rounded.TrendingUp
    val Settings: ImageVector get() = Icons.Rounded.Settings

    // Quiz question types
    val QuizMcq: ImageVector get() = Icons.Rounded.RadioButtonChecked
    val QuizTfng: ImageVector get() = Icons.AutoMirrored.Rounded.Rule
    val QuizYnng: ImageVector get() = Icons.Rounded.ThumbsUpDown
    val QuizFillBlank: ImageVector get() = Icons.Rounded.TextFields
    val QuizMatching: ImageVector get() = Icons.Rounded.SwapHoriz
    val QuizMapLabel: ImageVector get() = Icons.Rounded.Map

    // Streaks and achievements
    val StreakFlame: ImageVector get() = Icons.Rounded.LocalFireDepartment
    val TrophyBestBand: ImageVector get() = Icons.Rounded.EmojiEvents

    // Settings and connectivity
    val ShowKey: ImageVector get() = Icons.Rounded.Visibility
    val HideKey: ImageVector get() = Icons.Rounded.VisibilityOff
    val TestConnectionCheck: ImageVector get() = Icons.Rounded.CloudDone
    val OfflineWarning: ImageVector get() = Icons.Rounded.AirplanemodeActive

    // Empty states and errors
    val EmptyNoAttempts: ImageVector get() = Icons.Rounded.HourglassEmpty
    val EmptyNoSeeds: ImageVector get() = Icons.Rounded.Lock
    val ErrorRetry: ImageVector get() = Icons.Rounded.Refresh
    val Warning: ImageVector get() = Icons.Rounded.WarningAmber

    // Navigation, controls, and study tools
    val NextStepArrow: ImageVector get() = Icons.AutoMirrored.Rounded.ArrowForward
    val Search: ImageVector get() = Icons.Rounded.Search
    val Timer: ImageVector get() = Icons.Rounded.Timer
    val PlayAudio: ImageVector get() = Icons.Rounded.PlayArrow
    val Completed: ImageVector get() = Icons.Rounded.CheckCircle
    val Close: ImageVector get() = Icons.Rounded.Close
    val ExpandMore: ImageVector get() = Icons.Rounded.KeyboardArrowDown
    val ExpandLess: ImageVector get() = Icons.Rounded.KeyboardArrowUp
}
