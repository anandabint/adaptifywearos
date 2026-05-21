package com.adaptify.adaptifywearos.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.adaptify.adaptifywearos.health.HeartRateManager
import com.adaptify.adaptifywearos.health.HeartRateState
import com.adaptify.adaptifywearos.presentation.theme.AdaptifyWearOsTheme
import com.adaptify.adaptifywearos.sensor.SensorHandler
import com.adaptify.adaptifywearos.sensor.SensorSnapshot
import com.adaptify.adaptifywearos.sensor.Vector3Sample
import com.adaptify.adaptifywearos.stress.StressCalculator
import com.adaptify.adaptifywearos.stress.StressReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    companion object {
        private const val MODERN_HEART_RATE_PERMISSION_API = 36
    }

    private val sensorHandler by lazy { SensorHandler(applicationContext) }
    private val heartRateManager by lazy { HeartRateManager(applicationContext) }

    private val realtimeSender by lazy { AdaptifyRealtimeSender(applicationContext) }
    private val stressCalculator = StressCalculator()

    private val permissionState = MutableStateFlow(PermissionState())
    private val dashboardState = MutableStateFlow(DashboardUiState())

    private var permissionsRequestedOnce = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissions()
            restartHeartRateMonitoring()
            restartMotionMonitoring()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("AdaptifyWearSender", "WATCH APP STARTED")
        refreshPermissions()
        observeDashboard()

        setContent {
            val uiState by dashboardState.collectAsState()
            AdaptifyWearOsTheme {
                WearDashboard(
                    state = uiState,
                    onRequestPermissions = ::requestMissingPermissions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        refreshPermissions()
        requestMissingPermissionsOnce()
        restartMotionMonitoring()
    }

    override fun onStart() {
        super.onStart()

        refreshPermissions()
        requestMissingPermissionsOnce()
        restartHeartRateMonitoring()
    }

    override fun onPause() {
        sensorHandler.stop()
        super.onPause()
    }

    override fun onStop() {
        lifecycleScope.launch {
            heartRateManager.stop()
        }
        super.onStop()
    }

    private fun observeDashboard() {
        lifecycleScope.launch {
            combine(
                sensorHandler.sensorState,
                heartRateManager.heartRateState,
                permissionState,
            ) { sensorSnapshot, heartRateState, permissions ->
                DashboardUiState(
                    sensorSnapshot = sensorSnapshot,
                    heartRateState = heartRateState,
                    stressReading = stressCalculator.calculate(
                        currentHeartRate = heartRateState.bpm,
                        heartRateTimestamp = heartRateState.lastUpdatedEpochMillis,
                        accelerometerDelta = sensorSnapshot.accelerometerDelta,
                    ),
                    permissionState = permissions,
                )
            }.collect { state ->
                dashboardState.value = state

                val json = """
    {
        "heartRate": ${state.heartRateState.bpm ?: 0},
        "steps": ${state.sensorSnapshot.steps ?: 0},
        "stress": ${state.stressReading.index}
    }
    """.trimIndent()

                realtimeSender.sendPayload(json)

            }
        }
    }

    private fun sendHeartRateToPhone(bpm: Int) {
        val request = PutDataMapRequest.create("/adaptify_heart_rate").apply {
            dataMap.putInt("heart_rate", bpm)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this)
            .putDataItem(request)
            .addOnSuccessListener {
                Log.d("AdaptifyWearSender", "SUCCESS SEND BPM: $bpm")
            }
            .addOnFailureListener {
                Log.e("AdaptifyWearSender", "FAILED SEND", it)
            }
    }

    private fun restartMotionMonitoring() {
        val currentPermissions = permissionState.value

        sensorHandler.stop()
        sensorHandler.start(
            scope = lifecycleScope,
            canReadSteps = currentPermissions.activityRecognitionGranted,
        )
    }

    private fun restartHeartRateMonitoring() {
        val currentPermissions = permissionState.value

        lifecycleScope.launch {
            if (currentPermissions.heartRateGranted) {
                heartRateManager.start()
            } else {
                heartRateManager.stop()
            }
        }
    }

    private fun refreshPermissions() {
        permissionState.update {
            PermissionState(
                activityRecognitionGranted = hasPermission(Manifest.permission.ACTIVITY_RECOGNITION),
                heartRateGranted = hasPermission(requiredHeartRatePermission()),
            )
        }
    }

    private fun requestMissingPermissionsOnce() {
        if (permissionState.value.allRequestedSensorsGranted || permissionsRequestedOnce) return

        permissionsRequestedOnce = true
        requestMissingPermissions()
    }

    private fun requestMissingPermissions() {
        val missingPermissions = buildRequiredPermissions()
            .filterNot(::hasPermission)

        if (missingPermissions.isEmpty()) return
        permissionLauncher.launch(missingPermissions.toTypedArray())
    }

    private fun buildRequiredPermissions(): List<String> = listOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        requiredHeartRatePermission(),
    )

    private fun requiredHeartRatePermission(): String =
        if (Build.VERSION.SDK_INT >= MODERN_HEART_RATE_PERMISSION_API) {
            HeartRateManager.READ_HEART_RATE_PERMISSION
        } else {
            Manifest.permission.BODY_SENSORS
        }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

data class PermissionState(
    val activityRecognitionGranted: Boolean = false,
    val heartRateGranted: Boolean = false,
) {
    val allRequestedSensorsGranted: Boolean
        get() = activityRecognitionGranted && heartRateGranted

    val missingPermissionSummary: String
        get() = buildString {
            if (!heartRateGranted) append("Heart rate")
            if (!activityRecognitionGranted) {
                if (isNotEmpty()) append(" + ")
                append("Steps")
            }
        }
}

data class DashboardUiState(
    val sensorSnapshot: SensorSnapshot = SensorSnapshot(),
    val heartRateState: HeartRateState = HeartRateState(),
    val stressReading: StressReading = StressReading(),
    val permissionState: PermissionState = PermissionState(),
)

@Composable
private fun WearDashboard(
    state: DashboardUiState,
    onRequestPermissions: () -> Unit,
) {
    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                contentPadding = contentPadding,
                state = listState,
            ) {
                item {
                    HeaderCard(state = state)
                }

                if (!state.permissionState.allRequestedSensorsGranted) {
                    item {
                        PermissionCard(
                            missingPermissionSummary = state.permissionState.missingPermissionSummary,
                            onRequestPermissions = onRequestPermissions,
                        )
                    }
                }

                item {
                    ValueCard(
                        title = "Heart Rate",
                        value = state.heartRateState.bpm?.let { "$it bpm" } ?: "-- bpm",
                        subtitle = heartRateSubtitle(state),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                item {
                    ValueCard(
                        title = "Steps",
                        value = state.sensorSnapshot.steps?.toString() ?: "--",
                        subtitle = stepSubtitle(state.sensorSnapshot),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                item {
                    VectorCard(
                        title = "Accelerometer",
                        sample = state.sensorSnapshot.accelerometer,
                        subtitle = "delta ${state.sensorSnapshot.accelerometerDelta.format(2)}",
                        unavailableLabel = "Accelerometer not available",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                item {
                    VectorCard(
                        title = "Gyroscope",
                        sample = state.sensorSnapshot.gyroscope,
                        subtitle = if (state.sensorSnapshot.gyroscopeAvailable) {
                            "Live angular velocity"
                        } else {
                            "Gyroscope not available"
                        },
                        unavailableLabel = "Gyroscope not available",
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }

                item {
                    StressCard(
                        stressReading = state.stressReading,
                        currentHeartRate = state.heartRateState.bpm,
                    )
                }

                item {
                    FooterCard()
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(state: DashboardUiState) {
    SurfaceCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        padding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Adaptify Monitor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Wear OS dashboard with Health Services heart rate and 1s motion refresh.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (state.permissionState.allRequestedSensorsGranted) {
                    "All required permissions granted."
                } else {
                    "Some metrics are limited until permissions are granted."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    missingPermissionSummary: String,
    onRequestPermissions: () -> Unit,
) {
    SurfaceCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Permission needed: $missingPermissionSummary",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant access")
            }
        }
    }
}

@Composable
private fun ValueCard(
    title: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
) {
    SurfaceCard(
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun VectorCard(
    title: String,
    sample: Vector3Sample?,
    subtitle: String,
    unavailableLabel: String,
    containerColor: Color,
    contentColor: Color,
) {
    SurfaceCard(
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
            )
            if (sample == null) {
                Text(
                    text = unavailableLabel,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                AxisRow("x", sample.x)
                AxisRow("y", sample.y)
                AxisRow("z", sample.z)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StressCard(
    stressReading: StressReading,
    currentHeartRate: Int?,
) {
    val stressColor = when (stressReading.level) {
        "High" -> MaterialTheme.colorScheme.errorContainer
        "Elevated" -> MaterialTheme.colorScheme.tertiaryContainer
        "Moderate" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val stressContentColor = when (stressReading.level) {
        "High" -> MaterialTheme.colorScheme.onErrorContainer
        "Elevated" -> MaterialTheme.colorScheme.onTertiaryContainer
        "Moderate" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    SurfaceCard(
        containerColor = stressColor,
        contentColor = stressContentColor,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Stress Index",
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stressReading.index.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stressReading.level,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = when {
                    !stressReading.baselineReady -> "Warming up baseline from heart-rate samples"
                    stressReading.baselineHeartRate != null -> {
                        val delta = currentHeartRate?.minus(stressReading.baselineHeartRate) ?: 0
                        "Baseline ${stressReading.baselineHeartRate} bpm, delta ${abs(delta)} bpm"
                    }
                    else -> "Waiting for heart-rate data"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FooterCard() {
    SurfaceCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = "Heart rate stays active while the app is open, while motion sensors pause when the UI sleeps to save battery.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AxisRow(axis: String, value: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = axis,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value.format(2),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SurfaceCard(
    containerColor: Color,
    contentColor: Color,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(
                color = containerColor,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

private fun heartRateSubtitle(state: DashboardUiState): String = when {
    !state.permissionState.heartRateGranted -> "Grant heart-rate permission"
    !state.heartRateState.isSupported -> state.heartRateState.availabilityLabel
    else -> state.heartRateState.availabilityLabel
}

private fun stepSubtitle(sensorSnapshot: SensorSnapshot): String = when {
    !sensorSnapshot.stepCounterAvailable -> "Step counter not available"
    !sensorSnapshot.stepPermissionGranted -> "Grant activity recognition permission"
    else -> "Session steps since app start"
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun DefaultPreview() {
    AdaptifyWearOsTheme {
        WearDashboard(
            state = DashboardUiState(
                sensorSnapshot = SensorSnapshot(
                    accelerometer = Vector3Sample(0.04f, 9.81f, 0.12f),
                    gyroscope = Vector3Sample(0.01f, 0.03f, -0.02f),
                    steps = 187,
                    accelerometerDelta = 0.48f,
                    accelerometerAvailable = true,
                    gyroscopeAvailable = true,
                    stepCounterAvailable = true,
                    stepPermissionGranted = true,
                ),
                heartRateState = HeartRateState(
                    bpm = 82,
                    isSupported = true,
                    isMeasuring = true,
                    availabilityLabel = "Live",
                    lastUpdatedEpochMillis = System.currentTimeMillis(),
                ),
                stressReading = StressReading(
                    index = 36,
                    level = "Moderate",
                    baselineHeartRate = 72,
                    baselineReady = true,
                ),
                permissionState = PermissionState(
                    activityRecognitionGranted = true,
                    heartRateGranted = true,
                ),
            ),
            onRequestPermissions = {},
        )
    }
}
