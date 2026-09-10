package com.alpwarestudio.chargefreeze.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.hardware.usb.UsbManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.alpwarestudio.chargefreeze.MainActivity
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.data.UsbConnectionMonitor

/**
 * Offers Charge Freeze when USB power is connected and the user opted in.
 * It never starts a freeze silently; the notification requires an explicit tap.
 */
class UsbConnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in SUPPORTED_ACTIONS) return
        if (!AppPreferences(context).startOnUsbConnect) return
        if (!UsbConnectionMonitor(context).isConnected()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT)
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.usb_prompt_title))
                .setContentText(context.getString(R.string.usb_prompt_text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            UsbManager.ACTION_USB_ACCESSORY_ATTACHED,
            Intent.ACTION_POWER_CONNECTED
        )
        const val CHANNEL_ID = "usb_freeze_prompt"
        const val NOTIFICATION_ID = 43
    }
}
