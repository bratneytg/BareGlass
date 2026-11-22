package com.bareglass.app

/** Cómo vienen empaquetados los dos ojos dentro del video. */
enum class Layout(val label: String) {
    AUTO("Auto"),
    LR_FULL("Lado a lado"),
    LR_HALF("Lado a lado comprimido"),
    TB("Arriba-abajo"),
    MONO("2D")
}

object StereoDetect {
    private val lrHint = Regex("(^|[^a-z])(lr|sbs|3dh|hsbs|fsbs)([^a-z]|$)")
    private val tbHint = Regex("(^|[^a-z])(tb|ou|3dv|htb|hou)([^a-z]|$)")

    /** Adivina el formato por el nombre del archivo y la proporción del video. */
    fun detect(name: String, w: Int, h: Int): Layout {
        val n = name.lowercase()
        val aspect = if (h > 0) w.toFloat() / h.toFloat() else 1.78f
        if (tbHint.containsMatchIn(n)) return Layout.TB
        if (lrHint.containsMatchIn(n)) {
            val half = n.contains("half") || n.contains("hsbs")
            val full = n.contains("full") || n.contains("fsbs")
            return when {
                full -> Layout.LR_FULL
                half -> Layout.LR_HALF
                aspect in 1.6f..1.95f -> Layout.LR_HALF
                else -> Layout.LR_FULL
            }
        }
        return if (aspect >= 3.0f) Layout.LR_FULL else Layout.MONO
    }

    /** Proporción (ancho/alto) que debe tener la imagen de UN ojo al mostrarla. */
    fun eyeAspect(l: Layout, w: Int, h: Int): Float {
        if (w <= 0 || h <= 0) return 1.78f
        return when (l) {
            Layout.LR_FULL -> (w / 2f) / h
            Layout.TB -> {
                val a = w / (h / 2f)
                if (a <= 2.4f) a else w.toFloat() / h
            }
            else -> w.toFloat() / h
        }
    }
}
