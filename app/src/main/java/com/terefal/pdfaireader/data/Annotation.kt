package com.terefal.pdfaireader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class Annotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfUri: String,
    val pageNumber: Int,
    val rectLeft: Float,
    val rectTop: Float,
    val rectRight: Float,
    val rectBottom: Float,
    val color: Int = 0x40FFEB3B.toInt(),  // default yellow highlight
    val type: String = "highlight",       // "highlight" | "underline" | "note"
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
