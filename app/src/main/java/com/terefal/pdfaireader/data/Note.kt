package com.terefal.pdfaireader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfUri: String,
    val pageNumber: Int = 0,
    val question: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
