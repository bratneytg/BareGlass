package com.bareglass.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Menú del modo VR. Se dibuja en un bitmap que el renderizador coloca en el espacio 3D.
 * Cerrado: solo un botón pequeño "Menú". Abierto: barra de progreso y los controles.
 * Se activa mirando el elemento unos instantes (sin tocar la pantalla).
 */
class GazePanel {
    companion object {
        const val W = 1024
        const val H = 256
        const val NONE = 0
        const val BACK = 1
        const val PLAY = 2
        const val FWD = 3
        const val CENTER = 4
        const val EXIT = 5
        const val CLOSE = 6
        const val SEEK = 7
        const val MENU = 8

        private const val BTN_Y0 = 112f
        private const val BTN_Y1 = 236f
        private const val BTN_W = 148f
        private const val BTN_GAP = 12f
        private const val BTN_X0 = 38f
        private const val BAR_X0 = 170f
        private const val BAR_X1 = 854f
        private const val BAR_Y = 52f

        // botón de menú (cerrado)
        private const val M_X0 = 362f
        private const val M_X1 = 662f
        private const val M_Y0 = 78f
        private const val M_Y1 = 178f
    }

    val bitmap: Bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private fun btnX(i: Int): Float = BTN_X0 + i * (BTN_W + BTN_GAP)

    /** u, v en píxeles del bitmap. Devuelve el elemento bajo ese punto según el menú esté abierto o no. */
    fun hit(u: Float, v: Float, open: Boolean): Int {
        if (!open) {
            // zona de puntería algo más grande que el dibujo para que sea fácil acertar
            if (u >= M_X0 - 60f && u <= M_X1 + 60f && v >= M_Y0 - 50f && v <= M_Y1 + 50f) return MENU
            return NONE
        }
        if (v in BTN_Y0..BTN_Y1) {
            for (i in 0 until 6) {
                val x0 = btnX(i)
                if (u >= x0 && u <= x0 + BTN_W) return i + 1
            }
        }
        if (v in 10f..96f && u >= BAR_X0 - 20f && u <= BAR_X1 + 20f) return SEEK
        return NONE
    }

    fun seekFraction(u: Float): Float = ((u - BAR_X0) / (BAR_X1 - BAR_X0)).coerceIn(0f, 1f)

    fun draw(open: Boolean, hover: Int, hoverU: Float, dwell: Float, playing: Boolean, posMs: Long, durMs: Long) {
        bitmap.eraseColor(Color.TRANSPARENT)
        if (open) drawOpen(hover, hoverU, dwell, playing, posMs, durMs) else drawClosed(hover, dwell)
    }

    private fun drawClosed(hover: Int, dwell: Float) {
        val hot = hover == MENU
        paint.style = Paint.Style.FILL
        paint.color = if (hot) Color.parseColor("#E62A0E18") else Color.parseColor("#991A0A10")
        rect.set(M_X0, M_Y0, M_X1, M_Y1)
        canvas.drawRoundRect(rect, 50f, 50f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = if (hot) Color.parseColor("#FFF4B6C2") else Color.parseColor("#88FFFFFF")
        canvas.drawRoundRect(rect, 50f, 50f, paint)
        if (hot && dwell > 0f) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFF4B6C2")
            rect.set(M_X0 + 30f, M_Y1 - 22f, M_X0 + 30f + (M_X1 - M_X0 - 60f) * dwell, M_Y1 - 12f)
            canvas.drawRoundRect(rect, 5f, 5f, paint)
        }
        // icono de menú (tres rayas) y texto
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        for (k in 0 until 3) {
            rect.set(M_X0 + 40f, M_Y0 + 26f + k * 18f, M_X0 + 82f, M_Y0 + 34f + k * 18f)
            canvas.drawRoundRect(rect, 4f, 4f, paint)
        }
        paint.textSize = 44f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Menú", M_X0 + 108f, M_Y0 + 62f, paint)
    }

    private fun drawOpen(hover: Int, hoverU: Float, dwell: Float, playing: Boolean, posMs: Long, durMs: Long) {
        // fondo
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#F212070C")
        rect.set(0f, 0f, W.toFloat(), H.toFloat())
        canvas.drawRoundRect(rect, 36f, 36f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.parseColor("#FF4A1A2A")
        rect.set(2f, 2f, W - 2f, H - 2f)
        canvas.drawRoundRect(rect, 34f, 34f, paint)

        // tiempos
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FFF7EDF0")
        paint.textSize = 32f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(Ui.fmtTime(posMs), 34f, BAR_Y + 11f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Ui.fmtTime(durMs), W - 34f, BAR_Y + 11f, paint)

        // barra de progreso
        paint.color = Color.parseColor("#FF3A1522")
        rect.set(BAR_X0, BAR_Y - 9f, BAR_X1, BAR_Y + 9f)
        canvas.drawRoundRect(rect, 9f, 9f, paint)
        val frac = if (durMs > 0L) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f
        val fillX = BAR_X0 + (BAR_X1 - BAR_X0) * frac
        paint.color = Color.parseColor("#FFE11D48")
        rect.set(BAR_X0, BAR_Y - 9f, fillX, BAR_Y + 9f)
        canvas.drawRoundRect(rect, 9f, 9f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(fillX, BAR_Y, 15f, paint)
        if (hover == SEEK) {
            val hx = hoverU.coerceIn(BAR_X0, BAR_X1)
            paint.color = Color.parseColor("#FFF4B6C2")
            canvas.drawCircle(hx, BAR_Y, 15f, paint)
            if (dwell > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                rect.set(hx - 26f, BAR_Y - 26f, hx + 26f, BAR_Y + 26f)
                canvas.drawArc(rect, -90f, 360f * dwell, false, paint)
                paint.style = Paint.Style.FILL
            }
        }

        // botones
        for (i in 0 until 6) {
            val id = i + 1
            val x0 = btnX(i)
            val hot = hover == id
            paint.style = Paint.Style.FILL
            paint.color = if (hot) (if (id == EXIT) Color.parseColor("#FFB4343C") else Color.parseColor("#FFE11D48"))
            else Color.parseColor("#FF3A1522")
            rect.set(x0, BTN_Y0, x0 + BTN_W, BTN_Y1)
            canvas.drawRoundRect(rect, 26f, 26f, paint)

            val cx = x0 + BTN_W / 2f
            val cy = (BTN_Y0 + BTN_Y1) / 2f - 6f
            paint.color = Color.WHITE
            when (id) {
                BACK -> label("-10 s", cx, cy, 40f)
                FWD -> label("+10 s", cx, cy, 40f)
                CENTER -> label("Centrar", cx, cy, 33f)
                EXIT -> label("Salir", cx, cy, 38f)
                CLOSE -> label("✕", cx, cy, 52f)
                PLAY -> {
                    if (playing) {
                        rect.set(cx - 24f, cy - 28f, cx - 7f, cy + 28f)
                        canvas.drawRoundRect(rect, 4f, 4f, paint)
                        rect.set(cx + 7f, cy - 28f, cx + 24f, cy + 28f)
                        canvas.drawRoundRect(rect, 4f, 4f, paint)
                    } else {
                        path.reset()
                        path.moveTo(cx - 18f, cy - 32f)
                        path.lineTo(cx + 32f, cy)
                        path.lineTo(cx - 18f, cy + 32f)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                }
            }
            if (hot && dwell > 0f) {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FFF4B6C2")
                rect.set(x0 + 14f, BTN_Y1 - 20f, x0 + 14f + (BTN_W - 28f) * dwell, BTN_Y1 - 10f)
                canvas.drawRoundRect(rect, 5f, 5f, paint)
            }
        }
    }

    private fun label(text: String, cx: Float, cy: Float, size: Float) {
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        val ty = cy - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, cx, ty, paint)
    }
}
