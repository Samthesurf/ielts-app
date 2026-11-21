package com.samuel.ielts.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Test::class, Passage::class, QuestionGroup::class, Question::class, Attempt::class],
    version = 1,
    exportSchema = false
)
abstract class IeltsDb : RoomDatabase() {
    abstract fun dao(): IeltsDao

    companion object {
        @Volatile
        private var instance: IeltsDb? = null

        fun get(context: Context): IeltsDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    IeltsDb::class.java,
                    "ielts.db"
                ).build().also { instance = it }
            }
    }
}
