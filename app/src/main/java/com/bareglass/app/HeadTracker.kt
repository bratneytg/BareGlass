package com.bareglass.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager

/**
 * Orientación de la cabeza (del móvil dentro del visor) a partir del giroscopio / vector de rotación.
 * La matriz devuelta (4x4, por filas) transforma vectores del sistema de la pantalla
 * (x derecha, y arriba, z hacia el usuario) al sistema del mundo (x este, y norte, z arriba).
 */
class HeadTracker(private val ctx: Context) : SensorEventListener {

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val lock = Any()
    private val raw = FloatArray(16)
    private val remapped = FloatArray(16)
    private val result = FloatArray(16)
    @Volatile private var hasData = false

    val available: Boolean get() = sensor != null
    val ready: Boolean get() = hasData

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return wm.defaultDisplay.rotation
    }

    fun start() {
        val s = sensor ?: return
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm.unregisterListener(this)
        hasData = false
    }

    override fun onSensorChanged(ev: SensorEvent) {
        synchronized(lock) {
            SensorManager.getRotationMatrixFromVector(raw, ev.values)
            val ok = when (displayRotation()) {
                Surface.ROTATION_90 ->
                    SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remapped)
                Surface.ROTATION_270 ->
                    SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remapped)
                Surface.ROTATION_180 ->
                    SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remapped)
                else -> {
                    System.arraycopy(raw, 0, remapped, 0, 16)
                    true
                }
            }
            if (ok) {
                System.arraycopy(remapped, 0, result, 0, 16)
                hasData = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Copia la matriz actual (4x4 por filas) en [out]. Devuelve false si todavía no hay datos. */
    fun copyMatrix(out: FloatArray): Boolean {
        if (!hasData) return false
        synchronized(lock) { System.arraycopy(result, 0, out, 0, 16) }
        return true
    }
}
