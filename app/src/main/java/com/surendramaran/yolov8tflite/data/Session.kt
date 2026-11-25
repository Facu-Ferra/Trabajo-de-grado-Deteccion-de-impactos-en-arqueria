package com.surendramaran.yolov8tflite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val targetType: String,
    val expectedArrows: Int,
    var totalScore: Int = 0,
    
    // Duel Mode fields
    var isDuel: Boolean = false,
    var aiOpponentId: Int? = null,
    var aiTotalScore: Int = 0
)
