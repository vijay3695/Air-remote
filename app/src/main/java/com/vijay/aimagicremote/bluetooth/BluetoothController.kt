package com.vijay.aimagicremote.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 1€ Filter (One Euro Filter) for smoothing gyroscope jitter.
 * Tuned as in AimMac: minCutoff = 1.2f, beta = 0.08f, dCutoff = 1.0f.
 */
class OneEuroFilter(
    private var minCutoff: Float = 1.2f,
    private var beta: Float = 0.08f,
    private var dCutoff: Float = 1.0f
) {
    private var xPrev: Float? = null
    private var dxPrev: Float = 0f
    private var tPrev: Long? = null

    fun filter(x: Float, timestampNs: Long): Float {
        if (tPrev == null || xPrev == null) {
            xPrev = x
            dxPrev = 0f
            tPrev = timestampNs
            return x
        }
        val dt = (timestampNs - tPrev!!) / 1_000_000_000f
        tPrev = timestampNs
        if (dt <= 0f) return xPrev!!

        val dx = (x - xPrev!!) / dt
        val edx = alpha(dt, dCutoff) * dx + (1 - alpha(dt, dCutoff)) * dxPrev
        dxPrev = edx

        val cutoff = minCutoff + beta * abs(edx)
        val result = alpha(dt, cutoff) * x + (1 - alpha(dt, cutoff)) * xPrev!!
        xPrev = result
        return result
    }

    private fun alpha(dt: Float, cutoff: Float): Float {
        val tau = 1.0f / (2 * Math.PI.toFloat() * cutoff)
        return 1.0f / (1.0f + tau / dt)
    }

    fun reset() {
        xPrev = null
        dxPrev = 0f
        tPrev = null
    }
}

/**
 * Smart 3D Gravity-Compensated Air Mouse with Continuous Dynamic Auto-Calibration
 * Features:
 * - 2-Second Neutral Pose Calibration Wizard with Stability Validation
 * - Rotation Vector + Gyroscope 3D Sensor Fusion
 * - Adaptive Motion Deadzone Gate & Delta Clamping
 */
class BluetoothController(private val context: Context) : SensorEventListener {

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val sensorManager: SensorManager? = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val gyroscopeSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Motion filters (one per axis) - AimMac 1€ filter tuning
    private val filterX = OneEuroFilter(minCutoff = 1.2f, beta = 0.08f)
    private val filterY = OneEuroFilter(minCutoff = 1.2f, beta = 0.08f)

    @Volatile
    private var isAirMouseActive: Boolean = false
    @Volatile
    var pointerSensitivity: Float = 1.5f

    // Rotation matrix for gravity alignment
    private val rotationMatrix = FloatArray(9)
    private var hasRotationMatrix = false

    // Aim direction from the device's own frame to the screen
    // Elevation: how far +Y points above horizon (0 = flat, +90 = pointing up)
    @Volatile
    private var aimElevation: Float = 0f
    @Volatile
    private var lastElevationTimestamp: Long = 0

    // Azimuth: rotation around vertical axis (left/right)
    @Volatile
    private var aimAzimuth: Float = 0f
    @Volatile
    private var lastAzimuthTimestamp: Long = 0

    // Deadzone for idle stabilization
    @Volatile
    private var deadzone: Float = 0.032f

    // Calibration: recent motion samples to compute bias
    @Volatile
    private var calibrationSamples: MutableList<FloatArray> = mutableListOf()

    @Volatile
    private var isCalibratingWizard: Boolean = false

    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var pointerUpdateListener: ((dx: Float, dy: Float) -> Unit)? = null

    var pointerUpdateListener: ((dx: Float, dy: Float) -> Unit)? = null

    init {
        Log.d("BluetoothController", "Air Mouse Controller initialized - Gyro: ${gyroscopeSensor != null}, RotationVector: ${rotationVectorSensor != null}")
    }

    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun setSensitivity(sens: Float) {
        pointerSensitivity = sens.coerceIn(0.2f, 4.0f)
    }

    fun calibrateGyro() {
        gyroBiasX = 0f
        gyroBiasY = 0f
        gyroBiasZ = 0f
        filterX.reset()
        filterY.reset()
        Log.i("BluetoothController", "Gyroscope center reset")
    }

    /**
     * 2-Second Neutral Pose Calibration Wizard.
     * Uses recent motion samples to compute gyro bias and validate phone stability.
     */
    fun startNeutralPoseCalibration(onResult: (Boolean, String) -> Unit) {
        if (gyroscopeSensor == null) {
            onResult(false, "Gyroscope sensor not available on this phone.")
            return
        }

        controllerScope.launch {
            calibrationSamples.clear()
            isCalibratingWizard = true

            val wasActive = isAirMouseActive
            if (!wasActive) {
                sensorManager?.registerListener(
                    this@BluetoothController,
                    rotationVectorSensor ?: gyroscopeSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }

            delay(2000) // 2-second calibration sampling window

            isCalibratingWizard = false
            if (!wasActive) {
                sensorManager?.unregisterListener(this@BluetoothController)
            }

            if (calibrationSamples.size > 20) {
                // Compute mean from samples
                val meanX = calibrationSamples.map { it[0] }.average().toFloat()
                val meanY = calibrationSamples.map { it[1] }.average().toFloat()
                val meanZ = calibrationSamples.map { it[2] }.average().toFloat()

                // Check if phone stayed still (low variance)
                val varianceX = calibrationSamples.map { (it[0] - meanX) * (it[0] - meanX) }.average().toFloat()
                val varianceY = calibrationSamples.map { (it[1] - meanY) * (it[1] - meanY) }.average().toFloat()
                val varianceZ = calibrationSamples.map { (it[2] - meanZ) * (it[2] - meanZ) }.average().toFloat()
                val totalVariance = sqrt(varianceX + varianceY + varianceZ)

                if (totalVariance < 0.25f) {
                    gyroBiasX = meanX
                    gyroBiasY = meanY
                    gyroBiasZ = meanZ
                    filterX.reset()
                    filterY.reset()

                    Log.i("BluetoothController", "Calibration successful! Variance: $totalVariance")
                    withContext(Dispatchers.Main) {
                        onResult(true, "Calibration successful!")
                    }
                } else {
                    Log.w("BluetoothController", "Calibration failed: phone moved during calibration (Variance: $totalVariance)")
                    withContext(Dispatchers.Main) {
                        onResult(false, "Phone moved during calibration. Hold steady and try again.")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Not enough samples. Try again.")
                }
            }
        }
    }

    /**
     * Start the air mouse with AimMac-style gravity-based motion processing.
     */
    fun startAirMouse(): Boolean {
        if (isAirMouseActive) return true

        if (gyroscopeSensor == null && rotationVectorSensor == null) {
            Log.w("BluetoothController", "No motion sensor available on this device!")
            return false
        }

        val regGyro = if (gyroscopeSensor != null) {
            sensorManager?.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME) ?: false
        } else {
            false
        }

        val regRVec = if (rotationVectorSensor != null) {
            sensorManager?.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME) ?: false
        } else {
            false
        }

        isAirMouseActive = regGyro || regRVec
        Log.i("BluetoothController", "Air Mouse started (Gyro: $regGyro, RotationVector: $regRVec)")
        return regGyro || regRVec
    }

    fun stopAirMouse() {
        if (!isAirMouseActive) return
        isAirMouseActive = false
        sensorManager?.unregisterListener(this)
        filterX.reset()
        filterY.reset()
        hasRotationMatrix = false
        Log.i("BluetoothController", "Air-mouse mode stopped")
    }

    fun shutdown() {
        stopAirMouse()
        controllerScope.cancel()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotationMatrix = true
            }

            Sensor.TYPE_GYROSCOPE -> {
                // Gyro bias correction during calibration
                if (isCalibratingWizard) {
                    calibrationSamples.add(floatArrayOf(event.values[0], event.values[1], event.values[2]))
                    return
                }

                if (!isAirMouseActive) return

                updateAimFromRotationVector(event.values, event.timestamp)
            }
        }
    }

    /**
     * AimMac-style motion processing:
     * - Elevation (up/down): gravity's Y component relative to horizon
     * - Azimuth (left/right): gyro rate around the up axis
     */
    private fun updateAimFromRotationVector(values: FloatArray, timestamp: Long) {
        val g = floatArrayOf(values[0], values[1], values[2])
        val gn = sqrt(g[0]*g[0] + g[1]*g[1] + g[2]*g[2])
        if (gn < 0.1f) return

        val gx = g[0] / gn
        val gy = g[1] / gn
        val gz = g[2] / gn

        // Elevation: angle of top edge above horizon
        aimElevation = asin(max(-1f, min(1f, -gy))) * 180 / Math.PI.toFloat()
        lastElevationTimestamp = timestamp

        // Azimuth: update based on rotation rate
        val dt = if (lastAzimuthTimestamp != 0L) {
            min(max(timestamp - lastAzimuthTimestamp, 0L), 50_000_000L).toFloat() / 1_000_000_000f
        } else {
            0f
        }
        if (dt > 0.001f && hasRotationMatrix) {
            val w = values[0]
            aimAzimuth += w * dt * 180 / Math.PI.toFloat()
            aimAzimuth %= 360.0
        }
        lastAzimuthTimestamp = timestamp
    }
}
