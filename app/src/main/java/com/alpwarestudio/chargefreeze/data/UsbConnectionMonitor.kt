package com.alpwarestudio.chargefreeze.data

import android.content.Context
import android.content.IntentFilter

/**
 * Tracks the phone-side USB connection independently from BatteryManager's power-source type.
 *
 * Samsung devices can report a computer USB connection as AC power depending on the selected
 * USB mode. The framework USB_STATE broadcast, on the other hand, describes whether the phone's
 * USB gadget is actually connected to a host, which is what ChargeFreeze needs to know.
 */
class UsbConnectionMonitor(private val context: Context) {
    fun isConnected(): Boolean {
        val state = context.registerReceiver(null, IntentFilter(ACTION_USB_STATE))
            ?: return false
        return state.getBooleanExtra(EXTRA_CONNECTED, false)
    }

    private companion object {
        const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
        const val EXTRA_CONNECTED = "connected"
    }
}
