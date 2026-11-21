package com.samuel.ielts.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Flat row for the quiz screen: question plus its group context. */
data class QuizRow(
    val questionId: Long,
    val qNo: Int,
    val promptText: String,
    val imageName: String?,
    val choicesJson: String?,
    val correctAnswerRaw: String,
    val instructions: String,
    val wordLimit: String
)

/** Attempt plus question number for history and progress screens. */
data class HistoryRow(
    val attemptId: Long,
    val questionId: Long,
    val qNo: Int,
    val userAnswer: String,
    val correct: Boolean,
    val aiScoresJson: String?,
    val createdAt: Long
)

@Dao
interface IeltsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTest(test: Test): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPassage(passage: Passage): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: QuestionGroup): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestion(question: Question): Long

    @Insert
    suspend fun saveAttempt(attempt: Attempt): Long

    /** All questions for one book test section, ordered by question number. */
    @Query(
        """SELECT q.id AS questionId, q.qNo AS qNo, q.promptText AS promptText,
          q.imageName AS imageName, q.choicesJson AS choicesJson,
          q.correctAnswerRaw AS correctAnswerRaw, g.instructions AS instructions,
          g.wordLimit AS wordLimit
        FROM questions q
        JOIN question_groups g ON g.id = q.groupId
        JOIN tests t ON t.id = g.testId
        WHERE t.book = :book AND t.testNo = :testNo AND g.section = :section
        ORDER BY q.qNo"""
    )
    suspend fun getTestQuestions(book: String, testNo: Int, section: String): List<QuizRow>

    /** Recent attempts, newest first. */
    @Query(
        """SELECT a.id AS attemptId, a.questionId AS questionId, q.qNo AS qNo,
          a.userAnswer AS userAnswer, a.correct AS correct,
          a.aiScoresJson AS aiScoresJson, a.createdAt AS createdAt
        FROM attempts a
        JOIN questions q ON q.id = a.questionId
        ORDER BY a.createdAt DESC LIMIT :limit"""
    )
    suspend fun getHistory(limit: Int = 100): List<HistoryRow>
}
