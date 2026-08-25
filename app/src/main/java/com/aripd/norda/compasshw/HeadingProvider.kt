package com.aripd.norda.compasshw

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.aripd.norda.core.geo.Geo
import com.aripd.norda.core.heading.Smoothing
import kotlin.math.sqrt

/**
 * Yön okuma (docs/MVP.md, 6.1): rotation vector → ekran yönüne remap →
 * zaman sabitli yumuşatma. Ham manyetometre de dinlenir — füzyon yönü verir
 * ama alanın BÜYÜKLÜĞÜNÜ vermez; bozulma algılama o büyüklüğe bakar.
 */
class HeadingProvider(
    context: Context,
    private val displayRotation: () -> Int,
    private val onHeading: (magneticDeg: Double, accuracyLow: Boolean, fieldMicroTesla: Double?) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val hasRotationVector: Boolean get() = rotationVector != null

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val smoothing = Smoothing(0.17)
    private var lastTimestampNs = 0L
    private var accuracyLow = false
    private var fieldMicroTesla: Double? = null

    fun start() {
        lastTimestampNs = 0L
        smoothing.reset()
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()
                fieldMicroTesla = sqrt(x * x + y * y + z * z)
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val (axisX, axisY) = when (displayRotation()) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
                SensorManager.getOrientation(remappedMatrix, orientation)
                val raw = Geo.normalizeDeg(Math.toDegrees(orientation[0].toDouble()))
                val dtSeconds =
                    if (lastTimestampNs == 0L) 0.02
                    else ((event.timestamp - lastTimestampNs) / 1e9).coerceIn(0.001, 0.5)
                lastTimestampNs = event.timestamp
                onHeading(smoothing.update(raw, dtSeconds), accuracyLow, fieldMicroTesla)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        accuracyLow = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
    }
}
