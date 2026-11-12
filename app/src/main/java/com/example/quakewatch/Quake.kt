package com.example.quakewatch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Quake(
    val id: String,
    val mag: Double,
    val place: String,
    val time: Long,
    val url: String,
    val lat: Double,
    val lon: Double,
    var distanceKm: Float = -1f
)

fun formatTime(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ms))