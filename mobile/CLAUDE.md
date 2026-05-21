\# Adaptify Mobile — Music Player Integration



\## Current State

App sudah bisa terima data dari watch (HR, steps, stress, activityMode, genre).

UI sudah display semua data realtime.

ActivityClassifier sudah map mode → genre recommendation.



\## What's Missing (Fase 3)

1\. Music input: user pilih MP3 files dari storage → assign ke genre

2\. MediaPlayer: auto-play musik sesuai genre yang direkomendasikan

3\. Background playback dengan MediaSession

4\. UI controls: play/pause, skip, volume



\## Tech Stack

\- Current: ViewBinding, WearableListenerService, SharedPreferences

\- Need to add: MediaPlayer / ExoPlayer, MediaSession, SAF (Storage Access Framework)



\## Genre Mapping (from ActivityClassifier.kt)

\- "Workout Mode" → "EDM / Upbeat"

\- "High Stress Mode" → "Lo-fi / Calming"

\- "Active Mode" → "Pop / Energetic"

\- "Elevated Stress Mode" → "Ambient / Soft"

\- "Relax Mode" → "Jazz / Acoustic"

\- "Rest Mode" → "Classical / Sleep"



\## Data Flow

Watch → DataLayer → AdaptifyMobileReceiver → MainActivity (display) + MusicPlayer (auto-play)

