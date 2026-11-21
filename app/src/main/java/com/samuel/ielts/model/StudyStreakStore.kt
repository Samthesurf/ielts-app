package com.samuel.ielts.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

private val Context.studyProgressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ielts_study_progress_prefs"
)

data class StudyProgressSnapshot(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val lastStudyEpochDay: Long = 0L,
    val bestOverallBand: Double = 0.0,
    val studiedToday: Boolean = false
)

data class StudyRecordOutcome(
    val snapshot: StudyProgressSnapshot,
    val isNewBestBand: Boolean,
    val previousBestBand: Double
)

object StudyStreakCalculator {
    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    fun effectiveSnapshot(
        storedCurrentStreak: Int,
        storedLongestStreak: Int,
        lastStudyEpochDay: Long,
        bestOverallBand: Double,
        todayEpochDay: Long = todayEpochDay()
    ): StudyProgressSnapshot {
        val studiedToday = lastStudyEpochDay == todayEpochDay && storedCurrentStreak > 0
        val effectiveStreak = when {
            lastStudyEpochDay <= 0L -> 0
            lastStudyEpochDay == todayEpochDay -> storedCurrentStreak.coerceAtLeast(1)
            lastStudyEpochDay == todayEpochDay - 1L -> storedCurrentStreak.coerceAtLeast(1)
            else -> 0
        }
        return StudyProgressSnapshot(
            currentStreakDays = effectiveStreak,
            longestStreakDays = maxOf(storedLongestStreak, effectiveStreak),
            lastStudyEpochDay = lastStudyEpochDay,
            bestOverallBand = bestOverallBand.coerceIn(0.0, 9.0),
            studiedToday = studiedToday
        )
    }

    fun recordStudyAndBand(
        storedCurrentStreak: Int,
        storedLongestStreak: Int,
        lastStudyEpochDay: Long,
        storedBestOverallBand: Double,
        candidateOverallBand: Double,
        todayEpochDay: Long = todayEpochDay()
    ): StudyRecordOutcome {
        val nextStreak = when {
            lastStudyEpochDay == todayEpochDay -> storedCurrentStreak.coerceAtLeast(1)
            lastStudyEpochDay == todayEpochDay - 1L -> storedCurrentStreak.coerceAtLeast(0) + 1
            else -> 1
        }
        val nextLongest = maxOf(storedLongestStreak, nextStreak)
        val roundedCandidate = if (candidateOverallBand > 0.0) {
            IeltsScoring.roundToHalfBand(candidateOverallBand)
        } else {
            0.0
        }
        val previousBest = storedBestOverallBand.coerceIn(0.0, 9.0)
        val isNewBest = roundedCandidate > 0.0 && roundedCandidate > previousBest
        val nextBest = if (isNewBest) roundedCandidate else previousBest

        return StudyRecordOutcome(
            snapshot = StudyProgressSnapshot(
                currentStreakDays = nextStreak,
                longestStreakDays = nextLongest,
                lastStudyEpochDay = todayEpochDay,
                bestOverallBand = nextBest,
                studiedToday = true
            ),
            isNewBestBand = isNewBest,
            previousBestBand = previousBest
        )
    }
}

object StudyStreakStore {
    private val KEY_CURRENT_STREAK = intPreferencesKey("current_streak_days")
    private val KEY_LONGEST_STREAK = intPreferencesKey("longest_streak_days")
    private val KEY_LAST_STUDY_DAY = longPreferencesKey("last_study_epoch_day")
    private val KEY_BEST_OVERALL_BAND = doublePreferencesKey("best_overall_band")

    fun observeSnapshot(context: Context): Flow<StudyProgressSnapshot> {
        return context.applicationContext.studyProgressDataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { prefs ->
                StudyStreakCalculator.effectiveSnapshot(
                    storedCurrentStreak = prefs[KEY_CURRENT_STREAK] ?: 0,
                    storedLongestStreak = prefs[KEY_LONGEST_STREAK] ?: 0,
                    lastStudyEpochDay = prefs[KEY_LAST_STUDY_DAY] ?: 0L,
                    bestOverallBand = prefs[KEY_BEST_OVERALL_BAND] ?: 0.0
                )
            }
    }

    suspend fun recordStudyActivity(
        context: Context,
        candidateOverallBand: Double
    ): StudyRecordOutcome {
        var outcome = StudyRecordOutcome(
            snapshot = StudyProgressSnapshot(),
            isNewBestBand = false,
            previousBestBand = 0.0
        )
        context.applicationContext.studyProgressDataStore.edit { prefs ->
            val computed = StudyStreakCalculator.recordStudyAndBand(
                storedCurrentStreak = prefs[KEY_CURRENT_STREAK] ?: 0,
                storedLongestStreak = prefs[KEY_LONGEST_STREAK] ?: 0,
                lastStudyEpochDay = prefs[KEY_LAST_STUDY_DAY] ?: 0L,
                storedBestOverallBand = prefs[KEY_BEST_OVERALL_BAND] ?: 0.0,
                candidateOverallBand = candidateOverallBand
            )
            prefs[KEY_CURRENT_STREAK] = computed.snapshot.currentStreakDays
            prefs[KEY_LONGEST_STREAK] = computed.snapshot.longestStreakDays
            prefs[KEY_LAST_STUDY_DAY] = computed.snapshot.lastStudyEpochDay
            prefs[KEY_BEST_OVERALL_BAND] = computed.snapshot.bestOverallBand
            outcome = computed
        }
        return outcome
    }
}
