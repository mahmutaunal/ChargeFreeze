package com.alpwarestudio.chargefreeze.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alpwarestudio.chargefreeze.MainActivity
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.data.BatteryMonitor
import com.alpwarestudio.chargefreeze.device.samsung.SamsungChargeController
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class FreezeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var controller: SamsungChargeController
    private lateinit var battery: BatteryMonitor
    private var threshold = 100

    override fun onCreate() {
        super.onCreate()
        controller = SamsungChargeController(this)
        battery = BatteryMonitor(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        threshold = intent?.getIntExtra(EXTRA_THRESHOLD, threshold) ?: threshold
        startForeground(NOTIFICATION_ID, notification())
        scope.launch {
            while (isActive) {
                val snapshot = battery.snapshot()
                controller.maintainFreeze(snapshot.level, threshold).onSuccess { threshold = it }
                delay(30_000.milliseconds)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel(); super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val showDetails = AppPreferences(this).showPersistentNotification
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(if (showDetails) getString(R.string.notification_text) else null)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(!showDetails)
            .build()
    }

    companion object {
        const val EXTRA_THRESHOLD = "threshold"
        private const val CHANNEL = "charge_freeze"
        private const val NOTIFICATION_ID = 42
        fun start(context: Context, threshold: Int) = context.startForegroundService(
            Intent(context, FreezeService::class.java).putExtra(EXTRA_THRESHOLD, threshold)
        )
    }
}
