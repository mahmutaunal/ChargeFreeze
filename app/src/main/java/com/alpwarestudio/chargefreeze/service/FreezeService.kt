package com.alpwarestudio.chargefreeze.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alpwarestudio.chargefreeze.MainActivity
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.data.BatteryMonitor
import com.alpwarestudio.chargefreeze.data.SessionStore
import com.alpwarestudio.chargefreeze.device.samsung.SamsungChargeController
import com.alpwarestudio.chargefreeze.domain.SessionPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FreezeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var controller: SamsungChargeController
    private lateinit var battery: BatteryMonitor
    private lateinit var store: SessionStore
    private var maintenanceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        controller = SamsungChargeController(this)
        battery = BatteryMonitor(this)
        store = SessionStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        maintenanceJob?.cancel()
        maintenanceJob = when (intent?.action) {
            ACTION_STOP -> scope.launch { restoreAndStop(getString(R.string.freeze_stopped)) }
            else -> scope.launch { maintainSession() }
        }
        return START_STICKY
    }

    private suspend fun maintainSession() {
        val loadedSession = store.load()
        if (loadedSession?.phase != SessionPhase.ACTIVE) {
            // A crash can leave a preparation/restoration half-finished. Fail closed.
            if (loadedSession != null) restoreAndStop(getString(R.string.recovery_completed))
            else stopSelf()
            return
        }
        var session = requireNotNull(loadedSession)

        var chargingSamples = 0
        while (scope.isActive) {
            val snapshot = battery.snapshot()
            if (!snapshot.plugged || snapshot.source != "USB") {
                restoreAndStop(getString(R.string.usb_disconnected_restored))
                return
            }

            val result = controller.maintainFreeze(snapshot.level, session.currentThreshold)
            if (result.isFailure) {
                restoreAndStop(result.exceptionOrNull()?.message ?: getString(R.string.maintenance_failed))
                return
            }

            val nextThreshold = result.getOrThrow()
            if (nextThreshold != session.currentThreshold) {
                if (!store.updateThreshold(nextThreshold)) {
                    restoreAndStop(getString(R.string.session_write_failed))
                    return
                }
                session = session.copy(currentThreshold = nextThreshold)
            }

            // A setting read-back is not proof that firmware stopped charging. Repeated charging
            // samples after activation mean this firmware cannot safely provide the capability.
            chargingSamples = if (snapshot.isCharging) chargingSamples + 1 else 0
            if (chargingSamples >= MAX_CHARGING_SAMPLES) {
                restoreAndStop(getString(R.string.charging_not_paused))
                return
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun restoreAndStop(reason: String) {
        val session = store.load()
        if (session == null) {
            stopSelf()
            return
        }
        store.markRestoring()
        controller.restore(session.original)
            .onSuccess {
                store.clear()
                publishState(reason)
            }
            .onFailure {
                store.markRecoveryRequired(
                    getString(R.string.restore_failed_format, it.message ?: getString(R.string.unknown_error))
                )
                publishState(it.message ?: reason)
            }
        stopSelf()
    }

    private fun publishState(message: String) {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    override fun onDestroy() {
        maintenanceJob?.cancel()
        // onDestroy is not guaranteed for a killed process, but when Android does call it we can
        // avoid leaving vendor settings modified after an unexpected service termination.
        store.load()
            ?.takeIf { it.phase == SessionPhase.ACTIVE }
            ?.let { session ->
                controller.restore(session.original)
                    .onSuccess { store.clear() }
                    .onFailure {
                        store.markRecoveryRequired(
                            getString(
                                R.string.restore_failed_format,
                                it.message ?: getString(R.string.unknown_error)
                            )
                        )
                    }
            }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FreezeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showDetails = AppPreferences(this).showPersistentNotification
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(if (showDetails) getString(R.string.notification_text) else null)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop_freeze), stopIntent)
            .setOngoing(true)
            .setSilent(!showDetails)
            .build()
    }

    companion object {
        const val ACTION_STATE_CHANGED = "com.alpwarestudio.chargefreeze.action.STATE_CHANGED"
        const val EXTRA_MESSAGE = "message"
        private const val ACTION_START = "com.alpwarestudio.chargefreeze.action.START"
        private const val ACTION_STOP = "com.alpwarestudio.chargefreeze.action.STOP"
        private const val CHANNEL = "charge_freeze"
        private const val NOTIFICATION_ID = 42
        private const val POLL_INTERVAL_MS = 15_000L
        private const val MAX_CHARGING_SAMPLES = 4

        fun start(context: Context) = context.startForegroundService(
            Intent(context, FreezeService::class.java).setAction(ACTION_START)
        )

        fun stop(context: Context) = context.startForegroundService(
            Intent(context, FreezeService::class.java).setAction(ACTION_STOP)
        )
    }
}
