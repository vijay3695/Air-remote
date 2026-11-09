package com.vijay.aimagicremote.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver for Bluetooth device discovery events
 * Handles: Device found, pairing requests, bond state changes
 */
class BluetoothBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DEVICE_FOUND = "android.bluetooth.device.action.FOUND"
        const val ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST"
        const val ACTION_BOND_STATE_CHANGED = "android.bluetooth.device.action.BOND_STATE_CHANGED"
    }

    // Listener interface for UI updates
    interface OnBluetoothEventListener {
        fun onDeviceFound(device: BluetoothDevice)
        fun onPairingRequest(device: BluetoothDevice)
        fun onBondStateChanged(device: BluetoothDevice, bondState: Int)
    }

    private var listener: OnBluetoothEventListener? = null

    fun setListener(listener: OnBluetoothEventListener) {
        this.listener = listener
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

        when (action) {
            ACTION_DEVICE_FOUND -> {
                Log.d("BluetoothBroadcastReceiver", "Device found: ${device?.name} (${device?.address})")
                device?.let { listener?.onDeviceFound(it) }
            }
            ACTION_PAIRING_REQUEST -> {
                Log.i("BluetoothBroadcastReceiver", "Pairing request from: ${device?.name} (${device?.address})")
                device?.let { listener?.onPairingRequest(it) }
            }
            ACTION_BOND_STATE_CHANGED -> {
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                Log.d("BluetoothBroadcastReceiver", "Bond state changed: $prevBondState -> $bondState for ${device?.name}")
                device?.let { listener?.onBondStateChanged(it, bondState) }
            }
        }
    }
}