package com.terefal.pdfaireader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notebooks")
data class NoteBook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val pdfUri: String? = null,
    val pdfFileName: String? = null,
    val previewText: String? = null,
    val pageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
