\\# Adaptify Wear OS — Watch App







\\## Project Overview



Android Wear OS application untuk Xiaomi Wear OS Watch 2. Membaca sensor realtime (Heart Rate, Step Count, HRV/RMSSD, Accelerometer, Gyroscope) dan mengirim data ke aplikasi HP via Wearable Data Layer.







\\\*\\\*Status:\\\*\\\* Fase 1 SELESAI — Background service sudah berjalan sempurna.



\\\*\\\*Fase saat ini:\\\*\\\* Fase 2 — Implementasi ActivityClassifier berbasis threshold jurnal.







\\---







\\## Tech Stack



\\- \\\*\\\*Language:\\\*\\\* Kotlin



\\- \\\*\\\*UI:\\\*\\\* Jetpack Compose for Wear OS (Material 3)



\\- \\\*\\\*Sensor:\\\*\\\* Health Services Client (HR), Android Sensor API (Accel/Gyro/Step)



\\- \\\*\\\*Background:\\\*\\\* Foreground Service + WakeLock



\\- \\\*\\\*Data Transfer:\\\*\\\* Wearable Data Layer (Google Play Services)



\\- \\\*\\\*Build:\\\*\\\* Gradle 8.x, minSdk 30, targetSdk 36







\\---







\\## Project Structure







```



app/src/main/java/com/adaptify/adaptifywearos/



├── presentation/



│   ├── MainActivity.kt                 # UI utama, toggle monitoring



│   ├── AdaptifyMonitorService.kt      # Foreground service untuk background



│   ├── AdaptifyRealtimeSender.kt      # Kirim data ke HP via DataLayer



│   └── AdaptifyRealtimePayload.kt     # Data model untuk transfer



├── health/



│   └── HeartRateManager.kt            # Heart rate via Health Services



├── sensor/



│   └── SensorHandler.kt               # Accel, Gyro, Step counter



└── stress/



\&#x20;   └── StressCalculator.kt            # RMSSD calculation (HRV)



```







\\---







\\## Build Commands







```bash



\\# Build debug APK



./gradlew assembleDebug







\\# Install ke watch via ADB



adb -s <WATCH\\\_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk







\\# Run tests



./gradlew test







\\# Clean build



./gradlew clean



```







\\---







\\## Key Classes Explained







\\### 1. AdaptifyMonitorService.kt



\\\*\\\*Tujuan:\\\*\\\* Background monitoring dengan Foreground Service.







\\\*\\\*Fitur:\\\*\\\*



\\- Start/stop monitoring via Intent (`ACTION\\\_TOGGLE`)



\\- WakeLock untuk prevent sleep



\\- Activity lifecycle tracking (auto-pause saat UI aktif)



\\- Notification ongoing untuk Wear OS



\\- Combine sensor + HR data → kirim ke HP







\\\*\\\*Penting:\\\*\\\* Service ini SUDAH BERFUNGSI DI BACKGROUND. Jangan diubah tanpa alasan kuat.







\\### 2. StressCalculator.kt



\\\*\\\*Tujuan:\\\*\\\* Menghitung RMSSD (Root Mean Square of Successive Differences) dari RR intervals.







\\\*\\\*Metode ilmiah:\\\*\\\*



\\- RR interval = 60000 / HR (ms)



\\- RMSSD = sqrt(mean(successive\\\_differences^2))



\\- Baseline HR tracking (15 samples warmup)



\\- Smoothing via exponential moving average







\\\*\\\*Output saat ini:\\\*\\\* Stress Index (0-100) dengan level (Calm/Moderate/Elevated/High).







\\\*\\\*CATATAN:\\\*\\\* Ini adalah implementasi RMSSD yang valid secara ilmiah, TIDAK PERLU DIUBAH untuk fase 2. Kita hanya perlu membuat \\\*\\\*ActivityClassifier\\\*\\\* yang menggunakan `StressReading.rmssd` sebagai input.







\\### 3. SensorHandler.kt



\\\*\\\*Tujuan:\\\*\\\* Read accelerometer, gyroscope, step counter.







\\\*\\\*Output:\\\*\\\* `SensorSnapshot` dengan semua data sensor.







\\### 4. HeartRateManager.kt



\\\*\\\*Tujuan:\\\*\\\* Read heart rate via Health Services Client.







\\\*\\\*Output:\\\*\\\* `HeartRateState` dengan BPM + timestamp.







\\### 5. AdaptifyRealtimeSender.kt



\\\*\\\*Tujuan:\\\*\\\* Kirim data ke HP via `PutDataMapRequest`.







\\\*\\\*Path:\\\*\\\* `/adaptify/realtime`







\\\*\\\*Keys:\\\*\\\*



\\- `heartRate` (Int)



\\- `steps` (Int)



\\- `stressIndex` (Int)



\\- `rmssd` (Double)



\\- `updatedAt` (Long)







\\---







\\## Permissions (AndroidManifest.xml)







```xml



<!-- Sensor -->



<uses-permission android:name="android.permission.BODY\\\_SENSORS" />



<uses-permission android:name="android.permission.ACTIVITY\\\_RECOGNITION" />



<uses-permission android:name="android.permission.HIGH\\\_SAMPLING\\\_RATE\\\_SENSORS" />







<!-- Background -->



<uses-permission android:name="android.permission.BODY\\\_SENSORS\\\_BACKGROUND" />



<uses-permission android:name="android.permission.FOREGROUND\\\_SERVICE" />



<uses-permission android:name="android.permission.FOREGROUND\\\_SERVICE\\\_HEALTH" />



<uses-permission android:name="android.permission.WAKE\\\_LOCK" />







<!-- Notification -->



<uses-permission android:name="android.permission.POST\\\_NOTIFICATIONS" />







<!-- Health Connect (API 36+) -->



<uses-permission android:name="android.permission.health.READ\\\_HEART\\\_RATE" />



```







\\---







\\## Code Style \\\& Conventions







\\- \\\*\\\*Naming:\\\*\\\* camelCase untuk variables/functions, PascalCase untuk classes



\\- \\\*\\\*Kotlin idioms:\\\*\\\* Gunakan `?.let`, `?:`, data classes, sealed classes



\\- \\\*\\\*Coroutines:\\\*\\\* Semua async operation pakai coroutines (`suspend fun`, `launch`, `async`)



\\- \\\*\\\*Flow:\\\*\\\* Gunakan `StateFlow` untuk UI state, `SharedFlow` untuk events



\\- \\\*\\\*Compose:\\\*\\\* Stateless composables, hoist state ke ViewModel/Activity level



\\- \\\*\\\*Logging:\\\*\\\* `Log.d("Adaptify<Component>", "message")` untuk debug







\\---







\\## Testing Strategy







\\- \\\*\\\*Unit tests:\\\*\\\* Logic di StressCalculator, data transformations



\\- \\\*\\\*Instrumented tests:\\\*\\\* Sensor reading, Service lifecycle



\\- \\\*\\\*Manual testing:\\\*\\\* Install ke watch, test background behavior







\\---







\\## Known Issues \\\& Limitations







1\\. \\\*\\\*Xiaomi Doze Mode:\\\*\\\* Watch mungkin kill service setelah 1-2 jam. Solusi: user harus whitelist app dari battery optimization.



2\\. \\\*\\\*RMSSD accuracy:\\\*\\\* Perlu baseline 15 samples (\\\~30 detik) sebelum stabil.



3\\. \\\*\\\*Step counter:\\\*\\\* Reset saat watch reboot (sesuai Android SensorManager behavior).



4\\. \\\*\\\*Wearable Data Layer:\\\*\\\* Perlu Google Play Services di watch DAN HP.







\\---







\\## Next Steps (Fase 2)







\\\*\\\*Tujuan:\\\*\\\* Tambah `ActivityClassifier.kt` yang output 3 mode (Olahraga / Stres / Relax).







\\\*\\\*Input classifier:\\\*\\\*



\\- Heart Rate (BPM)



\\- Step Count (steps/min)



\\- RMSSD (ms) — dari `StressReading.rmssd`



\\- Accelerometer magnitude (g)



\\- Gyroscope angular velocity (rad/s)







\\\*\\\*Output classifier:\\\*\\\*



\\- `ActivityMode` enum: `EXERCISE`, `STRESS`, `RELAX`







\\\*\\\*Threshold (berbasis jurnal):\\\*\\\*



| Sensor | Olahraga | Stres | Relax |



|---|---|---|---|



| HR | >100 BPM | 60-100 BPM | 50-80 BPM |



| Step | >100/min | <30/min | <30/min |



| RMSSD | Varies | <20 ms | >40 ms |



| Accel | >1.5g | <0.3g | <0.3g |



| Gyro | >1 rad/s | Low | Low |







\\\*\\\*Prioritas:\\\*\\\* Olahraga > Stres > Relax (jika HR tinggi + step tinggi → Olahraga meski RMSSD rendah).







\\\*\\\*Decision journal:\\\*\\\* Setiap threshold harus ada referensi jurnal (untuk Dosbim B).







\\---







\\## Dependencies (build.gradle.kts)







```kotlin



// Wear OS



implementation("com.google.android.gms:play-services-wearable:18.2.0")



implementation("androidx.health:health-services-client:1.1.0")



implementation("androidx.wear:wear-ongoing:1.0.0")







// Compose



implementation(platform("androidx.compose:compose-bom:2024.xx.xx"))



implementation("androidx.wear.compose:compose-material3")



implementation("androidx.wear.compose:compose-foundation")







// Coroutines



implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")







// Splash



implementation("androidx.core:core-splashscreen:1.0.1")



```







\\---







\\## Important Notes







⚠️ \\\*\\\*JANGAN MODIFIKASI:\\\*\\\*



\\- `AdaptifyMonitorService` background logic — sudah stabil



\\- `StressCalculator` RMSSD calculation — sudah valid ilmiah



\\- Permission handling di `MainActivity` — sudah lengkap







✅ \\\*\\\*BOLEH DITAMBAHKAN:\\\*\\\*



\\- `ActivityClassifier.kt` (file baru)



\\- Unit tests untuk classifier



\\- Room Database entity/DAO untuk logging (nanti di Fase 4)







🔥 \\\*\\\*SEBELUM CODING:\\\*\\\*



\\- Always `git commit` sebelum mulai task baru



\\- Read error messages completely before asking help



\\- Test di real watch, bukan emulator (emulator tidak ada sensor fisik)



\\# Adaptify Wear OS — Watch App







\\## Project Overview



Android Wear OS application untuk Xiaomi Wear OS Watch 2. Membaca sensor realtime (Heart Rate, Step Count, HRV/RMSSD, Accelerometer, Gyroscope) dan mengirim data ke aplikasi HP via Wearable Data Layer.







\\\*\\\*Status:\\\*\\\* Fase 1 SELESAI — Background service sudah berjalan sempurna.



\\\*\\\*Fase saat ini:\\\*\\\* Fase 2 — Implementasi ActivityClassifier berbasis threshold jurnal.







\\---







\\## Tech Stack



\\- \\\*\\\*Language:\\\*\\\* Kotlin



\\- \\\*\\\*UI:\\\*\\\* Jetpack Compose for Wear OS (Material 3)



\\- \\\*\\\*Sensor:\\\*\\\* Health Services Client (HR), Android Sensor API (Accel/Gyro/Step)



\\- \\\*\\\*Background:\\\*\\\* Foreground Service + WakeLock



\\- \\\*\\\*Data Transfer:\\\*\\\* Wearable Data Layer (Google Play Services)



\\- \\\*\\\*Build:\\\*\\\* Gradle 8.x, minSdk 30, targetSdk 36







\\---







\\## Project Structure







```



app/src/main/java/com/adaptify/adaptifywearos/



├── presentation/



│   ├── MainActivity.kt                 # UI utama, toggle monitoring



│   ├── AdaptifyMonitorService.kt      # Foreground service untuk background



│   ├── AdaptifyRealtimeSender.kt      # Kirim data ke HP via DataLayer



│   └── AdaptifyRealtimePayload.kt     # Data model untuk transfer



├── health/



│   └── HeartRateManager.kt            # Heart rate via Health Services



├── sensor/



│   └── SensorHandler.kt               # Accel, Gyro, Step counter



└── stress/



\&#x20;   └── StressCalculator.kt            # RMSSD calculation (HRV)



```







\\---







\\## Build Commands







```bash



\\# Build debug APK



./gradlew assembleDebug







\\# Install ke watch via ADB



adb -s <WATCH\\\_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk







\\# Run tests



./gradlew test







\\# Clean build



./gradlew clean



```







\\---







\\## Key Classes Explained







\\### 1. AdaptifyMonitorService.kt



\\\*\\\*Tujuan:\\\*\\\* Background monitoring dengan Foreground Service.







\\\*\\\*Fitur:\\\*\\\*



\\- Start/stop monitoring via Intent (`ACTION\\\_TOGGLE`)



\\- WakeLock untuk prevent sleep



\\- Activity lifecycle tracking (auto-pause saat UI aktif)



\\- Notification ongoing untuk Wear OS



\\- Combine sensor + HR data → kirim ke HP







\\\*\\\*Penting:\\\*\\\* Service ini SUDAH BERFUNGSI DI BACKGROUND. Jangan diubah tanpa alasan kuat.







\\### 2. StressCalculator.kt



\\\*\\\*Tujuan:\\\*\\\* Menghitung RMSSD (Root Mean Square of Successive Differences) dari RR intervals.







\\\*\\\*Metode ilmiah:\\\*\\\*



\\- RR interval = 60000 / HR (ms)



\\- RMSSD = sqrt(mean(successive\\\_differences^2))



\\- Baseline HR tracking (15 samples warmup)



\\- Smoothing via exponential moving average







\\\*\\\*Output saat ini:\\\*\\\* Stress Index (0-100) dengan level (Calm/Moderate/Elevated/High).







\\\*\\\*CATATAN:\\\*\\\* Ini adalah implementasi RMSSD yang valid secara ilmiah, TIDAK PERLU DIUBAH untuk fase 2. Kita hanya perlu membuat \\\*\\\*ActivityClassifier\\\*\\\* yang menggunakan `StressReading.rmssd` sebagai input.







\\### 3. SensorHandler.kt



\\\*\\\*Tujuan:\\\*\\\* Read accelerometer, gyroscope, step counter.







\\\*\\\*Output:\\\*\\\* `SensorSnapshot` dengan semua data sensor.







\\### 4. HeartRateManager.kt



\\\*\\\*Tujuan:\\\*\\\* Read heart rate via Health Services Client.







\\\*\\\*Output:\\\*\\\* `HeartRateState` dengan BPM + timestamp.







\\### 5. AdaptifyRealtimeSender.kt



\\\*\\\*Tujuan:\\\*\\\* Kirim data ke HP via `PutDataMapRequest`.







\\\*\\\*Path:\\\*\\\* `/adaptify/realtime`







\\\*\\\*Keys:\\\*\\\*



\\- `heartRate` (Int)



\\- `steps` (Int)



\\- `stressIndex` (Int)



\\- `rmssd` (Double)



\\- `updatedAt` (Long)







\\---







\\## Permissions (AndroidManifest.xml)







```xml



<!-- Sensor -->



<uses-permission android:name="android.permission.BODY\\\_SENSORS" />



<uses-permission android:name="android.permission.ACTIVITY\\\_RECOGNITION" />



<uses-permission android:name="android.permission.HIGH\\\_SAMPLING\\\_RATE\\\_SENSORS" />







<!-- Background -->



<uses-permission android:name="android.permission.BODY\\\_SENSORS\\\_BACKGROUND" />



<uses-permission android:name="android.permission.FOREGROUND\\\_SERVICE" />



<uses-permission android:name="android.permission.FOREGROUND\\\_SERVICE\\\_HEALTH" />



<uses-permission android:name="android.permission.WAKE\\\_LOCK" />







<!-- Notification -->



<uses-permission android:name="android.permission.POST\\\_NOTIFICATIONS" />







<!-- Health Connect (API 36+) -->



<uses-permission android:name="android.permission.health.READ\\\_HEART\\\_RATE" />



```







\\---







\\## Code Style \\\& Conventions







\\- \\\*\\\*Naming:\\\*\\\* camelCase untuk variables/functions, PascalCase untuk classes



\\- \\\*\\\*Kotlin idioms:\\\*\\\* Gunakan `?.let`, `?:`, data classes, sealed classes



\\- \\\*\\\*Coroutines:\\\*\\\* Semua async operation pakai coroutines (`suspend fun`, `launch`, `async`)



\\- \\\*\\\*Flow:\\\*\\\* Gunakan `StateFlow` untuk UI state, `SharedFlow` untuk events



\\- \\\*\\\*Compose:\\\*\\\* Stateless composables, hoist state ke ViewModel/Activity level



\\- \\\*\\\*Logging:\\\*\\\* `Log.d("Adaptify<Component>", "message")` untuk debug







\\---







\\## Testing Strategy







\\- \\\*\\\*Unit tests:\\\*\\\* Logic di StressCalculator, data transformations



\\- \\\*\\\*Instrumented tests:\\\*\\\* Sensor reading, Service lifecycle



\\- \\\*\\\*Manual testing:\\\*\\\* Install ke watch, test background behavior







\\---







\\## Known Issues \\\& Limitations







1\\. \\\*\\\*Xiaomi Doze Mode:\\\*\\\* Watch mungkin kill service setelah 1-2 jam. Solusi: user harus whitelist app dari battery optimization.



2\\. \\\*\\\*RMSSD accuracy:\\\*\\\* Perlu baseline 15 samples (\\\~30 detik) sebelum stabil.



3\\. \\\*\\\*Step counter:\\\*\\\* Reset saat watch reboot (sesuai Android SensorManager behavior).



4\\. \\\*\\\*Wearable Data Layer:\\\*\\\* Perlu Google Play Services di watch DAN HP.







\\---







\\## Next Steps (Fase 2)







\\\*\\\*Tujuan:\\\*\\\* Tambah `ActivityClassifier.kt` yang output 3 mode (Olahraga / Stres / Relax).







\\\*\\\*Input classifier:\\\*\\\*



\\- Heart Rate (BPM)



\\- Step Count (steps/min)



\\- RMSSD (ms) — dari `StressReading.rmssd`



\\- Accelerometer magnitude (g)



\\- Gyroscope angular velocity (rad/s)







\\\*\\\*Output classifier:\\\*\\\*



\\- `ActivityMode` enum: `EXERCISE`, `STRESS`, `RELAX`







\\\*\\\*Threshold (berbasis jurnal):\\\*\\\*



| Sensor | Olahraga | Stres | Relax |



|---|---|---|---|



| HR | >100 BPM | 60-100 BPM | 50-80 BPM |



| Step | >100/min | <30/min | <30/min |



| RMSSD | Varies | <20 ms | >40 ms |



| Accel | >1.5g | <0.3g | <0.3g |



| Gyro | >1 rad/s | Low | Low |







\\\*\\\*Prioritas:\\\*\\\* Olahraga > Stres > Relax (jika HR tinggi + step tinggi → Olahraga meski RMSSD rendah).







\\\*\\\*Decision journal:\\\*\\\* Setiap threshold harus ada referensi jurnal (untuk Dosbim B).







\\---







\\## Dependencies (build.gradle.kts)







```kotlin



// Wear OS



implementation("com.google.android.gms:play-services-wearable:18.2.0")



implementation("androidx.health:health-services-client:1.1.0")



implementation("androidx.wear:wear-ongoing:1.0.0")







// Compose



implementation(platform("androidx.compose:compose-bom:2024.xx.xx"))



implementation("androidx.wear.compose:compose-material3")



implementation("androidx.wear.compose:compose-foundation")







// Coroutines



implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")







// Splash



implementation("androidx.core:core-splashscreen:1.0.1")



```







\\---







\\## Important Notes







⚠️ \\\*\\\*JANGAN MODIFIKASI:\\\*\\\*



\\- `AdaptifyMonitorService` background logic — sudah stabil



\\- `StressCalculator` RMSSD calculation — sudah valid ilmiah



\\- Permission handling di `MainActivity` — sudah lengkap







✅ \\\*\\\*BOLEH DITAMBAHKAN:\\\*\\\*



\\- `ActivityClassifier.kt` (file baru)



\\- Unit tests untuk classifier



\\- Room Database entity/DAO untuk logging (nanti di Fase 4)







🔥 \\\*\\\*SEBELUM CODING:\\\*\\\*



\\- Always `git commit` sebelum mulai task baru



\\- Read error messages completely before asking help



\\- Test di real watch, bukan emulator (emulator tidak ada sensor fisik)









