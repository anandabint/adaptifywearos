package com.adaptify.mobile

/**
 * Phone-side classifier reference. Mirrors the watch ActivityClassifier (3 modes).
 *
 * Genre mapping — 1 genre per mode:
 *   HIGH_ACTIVITY → "Exercise Music"
 *   LOW_ACTIVITY  → "Focus Music"
 *   RELAX         → "Relax Music"
 *
 * Thresholds — peer-reviewed, Tudor-Locke et al. (2011), Int J Behav Nutr Phys Act:
 *  - steps >= 100/min → MVPA (HIGH_ACTIVITY)
 *  - steps 30-99/min  → light activity (LOW_ACTIVITY)
 *  - steps < 30/min   → sedentary (RELAX)
 */
object ActivityClassifier {

    // Genre names — must match exactly with MusicPickerActivity.GENRE_SECTIONS
    const val GENRE_HIGH_ACTIVITY = "Exercise Music"
    const val GENRE_LOW_ACTIVITY  = "Focus Music"
    const val GENRE_RELAX         = "Relax Music"

    fun getMusicGenre(mode: String): String = when (mode) {
        "HIGH_ACTIVITY" -> GENRE_HIGH_ACTIVITY
        "LOW_ACTIVITY"  -> GENRE_LOW_ACTIVITY
        else            -> GENRE_RELAX
    }
}
