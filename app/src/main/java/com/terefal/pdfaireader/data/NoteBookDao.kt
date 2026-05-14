package com.terefal.pdfaireader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteBookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun getAllNoteBooks(): Flow<List<NoteBook>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNoteBookById(id: Long): NoteBook?

    @Insert
    suspend fun insert(notebook: NoteBook): Long

    @Update
    suspend fun update(notebook: NoteBook)

    @Delete
    suspend fun delete(notebook: NoteBook)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE notebooks SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())
}
