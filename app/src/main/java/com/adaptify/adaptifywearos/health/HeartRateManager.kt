package com.adaptify.adaptifywearos.health

import android.content.Context
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseEndReason
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.WarmUpConfig
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class HeartRateState(
    val bpm: Int? = null,
    val isSupported: Boolean = true,
    val isMeasuring: Boolean = false,
    val availabilityLabel: String = "Heart rate idle",
    val lastUpdatedEpochMillis: Long? = null,
)

class HeartRateManager(context: Context) {

    companion object {
        const val READ_HEART_RATE_PERMISSION = "android.permission.health.READ_HEART_RATE"
        private val DIRECT_EXECUTOR = Executor { command -> command.run() }
        private val PREFERRED_EXERCISE_TYPES = listOf(
            ExerciseType.WORKOUT,
            ExerciseType.WALKING,
            ExerciseType.RUNNING,
            ExerciseType.HIKING,
        )
    }

    private val healthServicesClient = HealthServices.getClient(context.applicationContext)
    private val exerciseClient = healthServicesClient.exerciseClient
    private val measureClient = healthServicesClient.measureClient

    private val _heartRateState = MutableStateFlow(HeartRateState())
    val heartRateState: StateFlow<HeartRateState> = _heartRateState.asStateFlow()

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionMutex = Mutex()
    private var shouldMonitor = false
    private var monitoringMode: MonitoringMode = MonitoringMode.None
    private var isMeasureRegistered = false
    private var isExerciseCallbackRegistered = false

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onAvailabilityChanged(
            dataType: androidx.health.services.client.data.DataType<*, *>,
            availability: Availability,
        ) {
            if (dataType != DataType.HEART_RATE_BPM) return

            _heartRateState.update { current ->
                current.copy(
                    availabilityLabel = availability.toAvailabilityLabel(),
                    isSupported = true,
                )
            }
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val latestReading = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
                .lastOrNull()
                ?.value
                ?.roundToInt()

            if (update.exerciseStateInfo.state.isEnded) {
                monitoringMode = MonitoringMode.None
                maybeRestartWarmUp(update.exerciseStateInfo.endReason)
            }

            _heartRateState.update { current ->
                current.copy(
                    bpm = latestReading ?: current.bpm,
                    isSupported = true,
                    isMeasuring = !update.exerciseStateInfo.state.isEnded,
                    availabilityLabel = update.toExerciseLabel(latestReading),
                    lastUpdatedEpochMillis = if (latestReading != null) {
                        System.currentTimeMillis()
                    } else {
                        current.lastUpdatedEpochMillis
                    },
                )
            }
        }

        override fun onRegistered() {
            isExerciseCallbackRegistered = true
            _heartRateState.update { current ->
                current.copy(
                    isSupported = true,
                    isMeasuring = true,
                    availabilityLabel = "Preparing sensor",
                )
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            monitoringMode = MonitoringMode.None
            isExerciseCallbackRegistered = false
            _heartRateState.update { current ->
                current.copy(
                    isMeasuring = false,
                    availabilityLabel = throwable.toDisplayMessage(),
                )
            }
        }
    }

    private val measureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(
            dataType: androidx.health.services.client.data.DeltaDataType<*, *>,
            availability: Availability,
        ) {
            if (dataType != DataType.HEART_RATE_BPM) return

            _heartRateState.update { current ->
                current.copy(availabilityLabel = availability.toAvailabilityLabel())
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            val latestReading = data.getData(DataType.HEART_RATE_BPM)
                .lastOrNull()
                ?.value
                ?.roundToInt()
                ?: return

            _heartRateState.update { current ->
                current.copy(
                    bpm = latestReading,
                    isSupported = true,
                    isMeasuring = true,
                    availabilityLabel = "Live",
                    lastUpdatedEpochMillis = System.currentTimeMillis(),
                )
            }
        }

        override fun onRegistered() {
            isMeasureRegistered = true
            _heartRateState.update { current ->
                current.copy(
                    isSupported = true,
                    isMeasuring = true,
                    availabilityLabel = "Live",
                )
            }
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            monitoringMode = MonitoringMode.None
            isMeasureRegistered = false
            _heartRateState.update { current ->
                current.copy(
                    isMeasuring = false,
                    availabilityLabel = throwable.toDisplayMessage(),
                )
            }
        }
    }

    suspend fun start() = sessionMutex.withLock {
        shouldMonitor = true
        if (monitoringMode != MonitoringMode.None) return

        _heartRateState.update { current ->
            current.copy(
                isSupported = true,
                isMeasuring = false,
                availabilityLabel = "Checking Health Services",
            )
        }

        if (startExerciseWarmUpLocked()) {
            return
        }

        startMeasureFallbackLocked()
    }

    suspend fun stop() = sessionMutex.withLock {
        shouldMonitor = false
        when (monitoringMode) {
            MonitoringMode.ExerciseWarmUp -> stopExerciseWarmUpLocked()
            MonitoringMode.MeasureFallback -> stopMeasureLocked()
            MonitoringMode.None -> {
                clearExerciseCallbackIfNeededLocked()
                clearMeasureCallbackIfNeededLocked()
            }
        }

        monitoringMode = MonitoringMode.None
        _heartRateState.update { current ->
            current.copy(
                isMeasuring = false,
                availabilityLabel = if (current.isSupported) "Paused" else current.availabilityLabel,
            )
        }
    }

    private suspend fun startExerciseWarmUpLocked(): Boolean {
        val capabilities = runCatching {
            exerciseClient.getCapabilitiesAsync().awaitResult()
        }.getOrElse {
            return false
        }

        val exerciseType = selectHeartRateExerciseType(capabilities) ?: return false

        if (!isExerciseCallbackRegistered) {
            runCatching {
                exerciseClient.setUpdateCallback(exerciseCallback)
                isExerciseCallbackRegistered = true
            }.getOrElse {
                isExerciseCallbackRegistered = false
                return false
            }
        }

        val currentExerciseInfo = runCatching {
            exerciseClient.getCurrentExerciseInfoAsync().awaitResult()
        }.getOrNull()

        if (currentExerciseInfo?.exerciseTrackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
            monitoringMode = MonitoringMode.ExerciseWarmUp
            _heartRateState.update { current ->
                current.copy(
                    isSupported = true,
                    isMeasuring = true,
                    availabilityLabel = "Reconnecting",
                )
            }
            return true
        }

        return runCatching {
            exerciseClient.prepareExerciseAsync(
                WarmUpConfig(
                    exerciseType = exerciseType,
                    dataTypes = setOf(DataType.HEART_RATE_BPM),
                ),
            ).awaitResult()

            monitoringMode = MonitoringMode.ExerciseWarmUp
            _heartRateState.update { current ->
                current.copy(
                    isSupported = true,
                    isMeasuring = true,
                    availabilityLabel = "Acquiring signal",
                )
            }
            true
        }.getOrElse {
            clearExerciseCallbackIfNeededLocked()
            false
        }
    }

    private suspend fun startMeasureFallbackLocked() {
        val capabilities = runCatching {
            measureClient.getCapabilitiesAsync().awaitResult()
        }.getOrElse { throwable ->
            _heartRateState.update { current ->
                current.copy(
                    isSupported = false,
                    isMeasuring = false,
                    availabilityLabel = throwable.toDisplayMessage(),
                )
            }
            return
        }

        if (DataType.HEART_RATE_BPM !in capabilities.supportedDataTypesMeasure) {
            _heartRateState.update { current ->
                current.copy(
                    isSupported = false,
                    isMeasuring = false,
                    availabilityLabel = "Heart rate not supported",
                )
            }
            return
        }

        runCatching {
            monitoringMode = MonitoringMode.MeasureFallback
            isMeasureRegistered = true
            _heartRateState.update { current ->
                current.copy(
                    isSupported = true,
                    isMeasuring = true,
                    availabilityLabel = "Starting live capture",
                )
            }
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, measureCallback)
        }.onFailure { throwable ->
            monitoringMode = MonitoringMode.None
            isMeasureRegistered = false
            _heartRateState.update { current ->
                current.copy(
                    isSupported = false,
                    isMeasuring = false,
                    availabilityLabel = throwable.toDisplayMessage(),
                )
            }
        }
    }

    private suspend fun stopExerciseWarmUpLocked() {
        runCatching {
            exerciseClient.endExerciseAsync().awaitResult()
        }
        clearExerciseCallbackIfNeededLocked()
    }

    private suspend fun stopMeasureLocked() {
        clearMeasureCallbackIfNeededLocked()
    }

    private suspend fun clearExerciseCallbackIfNeededLocked() {
        if (!isExerciseCallbackRegistered) return

        runCatching {
            exerciseClient.clearUpdateCallbackAsync(exerciseCallback).awaitResult()
        }
        isExerciseCallbackRegistered = false
    }

    private suspend fun clearMeasureCallbackIfNeededLocked() {
        if (!isMeasureRegistered) return

        runCatching {
            measureClient.unregisterMeasureCallbackAsync(
                DataType.HEART_RATE_BPM,
                measureCallback,
            ).awaitResult()
        }
        isMeasureRegistered = false
    }

    private fun maybeRestartWarmUp(@ExerciseEndReason endReason: Int) {
        if (!shouldMonitor) return
        if (
            endReason == ExerciseEndReason.USER_END ||
            endReason == ExerciseEndReason.AUTO_END_PERMISSION_LOST
        ) {
            return
        }

        managerScope.launch {
            sessionMutex.withLock {
                if (!shouldMonitor || monitoringMode != MonitoringMode.None) return@withLock

                _heartRateState.update { current ->
                    current.copy(
                        isMeasuring = false,
                        availabilityLabel = "Restarting sensor",
                    )
                }

                if (!startExerciseWarmUpLocked()) {
                    startMeasureFallbackLocked()
                }
            }
        }
    }

    private fun selectHeartRateExerciseType(capabilities: ExerciseCapabilities): ExerciseType? {
        val supportedHeartRateTypes = capabilities.supportedExerciseTypes.filter { exerciseType ->
            DataType.HEART_RATE_BPM in capabilities
                .getExerciseTypeCapabilities(exerciseType)
                .supportedDataTypes
        }

        return PREFERRED_EXERCISE_TYPES.firstOrNull { it in supportedHeartRateTypes }
            ?: supportedHeartRateTypes.firstOrNull()
    }

    private suspend fun <T> ListenableFuture<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    if (!continuation.isActive) return@addListener

                    runCatching { get() }
                        .onSuccess { value -> continuation.resume(value) }
                        .onFailure { error ->
                            val cause =
                                if (error is ExecutionException && error.cause != null) {
                                    error.cause!!
                                } else {
                                    error
                                }
                            continuation.resumeWithException(cause)
                        }
                },
                DIRECT_EXECUTOR,
            )

            continuation.invokeOnCancellation {
                cancel(true)
            }
        }

    private fun Availability.toAvailabilityLabel(): String = when (this) {
        DataTypeAvailability.ACQUIRING -> "Acquiring signal"
        DataTypeAvailability.AVAILABLE -> "Live"
        DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY -> "Wear the watch snugly"
        DataTypeAvailability.UNAVAILABLE -> "Sensor unavailable"
        else -> "Status changed"
    }

    private fun ExerciseUpdate.toExerciseLabel(latestReading: Int?): String {
        val state = exerciseStateInfo.state
        return when {
            state.isEnded -> exerciseStateInfo.endReason.toEndedLabel()
            latestReading != null -> "Live"
            state == ExerciseState.PREPARING || state == ExerciseState.USER_STARTING -> "Acquiring signal"
            state.isResuming -> "Reacquiring signal"
            state.isPaused -> "Paused"
            else -> "Preparing sensor"
        }
    }

    private fun Int.toEndedLabel(): String = when (this) {
        ExerciseEndReason.USER_END -> "Paused"
        ExerciseEndReason.AUTO_END_PERMISSION_LOST -> "Heart rate permission required"
        ExerciseEndReason.AUTO_END_SUPERSEDED -> "Another workout took over"
        ExerciseEndReason.AUTO_END_MISSING_LISTENER -> "Reopen app to continue"
        ExerciseEndReason.AUTO_END_PREPARE_EXPIRED -> "Restarting sensor"
        ExerciseEndReason.AUTO_END_PAUSE_EXPIRED -> "Paused too long"
        else -> "Heart rate unavailable"
    }

    private fun Throwable.toDisplayMessage(): String = when {
        message?.contains("permission", ignoreCase = true) == true -> "Heart rate permission required"
        message?.contains("support", ignoreCase = true) == true -> "Heart rate not supported"
        else -> "Heart rate unavailable"
    }

    private sealed interface MonitoringMode {
        data object None : MonitoringMode
        data object ExerciseWarmUp : MonitoringMode
        data object MeasureFallback : MonitoringMode
    }
}
