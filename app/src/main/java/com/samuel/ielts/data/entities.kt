package com.samuel.ielts.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One book test, e.g. book "C20", testNo 1. Holds Listening + Reading sections. */
@Entity(
    tableName = "tests",
    indices = [Index(value = ["book", "testNo"], unique = true)]
)
data class Test(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: String,
    val testNo: Int
)

/**
 * A reading passage or a listening section block.
 * paragraphsJson holds a JSON array of paragraph strings, with labels
 * like "A-G" embedded as separate entries where the test uses them.
 */
@Entity(
    tableName = "passages",
    foreignKeys = [ForeignKey(
        entity = Test::class,
        parentColumns = ["id"],
        childColumns = ["testId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("testId")]
)
data class Passage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testId: Long,
    val section: String,
    val title: String,
    val paragraphsJson: String
)

/**
 * A group of questions sharing instructions, e.g. "Questions 1-6"
 * with a word limit rule. passageId is null for standalone listening groups.
 */
@Entity(
    tableName = "question_groups",
    foreignKeys = [ForeignKey(
        entity = Test::class,
        parentColumns = ["id"],
        childColumns = ["testId"],
        onDelete = ForeignKey.CASCADE
    ), ForeignKey(
        entity = Passage::class,
        parentColumns = ["id"],
        childColumns = ["passageId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("testId"), Index("passageId")]
)
data class QuestionGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testId: Long,
    val passageId: Long?,
    val section: String,
    val instructions: String,
    val wordLimit: String
)

/**
 * One question. choicesJson is a JSON array of option strings for
 * multiple choice / matching, else null. imageName names a bundled
 * asset (charts), else null. correctAnswerRaw is the verbatim key text.
 */
@Entity(
    tableName = "questions",
    foreignKeys = [ForeignKey(
        entity = QuestionGroup::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("groupId"), Index(value = ["groupId", "qNo"], unique = true)]
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val qNo: Int,
    val promptText: String,
    val imageName: String?,
    val choicesJson: String?,
    val correctAnswerRaw: String
)

/** One student answer. aiScoresJson holds the marker JSON for writing/speaking. */
@Entity(
    tableName = "attempts",
    foreignKeys = [ForeignKey(
        entity = Question::class,
        parentColumns = ["id"],
        childColumns = ["questionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("questionId"), Index("createdAt")]
)
data class Attempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val userAnswer: String,
    val correct: Boolean,
    val aiScoresJson: String?,
    val createdAt: Long
)
