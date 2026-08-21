package com.alpwarestudio.chargefreeze.ui.screens

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alpwarestudio.chargefreeze.data.BatteryMonitor
import com.alpwarestudio.chargefreeze.data.SessionStore
import com.alpwarestudio.chargefreeze.device.samsung.SamsungChargeController
import com.alpwarestudio.chargefreeze.domain.FreezeState
import com.alpwarestudio.chargefreeze.service.FreezeService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val battery = BatteryMonitor(app)
    val controller = SamsungChargeController(app)
    private val store = SessionStore(app)
    private val _battery = MutableStateFlow(battery.snapshot())
    val batteryState = _battery.asStateFlow()
    private val _freeze = MutableStateFlow(FreezeState())
    val freeze = _freeze.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _battery.value = battery.snapshot(); delay(5_000.milliseconds)
            }
        }
        // Fail-safe: a prior interrupted session is restored before accepting a new one.
        store.loadOriginal()
            ?.let { original -> controller.restore(original).onSuccess { store.clear() } }
    }

    fun enable() {
        val snapshot = battery.snapshot()
        val original = controller.readOriginalState() ?: run {
            _freeze.value =
                FreezeState(message = "Unable to read battery protection settings"); return
        }
        store.saveOriginal(original)
        controller.beginFreeze(snapshot.level)
            .onSuccess { threshold ->
                _freeze.value = FreezeState(true, snapshot.level, threshold, System.currentTimeMillis())
                FreezeService.start(getApplication(), threshold)
            }
            .onFailure { _freeze.value = FreezeState(message = it.message); store.clear() }
    }

    fun disable() {
        getApplication<Application>().stopService(
            Intent(
                getApplication(),
                FreezeService::class.java
            )
        )
        val original = store.loadOriginal()
        if (original != null) controller.restore(original).onSuccess { store.clear() }
        _freeze.value = FreezeState()
    }

    fun restore() = disable()
}
