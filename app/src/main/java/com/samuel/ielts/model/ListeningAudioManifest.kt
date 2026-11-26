package com.samuel.ielts.model

import android.content.Context

/**
 * Bundled full-test listening audio metadata for offline exam playback.
 */
data class ListeningAudioTrack(
    val book: IeltsBook,
    val testNumber: Int,
    val assetPath: String,
    val durationSeconds: Int,
    val transferTimeSeconds: Int = ListeningAudioManifest.TRANSFER_TIME_SECONDS
) {
    val assetUri: String
        get() = "asset:///$assetPath"

    val totalExamDurationSeconds: Int
        get() = durationSeconds + transferTimeSeconds
}

/**
 * Explicit asset manifest mapping full-test listening tracks to bundled assets.
 * Only C21 Test 1 and Test 2 have full-test audio; all other tests stay in section-drill mode.
 */
object ListeningAudioManifest {
    const val TRANSFER_TIME_SECONDS = 600
    const val SECTION_CHECK_PAUSE_SECONDS = 30
    const val RED_WARNING_THRESHOLD_SECONDS = 300

    private val tracksByKey: Map<Pair<IeltsBook, Int>, ListeningAudioTrack> = mapOf(
        (IeltsBook.C21 to 1) to ListeningAudioTrack(
            book = IeltsBook.C21,
            testNumber = 1,
            assetPath = "audio/c21_t1.mp3",
            durationSeconds = 1856
        ),
        (IeltsBook.C21 to 2) to ListeningAudioTrack(
            book = IeltsBook.C21,
            testNumber = 2,
            assetPath = "audio/c21_t2.mp3",
            durationSeconds = 1761
        )
    )

    val allTracks: List<ListeningAudioTrack>
        get() = tracksByKey.values.toList()

    fun findTrack(book: IeltsBook, testNumber: Int): ListeningAudioTrack? {
        return tracksByKey[book to testNumber]
    }

    /**
     * Returns the manifest entry only when the bundled audio asset exists in APK assets.
     * Falls back to null so callers can route cleanly to section drill without crashing.
     */
    fun findAvailableTrack(
        context: Context,
        book: IeltsBook,
        testNumber: Int
    ): ListeningAudioTrack? {
        val track = findTrack(book, testNumber) ?: return null
        return if (assetExists(context, track.assetPath)) track else null
    }

    fun assetExists(context: Context, assetPath: String): Boolean {
        return runCatching {
            context.assets.open(assetPath).use { true }
        }.getOrDefault(false)
    }
}
