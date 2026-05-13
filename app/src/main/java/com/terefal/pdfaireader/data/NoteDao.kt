package com.terefal.pdfaireader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE pdfUri = :pdfUri ORDER BY timestamp DESC")
    fun getNotesForPdf(pdfUri: String): Flow<List<Note>>

    @Insert
    suspend fun insert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)
}
