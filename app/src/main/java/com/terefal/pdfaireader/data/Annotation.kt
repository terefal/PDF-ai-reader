package com.terefal.pdfaireader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AnnotationType { HIGHLIGHT, UNDERLINE, NOTE }

@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = NoteBook::class,
        parentColumns = ["id"],
        childColumns = ["noteBookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteBookId")]
)
data class Annotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteBookId: Long = 0,
    val pdfUri: String = "",
    val pageNumber: Int,
    val rectLeft: Float,
    val rectTop: Float,
    val rectRight: Float,
    val rectBottom: Float,
    val color: Int = 0x40FFEB3B.toInt(),
    val type: AnnotationType = AnnotationType.HIGHLIGHT,
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
