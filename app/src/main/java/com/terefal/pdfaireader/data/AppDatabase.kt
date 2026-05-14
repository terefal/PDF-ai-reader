package com.terefal.pdfaireader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class, Annotation::class, NoteBook::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun noteBookDao(): NoteBookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notebooks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        pdfUri TEXT,
                        pdfFileName TEXT,
                        previewText TEXT,
                        pageCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("ALTER TABLE notes ADD COLUMN noteBookId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE annotations ADD COLUMN noteBookId INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_ai_reader_db"
                ).addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
