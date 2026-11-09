package com.vijay.aimagicremote.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.vijay.aimagicremote.protocol.Atrpv2Protocol
import com.vijay.aimagicremote.protocol.PcRemoteProtocol
import com.vijay.aimagicremote.protocol.RemoteManager
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
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
 * - Real-Time Dynamic Self-Calibration (Zero Drift when still)
 * - Rotation Vector + Gyroscope 3D Sensor Fusion (LG Magic Remote math)
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

    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var pointerUpdateListener: ((dx: Float, dy: Float) -> Unit)? = null

    init {
        Log.d("BluetoothController", "Air Mouse Controller initialized - Gyro: ${gyroscopeSensor != null}, RotationVector: ${rotationVectorSensor != null}")
    }

    /**
     * Instantly Recenter / Reset Calibration
     */
    fun calibrateGyro() {
        gyroBiasX = 0f
        gyroBiasY = 0f
        gyroBiasZ = 0f
        stillnessEma = 0f
        filterX.reset()
        filterY.reset()
        Log.i("BluetoothController", "Gyroscope center reset")
    }

    fun startAirMouse(): Boolean {
        if (isAirMouseActive) return true

        if (gyroscopeSensor == null) {
            Log.w("BluetoothController", "Gyroscope sensor not available on this device!")
            return false
        }

        calibrateGyro()

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

    fun toggleAirMouse(): Boolean {
        return if (isAirMouseActive) {
            stopAirMouse()
            false
        } else {
            startAirMouse()
        }
    }

    fun isAirMouseActive(): Boolean = isAirMouseActive

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isAirMouseActive || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotationMatrix = true
            }

            Sensor.TYPE_GYROSCOPE -> {
                val timestamp = event.timestamp
                val rawGyroX = event.values[0] // pitch
                val rawGyroY = event.values[1] // roll
                val rawGyroZ = event.values[2] // yaw

                // Calculate total angular velocity magnitude
                val motionMag = sqrt(rawGyroX * rawGyroX + rawGyroY * rawGyroY + rawGyroZ * rawGyroZ)

                // Update Exponential Moving Average of stillness
                stillnessEma = 0.90f * stillnessEma + 0.10f * motionMag

                // CONTINUOUS DYNAMIC AUTO-CALIBRATION:
                // When phone is still or resting (motionMag < 0.045 rad/s and stillnessEma < 0.045 rad/s),
                // dynamically update DC bias towards current raw readings and suppress motion.
                if (motionMag < 0.045f && stillnessEma < 0.045f) {
                    val alpha = 0.08f
                    gyroBiasX = (1 - alpha) * gyroBiasX + alpha * rawGyroX
                    gyroBiasY = (1 - alpha) * gyroBiasY + alpha * rawGyroY
                    gyroBiasZ = (1 - alpha) * gyroBiasZ + alpha * rawGyroZ
                    return // Zero output when resting/still
                }

                // Subtract dynamically tracked DC bias
                val cleanX = rawGyroX - gyroBiasX
                val cleanY = rawGyroY - gyroBiasY
                val cleanZ = rawGyroZ - gyroBiasZ

                // Adaptive deadzone gate
                val deadzone = 0.032f
                var deltaX = if (abs(cleanZ) > deadzone) -cleanZ else 0f
                var deltaY = if (abs(cleanX) > deadzone) -cleanX else 0f

                if (hasRotationMatrix) {
                    // 3D Gravity Projection onto screen coordinates
                    val projX = -(cleanZ * rotationMatrix[8] + cleanY * rotationMatrix[5] + cleanX * rotationMatrix[2])
                    val projY = -(cleanX * rotationMatrix[0] + cleanY * rotationMatrix[3] + cleanZ * rotationMatrix[6])

                    deltaX = if (abs(projX) > deadzone) projX else 0f
                    deltaY = if (abs(projY) > deadzone) projY else 0f
                }

                if (deltaX == 0f && deltaY == 0f) return

                // Smooth 1€ Filter
                val filteredX = filterX.filter(deltaX, timestamp)
                val filteredY = filterY.filter(deltaY, timestamp)

                // Scale and clamp per-frame displacement to prevent cursor jumps
                val dx = (filteredX * pointerSensitivity * 22f).coerceIn(-30f, 30f)
                val dy = (filteredY * pointerSensitivity * 22f).coerceIn(-30f, 30f)

                if (abs(dx) > 0.05f || abs(dy) > 0.05f) {
                    pointerUpdateListener?.invoke(dx, dy)

                    when (RemoteManager.activeTargetType) {
                        RemoteManager.TargetDeviceType.WINDOWS_PC -> {
                            PcRemoteProtocol.sendMouseMove(dx, dy)
                        }
                        else -> {
                            controllerScope.launch {
                                Atrpv2Protocol.injectPointer(dx, dy, isAbsolute = false)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun setSensitivity(sensitivity: Float) {
        this.pointerSensitivity = sensitivity.coerceIn(0.2f, 4.0f)
        Log.d("BluetoothController", "Pointer sensitivity set to: $pointerSensitivity")
    }

    fun isBluetoothAvailable(): Boolean {
        return bluetoothManager != null && bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

    fun shutdown() {
        stopAirMouse()
        controllerScope.cancel()
    }
}

fun Context.getBluetoothController(): BluetoothController {
    return BluetoothController(this)
}
