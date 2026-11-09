package com.vijay.aimagicremote.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver for Bluetooth pairing result events
 * Handles the result of pairing attempts initiated by the TV or user
 */
class PairingResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST"
        const val ACTION_PAIRING_CANCEL = "android.bluetooth.device.action.PAIRING_CANCEL"
    }

    // Listener interface for pairing results
    interface OnPairingResultListener {
        fun onPairingRequested(device: BluetoothDevice, pairingKey: Int?)
        fun onPairingCancelled(device: BluetoothDevice)
        fun onPairingComplete(device: BluetoothDevice, success: Boolean)
    }

    private var listener: OnPairingResultListener? = null

    fun setListener(listener: OnPairingResultListener) {
        this.listener = listener
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

        when (action) {
            ACTION_PAIRING_REQUEST -> {
                // Get the pairing key if available
                val pairingKey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1)
                val key = if (pairingKey != -1) pairingKey else null
                
                Log.i("PairingResultReceiver", "Pairing requested by: ${device?.name} (key: $key)")
                device?.let { listener?.onPairingRequested(it, key) }
            }
            ACTION_PAIRING_CANCEL -> {
                Log.w("PairingResultReceiver", "Pairing cancelled for: ${device?.name}")
                device?.let { listener?.onPairingCancelled(it) }
            }
            // Note: Pairing completion is typically detected via BOND_STATE_CHANGED
            // in BluetoothBroadcastReceiver, but we can also handle it here
        }
    }
}