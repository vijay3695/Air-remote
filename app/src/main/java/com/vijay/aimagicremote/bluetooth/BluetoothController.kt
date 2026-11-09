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
 * 1€ Filter (One Euro Filter) for smoothing gyroscope jitter
 * Tuned with minCutoff = 1.2f, beta = 0.08f for zero motion latency and smooth cursor motion.
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

    private val filterX = OneEuroFilter(minCutoff = 1.2f, beta = 0.08f)
    private val filterY = OneEuroFilter(minCutoff = 1.2f, beta = 0.08f)

    @Volatile
    private var isAirMouseActive: Boolean = false
    @Volatile
    var pointerSensitivity: Float = 1.5f

    // 3D Rotation Matrix for Gravity Alignment
    private val rotationMatrix = FloatArray(9)
    private var hasRotationMatrix = false

    // Continuous Dynamic Auto-Calibration & Zero Bias Tracking
    @Volatile
    private var gyroBiasX: Float = 0f
    @Volatile
    private var gyroBiasY: Float = 0f
    @Volatile
    private var gyroBiasZ: Float = 0f

    // Stillness Detection EMA
    private var stillnessEma: Float = 0f

    // Calibration Wizard Buffers
    @Volatile
    private var isCalibratingWizard: Boolean = false
    private val calibrationSamplesX = mutableListOf<Float>()
    private val calibrationSamplesY = mutableListOf<Float>()
    private val calibrationSamplesZ = mutableListOf<Float>()

    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        stillnessEma = 0f
        filterX.reset()
        filterY.reset()
        Log.i("BluetoothController", "Gyroscope center reset")
    }

    /**
     * 2-Second Neutral Pose Calibration Wizard
     */
    fun startNeutralPoseCalibration(onResult: (Boolean, String) -> Unit) {
        if (gyroscopeSensor == null) {
            onResult(false, "Gyroscope sensor not available on this phone.")
            return
        }

        controllerScope.launch {
            calibrationSamplesX.clear()
            calibrationSamplesY.clear()
            calibrationSamplesZ.clear()
            isCalibratingWizard = true

            val wasActive = isAirMouseActive
            if (!wasActive) {
                sensorManager?.registerListener(
                    this@BluetoothController,
                    gyroscopeSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }

            delay(2000) // 2-second calibration sampling window

            isCalibratingWizard = false
            if (!wasActive) {
                sensorManager?.unregisterListener(this@BluetoothController)
            }

            if (calibrationSamplesX.size > 20) {
                val meanX = calibrationSamplesX.average().toFloat()
                val meanY = calibrationSamplesY.average().toFloat()
                val meanZ = calibrationSamplesZ.average().toFloat()

                // Calculate variance to check if phone was held steady
                val varianceX = calibrationSamplesX.map { (it - meanX) * (it - meanX) }.average().toFloat()
                val varianceY = calibrationSamplesY.map { (it - meanY) * (it - meanY) }.average().toFloat()
                val varianceZ = calibrationSamplesZ.map { (it - meanZ) * (it - meanZ) }.average().toFloat()
                val totalVariance = sqrt(varianceX + varianceY + varianceZ)

                if (totalVariance < 0.15f) {
                    gyroBiasX = meanX
                    gyroBiasY = meanY
                    gyroBiasZ = meanZ
                    filterX.reset()
                    filterY.reset()

                    Log.i("BluetoothController", "Calibration successful! Variance: $totalVariance, Biases: X=$meanX, Y=$meanY, Z=$meanZ")
                    withContext(Dispatchers.Main) {
                        onResult(true, "Calibration successful! Variance: %.3f. Neutral pose set.".format(totalVariance))
                    }
                } else {
                    Log.w("BluetoothController", "Calibration failed due to phone motion. Variance: $totalVariance")
                    withContext(Dispatchers.Main) {
                        onResult(false, "Phone moved during calibration (Variance: %.2f). Hold steady and try again.".format(totalVariance))
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "Not enough sensor samples collected. Try again.")
                }
            }
        }
    }

    fun startAirMouse(): Boolean {
        if (isAirMouseActive) return true

        if (gyroscopeSensor == null) {
            Log.w("BluetoothController", "Gyroscope sensor not available on this device!")
            return false
        }

        val regGyro = sensorManager?.registerListener(
            this,
            gyroscopeSensor,
            SensorManager.SENSOR_DELAY_GAME
        ) ?: false

        rotationVectorSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        isAirMouseActive = regGyro
        Log.i("BluetoothController", "3D Smart Auto-Calibrating Air Mouse started (Registered: $regGyro)")
        return regGyro
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
                val rawGyroX = event.values[0] // pitch
                val rawGyroY = event.values[1] // roll
                val rawGyroZ = event.values[2] // yaw

                if (isCalibratingWizard) {
                    calibrationSamplesX.add(rawGyroX)
                    calibrationSamplesY.add(rawGyroY)
                    calibrationSamplesZ.add(rawGyroZ)
                    return
                }

                if (!isAirMouseActive) return

                val timestamp = event.timestamp
                val motionMag = sqrt(rawGyroX * rawGyroX + rawGyroY * rawGyroY + rawGyroZ * rawGyroZ)

                stillnessEma = 0.90f * stillnessEma + 0.10f * motionMag

                if (motionMag < 0.045f && stillnessEma < 0.045f) {
                    val alpha = 0.08f
                    gyroBiasX = (1 - alpha) * gyroBiasX + alpha * rawGyroX
                    gyroBiasY = (1 - alpha) * gyroBiasY + alpha * rawGyroY
                    gyroBiasZ = (1 - alpha) * gyroBiasZ + alpha * rawGyroZ
                    return
                }

                val cleanX = rawGyroX - gyroBiasX
                val cleanY = rawGyroY - gyroBiasY
                val cleanZ = rawGyroZ - gyroBiasZ

                val deadzone = 0.032f
                var deltaX = if (abs(cleanZ) > deadzone) -cleanZ else 0f
                var deltaY = if (abs(cleanX) > deadzone) -cleanX else 0f

                if (hasRotationMatrix) {
                    val projX = -(cleanZ * rotationMatrix[8] + cleanY * rotationMatrix[5] + cleanX * rotationMatrix[2])
                    val projY = -(cleanX * rotationMatrix[0] + cleanY * rotationMatrix[3] + cleanZ * rotationMatrix[6])

                    deltaX = if (abs(projX) > deadzone) projX else 0f
                    deltaY = if (abs(projY) > deadzone) projY else 0f
                }

                val smoothDx = filterX.filter(deltaX, timestamp)
                val smoothDy = filterY.filter(deltaY, timestamp)

                val scaledDx = (smoothDx * 28.0f * pointerSensitivity).coerceIn(-35.0f, 35.0f)
                val scaledDy = (smoothDy * 28.0f * pointerSensitivity).coerceIn(-35.0f, 35.0f)

                if (abs(scaledDx) > 0.1f || abs(scaledDy) > 0.1f) {
                    pointerUpdateListener?.invoke(scaledDx, scaledDy)
                }
            }
        }
    }
}
