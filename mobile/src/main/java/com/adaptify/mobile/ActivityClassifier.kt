package com.adaptify.mobile

/**
 * Phone-side classifier. Mirrors the watch ActivityClassifier (3 modes).
 *
 * Genre mapping — 1 genre per mode (simplified from 6 to 3):
 *   EXERCISE → "Exercise Music"
 *   STRESS   → "Stress Relief"
 *   RELAX    → "Relax Music"
 *
 * Thresholds — peer-reviewed:
 *  - HR ≥100 BPM → MVPA exercise (Ho et al., 2022)
 *  - RMSSD <20ms → sympathetic dominance / stress (Chalmers et al., 2022)
 *  - RMSSD ≥20ms → parasympathetic / relaxed (Shaffer & Ginsberg, 2017)
 */
object ActivityClassifier {

    private const val HR_EXERCISE_MIN = 100
    private const val HR_STRESS_LOW = 60
    private const val HR_STRESS_HIGH = 99
    private const val RMSSD_STRESS_MAX_MS = 20.0

    // Genre names — must match exactly with MusicPickerActivity.GENRE_SECTIONS
    const val GENRE_EXERCISE = "Exercise Music"
    const val GENRE_STRESS   = "Stress Relief"
    const val GENRE_RELAX    = "Relax Music"

    // Note: fungsi ini tidak lagi digunakan sebagai fallback di AdaptifyMobileReceiver.
    // Mode klasifikasi utama dikirim langsung dari watch (ActivityClassifier.kt Wear OS).
    // Fungsi ini dipertahankan sebagai referensi threshold saja.
    fun classify(heartRate: Int, rmssd: Double): String = when {
        heartRate >= HR_EXERCISE_MIN -> "EXERCISE"
        heartRate in HR_STRESS_LOW..HR_STRESS_HIGH
            && rmssd > 0.0
            && rmssd < RMSSD_STRESS_MAX_MS -> "STRESS"
        else -> "RELAX"
    }

    fun getMusicGenre(mode: String): String = when (mode) {
        "EXERCISE" -> GENRE_EXERCISE
        "STRESS"   -> GENRE_STRESS
        else       -> GENRE_RELAX
    }
}
