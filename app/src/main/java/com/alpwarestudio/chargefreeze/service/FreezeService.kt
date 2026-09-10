package com.alpwarestudio.chargefreeze.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alpwarestudio.chargefreeze.MainActivity
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.data.BatteryMonitor
import com.alpwarestudio.chargefreeze.data.SessionStore
import com.alpwarestudio.chargefreeze.data.UsbConnectionMonitor
import com.alpwarestudio.chargefreeze.device.samsung.SamsungChargeController
import com.alpwarestudio.chargefreeze.domain.SessionPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class FreezeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventMutex = Mutex()
    private lateinit var controller: SamsungChargeController
    private lateinit var battery: BatteryMonitor
    private lateinit var store: SessionStore
    private lateinit var usb: UsbConnectionMonitor
    private var startupJob: Job? = null
    private var receiverRegistered = false
    @Volatile private var stopping = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_DISCONNECTED,
                ACTION_USB_STATE -> scope.launch {
                    eventMutex.withLock { handleStateChanged() }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = SamsungChargeController(this)
        battery = BatteryMonitor(this)
        store = SessionStore(this)
        usb = UsbConnectionMonitor(this)
        createChannels()
        registerStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        startupJob?.cancel()
        startupJob = when (intent?.action) {
            ACTION_STOP -> scope.launch { eventMutex.withLock { restoreAndStop(getString(R.string.freeze_stopped)) } }
            else -> scope.launch { verifyAndBeginEventMonitoring() }
        }
        return START_STICKY
    }

    /**
     * Performs only a short startup verification. Once Samsung reports NOT_CHARGING, the service
     * becomes fully event-driven and reacts to battery/USB broadcasts instead of polling forever.
     */
    private suspend fun verifyAndBeginEventMonitoring() {
        val loadedSession = store.load()
        if (loadedSession?.phase != SessionPhase.ACTIVE) {
            if (loadedSession != null) restoreAndStop(getString(R.string.recovery_completed))
            else stopSelf()
            return
        }

        repeat(STARTUP_VERIFY_ATTEMPTS) { attempt ->
            if (stopping) return
            val snapshot = battery.snapshot()

            if (!snapshot.plugged || !usb.isConnected()) {
                restoreAndStop(getString(R.string.usb_disconnected_restored))
                return
            }

            if (snapshot.level <= loadedSession.resumeChargeLevel) {
                restoreAndStop(
                    getString(R.string.resume_level_reached, loadedSession.resumeChargeLevel),
                    notifyCompletion = true
                )
                return
            }

            if (!snapshot.isCharging) {
                // Synchronize the moving threshold once, then all future work is broadcast-driven.
                eventMutex.withLock { handleStateChanged() }
                return
            }

            if (attempt < STARTUP_VERIFY_ATTEMPTS - 1) delay(STARTUP_VERIFY_INTERVAL_MS.milliseconds)
        }

        restoreAndStop(getString(R.string.charging_not_paused))
    }

    /** Called only in response to Android battery/USB state events after startup. */
    private fun handleStateChanged() {
        if (stopping) return
        val session = store.load() ?: return
        if (session.phase != SessionPhase.ACTIVE) return

        val snapshot = battery.snapshot()
        if (!snapshot.plugged || !usb.isConnected()) {
            restoreAndStop(getString(R.string.usb_disconnected_restored))
            return
        }

        if (snapshot.level <= session.resumeChargeLevel) {
            restoreAndStop(
                getString(R.string.resume_level_reached, session.resumeChargeLevel),
                notifyCompletion = true
            )
            return
        }

        controller.maintainFreeze(snapshot.level, session.currentThreshold)
            .onSuccess { nextThreshold ->
                if (nextThreshold != session.currentThreshold && !store.updateThreshold(nextThreshold)) {
                    restoreAndStop(getString(R.string.session_write_failed))
                }
            }
            .onFailure {
                restoreAndStop(it.message ?: getString(R.string.maintenance_failed))
            }
    }

    private fun restoreAndStop(reason: String, notifyCompletion: Boolean = false) {
        if (stopping) return
        stopping = true
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
                if (notifyCompletion) showResumeNotification(session.resumeChargeLevel)
            }
            .onFailure {
                store.markRecoveryRequired(
                    getString(R.string.restore_failed_format, it.message ?: getString(R.string.unknown_error))
                )
                publishState(it.message ?: reason)
            }
        stopSelf()
    }

    private fun registerStateReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(ACTION_USB_STATE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterStateReceiver() {
        if (!receiverRegistered) return
        runCatching { unregisterReceiver(stateReceiver) }
        receiverRegistered = false
    }

    private fun publishState(message: String) {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    override fun onDestroy() {
        startupJob?.cancel()
        unregisterStateReceiver()
        // onDestroy is not guaranteed for a killed process, but when Android does call it we can
        // avoid leaving vendor settings modified after an unexpected service termination.
        if (!stopping) {
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
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(
                EVENT_CHANNEL,
                getString(R.string.notification_events_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun showResumeNotification(level: Int) {
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, EVENT_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.resume_notification_title))
            .setContentText(getString(R.string.resume_notification_text, level))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(RESUME_NOTIFICATION_ID, notification)
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
        private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
        private const val CHANNEL = "charge_freeze"
        private const val EVENT_CHANNEL = "charge_freeze_events"
        private const val NOTIFICATION_ID = 42
        private const val RESUME_NOTIFICATION_ID = 43
        private const val STARTUP_VERIFY_INTERVAL_MS = 1_500L
        private const val STARTUP_VERIFY_ATTEMPTS = 4

        fun start(context: Context) = context.startForegroundService(
            Intent(context, FreezeService::class.java).setAction(ACTION_START)
        )

        fun stop(context: Context) = context.startForegroundService(
            Intent(context, FreezeService::class.java).setAction(ACTION_STOP)
        )
    }
}
