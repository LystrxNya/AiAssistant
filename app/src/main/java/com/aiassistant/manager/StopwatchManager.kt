package com.aiassistant.manager

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import com.aiassistant.service.StopwatchService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class StopwatchState {
    IDLE, RUNNING, PAUSED
}

@Singleton
class StopwatchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(StopwatchState.IDLE)
    val state: StateFlow<StopwatchState> = _state.asStateFlow()

    private val _elapsedSeconds = MutableSharedFlow<Long>(replay = 1)
    val elapsedSeconds: SharedFlow<Long> = _elapsedSeconds.asSharedFlow()

    private var startElapsedTime = 0L
    private var pausedAccumulated = 0L
    private var pauseStartElapsedTime = 0L
    private var tickerJob: kotlinx.coroutines.Job? = null

    init {
        restorePersistedState()
    }

    fun start() {
        if (_state.value == StopwatchState.RUNNING) return

        startElapsedTime = SystemClock.elapsedRealtime()
        pausedAccumulated = 0L
        _state.value = StopwatchState.RUNNING
        persistState()

        scope.launch { _elapsedSeconds.emit(0L) }
        startForegroundService(StopwatchService.ACTION_START)
        startTicker()
    }

    fun pause() {
        if (_state.value != StopwatchState.RUNNING) return

        pauseStartElapsedTime = SystemClock.elapsedRealtime()
        _state.value = StopwatchState.PAUSED
        persistState()
        tickerJob?.cancel()

        sendActionToService(StopwatchService.ACTION_PAUSE)
    }

    fun resume() {
        if (_state.value != StopwatchState.PAUSED) return

        pausedAccumulated += SystemClock.elapsedRealtime() - pauseStartElapsedTime
        _state.value = StopwatchState.RUNNING
        persistState()

        sendActionToService(StopwatchService.ACTION_RESUME)
        startTicker()
    }

    fun stop(): Long {
        if (_state.value == StopwatchState.IDLE) return 0L

        tickerJob?.cancel()

        val totalElapsed = getElapsedSeconds()

        _state.value = StopwatchState.IDLE
        startElapsedTime = 0L
        pausedAccumulated = 0L
        pauseStartElapsedTime = 0L
        clearPersistedState()

        scope.launch { _elapsedSeconds.emit(0L) }
        sendActionToService(StopwatchService.ACTION_STOP)
        return totalElapsed
    }

    fun getElapsedSeconds(): Long {
        if (startElapsedTime == 0L) return 0L

        return when (_state.value) {
            StopwatchState.RUNNING -> {
                val now = SystemClock.elapsedRealtime()
                ((now - startElapsedTime - pausedAccumulated) / 1000).coerceAtLeast(0)
            }
            StopwatchState.PAUSED -> {
                ((pauseStartElapsedTime - startElapsedTime - pausedAccumulated) / 1000).coerceAtLeast(0)
            }
            StopwatchState.IDLE -> 0L
        }
    }

    fun restoreState(): Long {
        restorePersistedState()
        val elapsed = getElapsedSeconds()
        if (_state.value != StopwatchState.IDLE) {
            scope.launch { _elapsedSeconds.emit(elapsed) }
            if (_state.value == StopwatchState.RUNNING) {
                startTicker()
            }
        }
        return elapsed
    }

    private fun restorePersistedState() {
        val stateOrdinal = prefs.getInt(KEY_STATE, StopwatchState.IDLE.ordinal)
        val savedState = StopwatchState.entries.getOrElse(stateOrdinal) { StopwatchState.IDLE }

        if (savedState == StopwatchState.IDLE) return

        startElapsedTime = prefs.getLong(KEY_START_ELAPSED, 0L)
        pausedAccumulated = prefs.getLong(KEY_PAUSED_ACCUMULATED, 0L)
        pauseStartElapsedTime = prefs.getLong(KEY_PAUSE_START, 0L)

        if (startElapsedTime == 0L) {
            clearPersistedState()
            return
        }

        _state.value = savedState
    }

    private fun persistState() {
        prefs.edit().apply {
            putInt(KEY_STATE, _state.value.ordinal)
            putLong(KEY_START_ELAPSED, startElapsedTime)
            putLong(KEY_PAUSED_ACCUMULATED, pausedAccumulated)
            putLong(KEY_PAUSE_START, pauseStartElapsedTime)
            apply()
        }
    }

    private fun clearPersistedState() {
        prefs.edit().clear().apply()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (_state.value == StopwatchState.RUNNING) {
                val elapsed = getElapsedSeconds()
                _elapsedSeconds.emit(elapsed)
                delay(1000)
            }
        }
    }

    private fun startForegroundService(action: String) {
        val intent = Intent(context, StopwatchService::class.java).apply {
            this.action = action
        }
        context.startForegroundService(intent)
    }

    private fun sendActionToService(action: String) {
        val intent = Intent(context, StopwatchService::class.java).apply {
            this.action = action
        }
        context.startService(intent)
    }

    fun formatTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun release() {
        tickerJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val PREFS_NAME = "stopwatch_prefs"
        private const val KEY_STATE = "state"
        private const val KEY_START_ELAPSED = "start_elapsed"
        private const val KEY_PAUSED_ACCUMULATED = "paused_accumulated"
        private const val KEY_PAUSE_START = "pause_start"
    }
}
