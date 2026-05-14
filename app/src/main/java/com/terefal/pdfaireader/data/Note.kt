package com.terefal.pdfaireader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = NoteBook::class,
        parentColumns = ["id"],
        childColumns = ["noteBookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteBookId")]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteBookId: Long = 0,
    val pdfUri: String = "",
    val pageNumber: Int = 0,
    val question: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
