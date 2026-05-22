package com.adaptify.mobile

object ActivityClassifier {

    fun classify(heartRate: Int, stressIndex: Int): String {
        return when {
            heartRate >= 100 -> "Workout Mode"
            heartRate in 80..99 && stressIndex >= 60 -> "High Stress Mode"
            heartRate in 80..99 -> "Active Mode"
            heartRate in 60..79 && stressIndex >= 50 -> "Elevated Stress Mode"
            heartRate in 60..79 -> "Relax Mode"
            else -> "Rest Mode"
        }
    }

    fun getMusicGenre(mode: String): String {
        return when (mode) {
            "Workout Mode" -> "EDM / Upbeat"
            "High Stress Mode" -> "Lo-fi / Calming"
            "Active Mode" -> "Pop / Energetic"
            "Elevated Stress Mode" -> "Ambient / Soft"
            "Relax Mode" -> "Jazz / Acoustic"
            "Rest Mode" -> "Classical / Sleep"
            else -> "Ambient / Soft"
        }
    }
}
