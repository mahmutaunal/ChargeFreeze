package com.alpwarestudio.chargefreeze.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.data.BatteryMonitor
import com.alpwarestudio.chargefreeze.data.SessionStore
import com.alpwarestudio.chargefreeze.data.UsbConnectionMonitor
import com.alpwarestudio.chargefreeze.device.samsung.SamsungChargeController
import com.alpwarestudio.chargefreeze.domain.FreezeSession
import com.alpwarestudio.chargefreeze.domain.FreezeState
import com.alpwarestudio.chargefreeze.domain.SessionPhase
import com.alpwarestudio.chargefreeze.service.FreezeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val battery = BatteryMonitor(app)
    private val usb = UsbConnectionMonitor(app)
    val controller = SamsungChargeController(app)
    private val store = SessionStore(app)
    private val operationMutex = Mutex()
    private val _battery = MutableStateFlow(battery.snapshot())
    val batteryState = _battery.asStateFlow()
    private val _freeze = MutableStateFlow(FreezeState())
    val freeze = _freeze.asStateFlow()
    private var transientMessage: String? = null
    private var transientMessageJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            recoverOrResume()
            while (true) {
                _battery.value = battery.snapshot()
                syncSessionState()
                delay(2_000.milliseconds)
            }
        }
    }

    fun enable() {
        viewModelScope.launch(Dispatchers.IO) {
            operationMutex.withLock {
                val existing = store.load()
                if (existing != null) {
                    if (existing.phase == SessionPhase.ACTIVE) {
                        FreezeService.start(app)
                        syncSessionState()
                    } else {
                        recover(existing)
                    }
                    return@withLock
                }

                val snapshot = battery.snapshot()
                when {
                    !snapshot.plugged || !usb.isConnected() -> {
                        showMessage(app.getString(R.string.usb_required))
                        return@withLock
                    }
                    !controller.isSupported() -> {
                        showMessage(app.getString(R.string.unsupported))
                        return@withLock
                    }
                    !controller.hasWritePermission() -> {
                        showMessage(app.getString(R.string.permission_required))
                        return@withLock
                    }
                }

                val resumeChargeLevel = AppPreferences(app).resumeChargeLevel
                if (snapshot.level <= resumeChargeLevel) {
                    showMessage(
                        app.getString(
                            R.string.resume_level_must_be_lower,
                            resumeChargeLevel,
                            snapshot.level
                        )
                    )
                    return@withLock
                }

                val original = controller.readOriginalState()
                if (original == null) {
                    showMessage(app.getString(R.string.read_settings_failed))
                    return@withLock
                }
                if (!store.savePreparing(original, snapshot.level, resumeChargeLevel)) {
                    showMessage(app.getString(R.string.session_write_failed))
                    return@withLock
                }

                val beginResult = controller.beginFreeze(snapshot.level)
                if (beginResult.isFailure) {
                    rollback(original, beginResult.exceptionOrNull()?.message)
                    return@withLock
                }

                val threshold = beginResult.getOrThrow()
                if (!store.markActive(threshold)) {
                    rollback(original, app.getString(R.string.session_write_failed))
                    return@withLock
                }

                runCatching { FreezeService.start(app) }
                    .onSuccess {
                        transientMessage = null
                        syncSessionState()
                    }
                    .onFailure { rollback(original, it.message) }
            }
        }
    }

    fun disable() {
        viewModelScope.launch(Dispatchers.IO) {
            operationMutex.withLock {
                if (store.load() == null) {
                    showMessage(app.getString(R.string.no_active_session))
                } else {
                    showMessage(app.getString(R.string.restoring_settings))
                    runCatching { FreezeService.stop(app) }
                        .onFailure { recover(store.load()) }
                }
            }
        }
    }

    fun restore() {
        viewModelScope.launch(Dispatchers.IO) {
            operationMutex.withLock { recover(store.load()) }
        }
    }

    private fun recoverOrResume() {
        val session = store.load() ?: return
        if (session.phase == SessionPhase.ACTIVE) {
            runCatching { FreezeService.start(app) }
                .onFailure { recover(session) }
            syncSessionState()
        } else {
            recover(session)
        }
    }

    private fun recover(session: FreezeSession?) {
        if (session == null) {
            syncSessionState()
            return
        }
        store.markRestoring()
        controller.restore(session.original)
            .onSuccess {
                store.clear()
                showMessage(app.getString(R.string.recovery_completed))
            }
            .onFailure {
                val message = app.getString(
                    R.string.restore_failed_format,
                    it.message ?: app.getString(R.string.unknown_error)
                )
                store.markRecoveryRequired(message)
                showMessage(message)
            }
    }

    private fun rollback(
        original: com.alpwarestudio.chargefreeze.domain.OriginalBatteryProtection,
        cause: String?
    ) {
        controller.restore(original)
            .onSuccess {
                store.clear()
                showMessage(cause ?: app.getString(R.string.freeze_start_failed))
            }
            .onFailure {
                val message = app.getString(
                    R.string.rollback_failed_format,
                    cause ?: app.getString(R.string.unknown_error),
                    it.message ?: app.getString(R.string.unknown_error)
                )
                store.markRecoveryRequired(message)
                showMessage(message)
            }
    }

    private fun syncSessionState() {
        val session = store.load()
        _freeze.value = if (session?.phase == SessionPhase.ACTIVE) {
            FreezeState(
                active = true,
                startLevel = session.startLevel,
                currentThreshold = session.currentThreshold,
                resumeChargeLevel = session.resumeChargeLevel,
                startedAtMillis = session.startedAtMillis,
                recoveryRequired = false,
                message = transientMessage
            )
        } else {
            FreezeState(
                recoveryRequired = session != null,
                message = session?.message ?: transientMessage
            )
        }
    }

    private fun showMessage(message: String?) {
        transientMessageJob?.cancel()
        transientMessage = message
        syncSessionState()

        if (message != null) {
            transientMessageJob = viewModelScope.launch {
                delay(TRANSIENT_MESSAGE_DURATION_MS.milliseconds)
                if (transientMessage == message) {
                    transientMessage = null
                    syncSessionState()
                }
            }
        }
    }

    companion object {
        private const val TRANSIENT_MESSAGE_DURATION_MS = 4_000L
    }
}
