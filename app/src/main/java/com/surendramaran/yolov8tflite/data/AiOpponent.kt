package com.surendramaran.yolov8tflite.data

data class AiOpponent(
    val id: Int,
    val name: String,
    val title: String,
    val difficultyStars: Int,
    val isPremium: Boolean,
    val averageScore: Double, // We use this for math, but we won't show it in UI
    val avatarRes: Int? = null
)
