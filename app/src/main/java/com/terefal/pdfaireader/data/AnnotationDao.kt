package com.terefal.pdfaireader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE pdfUri = :pdfUri ORDER BY pageNumber, rectTop")
    fun getAnnotationsForPdf(pdfUri: String): Flow<List<Annotation>>

    @Query("SELECT * FROM annotations WHERE pdfUri = :pdfUri AND pageNumber = :pageNumber ORDER BY rectTop")
    fun getAnnotationsForPage(pdfUri: String, pageNumber: Int): Flow<List<Annotation>>

    @Insert
    suspend fun insert(annotation: Annotation): Long

    @Update
    suspend fun update(annotation: Annotation)

    @Delete
    suspend fun delete(annotation: Annotation)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE annotations SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("SELECT * FROM annotations WHERE noteBookId = :noteBookId ORDER BY pageNumber, rectTop")
    fun getAnnotationsForNoteBook(noteBookId: Long): Flow<List<Annotation>>

    @Query("SELECT * FROM annotations WHERE noteBookId = :noteBookId AND pageNumber = :pageNumber ORDER BY rectTop")
    fun getAnnotationsForNoteBookPage(noteBookId: Long, pageNumber: Int): Flow<List<Annotation>>
}
