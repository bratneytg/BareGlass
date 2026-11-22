package com.bareglass.app

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Recibe los fotogramas del reproductor en una textura externa (OES) y dibuja la vista estéreo:
 * VR (dos ojos con corrección de lente), rojo/cian (anaglifo) o 2D (solo un ojo).
 */
class StereoRenderer(
    private val onSurfaceReady: (Surface) -> Unit,
    private val requestRender: () -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        const val MODE_VR = 0
        const val MODE_ANAGLYPH = 1
        const val MODE_2D = 2

        private const val VS = """
attribute vec2 aPos;
varying vec2 vPos;
void main() {
  vPos = aPos;
  gl_Position = vec4(aPos, 0.0, 1.0);
}
"""
        private const val FS = """
#extension GL_OES_EGL_image_external : require
precision highp float;
varying vec2 vPos;
uniform samplerExternalOES uTex;
uniform mat4 uTexMat;
uniform vec4 uRectA;
uniform vec4 uRectB;
uniform vec2 uFit;
uniform vec2 uShifts;
uniform vec2 uK;
uniform int uMode;

vec3 fetchSafe(vec4 r, vec2 p) {
  if (abs(p.x) > 1.0 || abs(p.y) > 1.0) {
    return vec3(0.0);
  }
  vec2 uv = vec2(p.x * 0.5 + 0.5, 0.5 - p.y * 0.5);
  uv = clamp(uv, 0.0, 1.0);
  vec2 vu = r.xy + uv * r.zw;
  vec2 t = (uTexMat * vec4(vu.x, 1.0 - vu.y, 0.0, 1.0)).xy;
  return texture2D(uTex, t).rgb;
}

void main() {
  vec2 q = vPos;
  float r2 = dot(q, q);
  q = q * (1.0 + uK.x * r2 + uK.y * r2 * r2);
  vec2 p = q / uFit;
  vec2 pa = vec2(p.x + uShifts.x, p.y);
  vec2 pb = vec2(p.x + uShifts.y, p.y);
  vec3 a = fetchSafe(uRectA, pa);
  if (uMode == 1) {
    vec3 b = fetchSafe(uRectB, pb);
    gl_FragColor = vec4(a.r, b.g, b.b, 1.0);
  } else {
    gl_FragColor = vec4(a, 1.0);
  }
}
"""
        // Fuerza de la corrección de lente para cada nivel (0 = apagada)
        val LENS_LEVELS = floatArrayOf(0f, 0.6f, 1f, 1.6f)

        // Pantalla de cine virtual fija en el espacio + menú de controles que se activa con la mirada.
        private const val FS_SCENE = """
#extension GL_OES_EGL_image_external : require
precision highp float;
varying vec2 vPos;
uniform samplerExternalOES uTex;
uniform sampler2D uPanel;
uniform mat4 uTexMat;
uniform vec4 uRect;
uniform mat3 uRot;
uniform vec2 uTan;
uniform vec2 uK;
uniform vec2 uScr;
uniform vec2 uCyl;
uniform float uCurved;
uniform vec4 uPan;
uniform vec2 uMenu;
uniform float uShowMenu;
uniform vec2 uAspect;
uniform float uPointer;
uniform float uShift;

vec3 video(vec2 p) {
  vec2 uv = vec2(p.x * 0.5 + 0.5, 0.5 - p.y * 0.5);
  uv = clamp(uv, 0.0, 1.0);
  vec2 vu = uRect.xy + uv * uRect.zw;
  vec2 t = (uTexMat * vec4(vu.x, 1.0 - vu.y, 0.0, 1.0)).xy;
  return texture2D(uTex, t).rgb;
}

void main() {
  vec2 q = vPos;
  float r2 = dot(q, q);
  vec2 d = q * (1.0 + uK.x * r2 + uK.y * r2 * r2);
  vec3 ray = uRot * vec3(d * uTan, -1.0);
  vec3 col = vec3(0.015, 0.015, 0.03);
  {
    vec2 p = vec2(2.0);
    if (uCurved > 0.5) {
      // pantalla cóncava: un trozo de cilindro alrededor de ti (cada punto está a la misma distancia).
      // Con zoom alto la pantalla se abre más y te envuelve (hasta rodearte por los lados).
      float th = atan(ray.x, -ray.z);
      float yy = ray.y / max(length(ray.xz), 0.0001);
      p = vec2(th / uCyl.x, yy / uCyl.y);
    } else if (ray.z < -0.0001) {
      p = (ray.xy / (-ray.z)) / uScr;
    }
    if (abs(p.x) <= 1.0 && abs(p.y) <= 1.0) {
      col = video(vec2(p.x + uShift, p.y));
    }
  }
  if (uShowMenu > 0.5) {
    // el menú vive en un marco que solo gira en horizontal hasta donde estabas mirando al bajar la vista
    vec3 rm = vec3(ray.x * uMenu.x + ray.z * uMenu.y, ray.y, -ray.x * uMenu.y + ray.z * uMenu.x);
    if (rm.z < -0.0001) {
      vec2 s2 = rm.xy / (-rm.z);
      vec2 pp = (s2 - uPan.xy) / uPan.zw;
      if (abs(pp.x) <= 1.0 && abs(pp.y) <= 1.0) {
        vec4 c = texture2D(uPanel, vec2(pp.x * 0.5 + 0.5, 0.5 - pp.y * 0.5));
        col = c.rgb + (1.0 - c.a) * col;
      }
    }
  }
  if (uPointer > 0.5) {
    float rr = length(vPos * uAspect);
    if (rr < 0.012) {
      col = vec3(0.13, 0.83, 0.93);
    } else if (rr < 0.018) {
      col = vec3(1.0);
    }
  }
  gl_FragColor = vec4(col, 1.0);
}
"""
        // Geometría de la escena (en unidades de tangente a distancia 1)
        private const val SCREEN_HALF_W = 1.05f
        private const val CURVE_HALF_ANGLE = 0.90f // medio ángulo de la pantalla curva, en radianes (~52°)
        private const val TAN_V = 0.95f
        private val PANEL = floatArrayOf(0f, -0.80f, 0.62f, 0.155f) // cx, cy, medio ancho, medio alto
        private const val DWELL_NS = 1_200_000_000L
        private const val MENU_NS = 900_000_000L
        private const val SEEK_NS = 800_000_000L
        private const val MENU_IDLE_NS = 5_000_000_000L
    }

    @Volatile var viewMode: Int = MODE_VR
    @Volatile var layout: Layout = Layout.MONO
    @Volatile var swapEyes: Boolean = false
    @Volatile var separation: Float = 0f
    @Volatile var zoom: Float = 1f
    @Volatile var lensLevel: Int = 2
    @Volatile var videoWidth: Int = 0
    @Volatile var videoHeight: Int = 0

    // --- seguimiento de cabeza y controles por mirada (modo VR)
    @Volatile var headTracker: HeadTracker? = null
    @Volatile var playing: Boolean = true
    @Volatile var posMs: Long = 0L
    @Volatile var durMs: Long = 0L
    @Volatile var recenterRequested: Boolean = true
    /** Pantalla cóncava (cilindro alrededor del espectador) en lugar de plana. */
    @Volatile var curved: Boolean = true
    /** Estira la pantalla en vertical (1 = proporción original del video) para que llene más el campo de visión. */
    @Volatile var heightScale: Float = 1.35f
    /** Se llama (desde el hilo de dibujo) cuando una mirada fija activa un control: (botón, fracción 0..1 si es la barra). */
    @Volatile var onGaze: ((Int, Float) -> Unit)? = null

    private val panel = GazePanel()
    private var sceneProgram = 0
    private var sAPos = 0
    private var sTex = 0
    private var sPanel = 0
    private var sTexMat = 0
    private var sRect = 0
    private var sRot = 0
    private var sTan = 0
    private var sK = 0
    private var sScr = 0
    private var sCyl = 0
    private var sCurved = 0
    private var sPan = 0
    private var sMenu = 0
    private var sShowMenu = 0
    private var sAspect = 0
    private var sPointer = 0
    private var sShift = 0
    private var panelTex = 0
    private val rawM = FloatArray(16)
    private val rot = FloatArray(9)
    private var yaw0 = 0f
    private var hoverId = GazePanel.NONE
    private var hoverU = 0f
    private var hoverStart = 0L
    private var armed = true
    private var lastActivation = 0L
    private var dwellFrac = 0f
    private var drawnHover = -1
    private var drawnPlaying = false
    private var drawnPosSec = -1L
    private var drawnU = -100f
    private var drawnOpen = false
    private var drawnDwell = 0f
    private var lastDrawNs = 0L
    private var menuOpen = false
    private var menuActivity = 0L
    private var menuYaw = 0f
    private var menuVisible = false

    private var program = 0
    private var texId = 0
    private var aPos = 0
    private var uTexMat = 0
    private var uRectA = 0
    private var uRectB = 0
    private var uFit = 0
    private var uShifts = 0
    private var uK = 0
    private var uMode = 0
    private var uTex = 0

    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private val frameAvailable = AtomicBoolean(false)
    private val texMatrix = FloatArray(16)
    private var viewW = 1
    private var viewH = 1

    private val quad: FloatBuffer =
        ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    init {
        Matrix.setIdentityM(texMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram()
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uTexMat = GLES20.glGetUniformLocation(program, "uTexMat")
        uRectA = GLES20.glGetUniformLocation(program, "uRectA")
        uRectB = GLES20.glGetUniformLocation(program, "uRectB")
        uFit = GLES20.glGetUniformLocation(program, "uFit")
        uShifts = GLES20.glGetUniformLocation(program, "uShifts")
        uK = GLES20.glGetUniformLocation(program, "uK")
        uMode = GLES20.glGetUniformLocation(program, "uMode")
        uTex = GLES20.glGetUniformLocation(program, "uTex")

        sceneProgram = buildProgram(FS_SCENE)
        sAPos = GLES20.glGetAttribLocation(sceneProgram, "aPos")
        sTex = GLES20.glGetUniformLocation(sceneProgram, "uTex")
        sPanel = GLES20.glGetUniformLocation(sceneProgram, "uPanel")
        sTexMat = GLES20.glGetUniformLocation(sceneProgram, "uTexMat")
        sRect = GLES20.glGetUniformLocation(sceneProgram, "uRect")
        sRot = GLES20.glGetUniformLocation(sceneProgram, "uRot")
        sTan = GLES20.glGetUniformLocation(sceneProgram, "uTan")
        sK = GLES20.glGetUniformLocation(sceneProgram, "uK")
        sScr = GLES20.glGetUniformLocation(sceneProgram, "uScr")
        sCyl = GLES20.glGetUniformLocation(sceneProgram, "uCyl")
        sCurved = GLES20.glGetUniformLocation(sceneProgram, "uCurved")
        sPan = GLES20.glGetUniformLocation(sceneProgram, "uPan")
        sMenu = GLES20.glGetUniformLocation(sceneProgram, "uMenu")
        sShowMenu = GLES20.glGetUniformLocation(sceneProgram, "uShowMenu")
        sAspect = GLES20.glGetUniformLocation(sceneProgram, "uAspect")
        sPointer = GLES20.glGetUniformLocation(sceneProgram, "uPointer")
        sShift = GLES20.glGetUniformLocation(sceneProgram, "uShift")

        val pt = IntArray(1)
        GLES20.glGenTextures(1, pt, 0)
        panelTex = pt[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        drawnHover = -1 // fuerza a volver a subir el panel

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        texId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surface?.release()
        surfaceTexture?.release()
        val st = SurfaceTexture(texId)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st
        val s = Surface(st)
        surface = s
        frameAvailable.set(false)
        onSurfaceReady(s)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = if (width > 0) width else 1
        viewH = if (height > 0) height else 1
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        frameAvailable.set(true)
        requestRender()
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture
        if (st != null && frameAvailable.getAndSet(false)) {
            try {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
            } catch (e: Exception) {
                // se ignora: se reintenta con el siguiente fotograma
            }
        }
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0 || videoWidth <= 0 || videoHeight <= 0) return

        val tracker = headTracker
        if (viewMode == MODE_VR && tracker != null && tracker.ready && sceneProgram != 0) {
            drawScene(tracker)
            return
        }

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uTexMat, 1, false, texMatrix, 0)

        val sep = separation
        val lensK = LENS_LEVELS[lensLevel.coerceIn(0, LENS_LEVELS.size - 1)]
        val leftIdx = if (swapEyes) 1 else 0
        val rightIdx = if (swapEyes) 0 else 1
        val lay = layout

        when (viewMode) {
            MODE_VR -> {
                val half = viewW / 2
                drawEye(0, 0, half, viewH, rect(lay, leftIdx), rect(lay, leftIdx), sep, sep, 0, lensK)
                drawEye(half, 0, viewW - half, viewH, rect(lay, rightIdx), rect(lay, rightIdx), -sep, -sep, 0, lensK)
            }
            MODE_ANAGLYPH ->
                drawEye(0, 0, viewW, viewH, rect(lay, leftIdx), rect(lay, rightIdx), sep, -sep, 1, 0f)
            else ->
                drawEye(0, 0, viewW, viewH, rect(lay, leftIdx), rect(lay, leftIdx), 0f, 0f, 0, 0f)
        }
    }

    // ------------------------------------------------------------------ escena VR con seguimiento de cabeza

    private fun drawScene(tracker: HeadTracker) {
        if (!tracker.copyMatrix(rawM)) return

        // Recentrar: la dirección en la que miras ahora pasa a ser "el frente" de la pantalla.
        if (recenterRequested) {
            val fx = -rawM[2]
            val fy = -rawM[6]
            if (Math.hypot(fx.toDouble(), fy.toDouble()) > 0.2) {
                yaw0 = Math.atan2(fx.toDouble(), fy.toDouble()).toFloat()
                recenterRequested = false
                menuOpen = false // el menú está en el marco anterior; se cierra al centrar
            }
        }

        // rot = Ry(yaw0) * G * M  (guardada por columnas para OpenGL). G: este/norte/arriba -> x/y/-z.
        val c = Math.cos(yaw0.toDouble()).toFloat()
        val s = Math.sin(yaw0.toDouble()).toFloat()
        for (col in 0 until 3) {
            val wx = rawM[col]
            val wy = rawM[4 + col]
            val wz = rawM[8 + col]
            val gx = wx
            val gy = wz
            val gz = -wy
            rot[col * 3] = gx * c + gz * s
            rot[col * 3 + 1] = gy
            rot[col * 3 + 2] = -gx * s + gz * c
        }

        updateGaze()

        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glUseProgram(sceneProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(sTex, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTex)
        GLES20.glUniform1i(sPanel, 1)
        GLES20.glUniformMatrix4fv(sTexMat, 1, false, texMatrix, 0)
        GLES20.glUniformMatrix3fv(sRot, 1, false, rot, 0)

        val lay = layout
        val ea = StereoDetect.eyeAspect(lay, videoWidth, videoHeight)
        val z = zoom
        val hw = SCREEN_HALF_W * z
        val hh = hw / ea * heightScale
        val half = viewW / 2
        val lensK = LENS_LEVELS[lensLevel.coerceIn(0, LENS_LEVELS.size - 1)]
        val leftIdx = if (swapEyes) 1 else 0
        val rightIdx = if (swapEyes) 0 else 1

        GLES20.glUniform2f(sScr, hw, hh)
        val thMax = (CURVE_HALF_ANGLE * z).coerceAtMost(2.4f)
        GLES20.glUniform2f(sCyl, thMax, thMax / ea * heightScale)
        GLES20.glUniform1f(sCurved, if (curved) 1f else 0f)
        GLES20.glUniform4f(sPan, PANEL[0], PANEL[1], PANEL[2], PANEL[3])
        GLES20.glUniform2f(sMenu, Math.cos(menuYaw.toDouble()).toFloat(), Math.sin(menuYaw.toDouble()).toFloat())
        GLES20.glUniform1f(sShowMenu, if (menuVisible) 1f else 0f)
        GLES20.glUniform1f(sPointer, if (menuOpen) 1f else 0f)
        GLES20.glUniform2f(sK, 0.12f * lensK, 0.06f * lensK)

        for (eye in 0..1) {
            val x = if (eye == 0) 0 else half
            val w = if (eye == 0) half else viewW - half
            val r = rect(lay, if (eye == 0) leftIdx else rightIdx)
            val va = w.toFloat() / viewH.toFloat()
            GLES20.glViewport(x, 0, w, viewH)
            GLES20.glUniform4f(sRect, r[0], r[1], r[2], r[3])
            GLES20.glUniform2f(sTan, TAN_V * va, TAN_V)
            GLES20.glUniform2f(sAspect, va, 1f)
            GLES20.glUniform1f(sShift, if (eye == 0) separation else -separation)
            GLES20.glEnableVertexAttribArray(sAPos)
            quad.position(0)
            GLES20.glVertexAttribPointer(sAPos, 2, GLES20.GL_FLOAT, false, 8, quad)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    /** Calcula qué control está mirando el usuario, lleva la cuenta de la mirada fija y sube el menú a la GPU. */
    private fun updateGaze() {
        // rayo del centro de la vista en el espacio de la escena = A * (0, 0, -1)
        val rx = -rot[6]
        val ry = -rot[7]
        val rz = -rot[8]
        var hit = GazePanel.NONE
        var u = 0f

        // El menú aparece al bajar la vista, justo debajo de hacia donde mirabas (no depende de dónde esté el video).
        if (ry > -0.25f && !menuOpen) {
            menuYaw = Math.atan2(rx.toDouble(), (-rz).toDouble()).toFloat()
        }
        menuVisible = menuOpen || ry < -0.30f
        val cs = Math.cos(menuYaw.toDouble()).toFloat()
        val sn = Math.sin(menuYaw.toDouble()).toFloat()
        val mx = rx * cs + rz * sn
        val mz = -rx * sn + rz * cs
        if (menuVisible && mz < -0.0001f) {
            val sx = mx / -mz
            val sy = ry / -mz
            val px = (sx - PANEL[0]) / PANEL[2]
            val py = (sy - PANEL[1]) / PANEL[3]
            if (Math.abs(px) <= 1f && Math.abs(py) <= 1f) {
                u = (px * 0.5f + 0.5f) * GazePanel.W
                val v = (0.5f - py * 0.5f) * GazePanel.H
                hit = panel.hit(u, v, menuOpen)
            }
        }

        val now = System.nanoTime()
        if (hit != hoverId) {
            hoverId = hit
            hoverStart = now
            armed = true
            dwellFrac = 0f
        }
        hoverU = u
        if (hit != GazePanel.NONE) menuActivity = now

        if (hit == GazePanel.NONE) {
            dwellFrac = 0f
        } else if (!armed) {
            dwellFrac = 0f
        } else {
            val need = when (hit) {
                GazePanel.SEEK -> SEEK_NS
                GazePanel.MENU -> MENU_NS
                else -> DWELL_NS
            }
            val since = now - maxOf(hoverStart, lastActivation)
            dwellFrac = (since.toFloat() / need.toFloat()).coerceIn(0f, 1f)
            if (since >= need) {
                lastActivation = now
                dwellFrac = 0f
                // -10 s / +10 s se repiten mientras sigas mirándolos; el resto exige apartar la vista
                armed = hit == GazePanel.BACK || hit == GazePanel.FWD
                when (hit) {
                    GazePanel.MENU -> {
                        menuOpen = true
                        menuActivity = now
                    }
                    GazePanel.CLOSE -> menuOpen = false
                    else -> {
                        val frac = if (hit == GazePanel.SEEK) panel.seekFraction(u) else 0f
                        onGaze?.invoke(hit, frac)
                    }
                }
            }
        }

        // El menú se cierra solo tras 5 s sin mirarlo
        if (menuOpen && now - menuActivity > MENU_IDLE_NS) {
            menuOpen = false
            hoverId = GazePanel.NONE
            dwellFrac = 0f
        }

        // Volver a dibujar el menú solo cuando algo cambia (con un máximo de ~20 por segundo para el progreso)
        val posSec = posMs / 1000L
        val p = playing
        var changed = hoverId != drawnHover || p != drawnPlaying || posSec != drawnPosSec || menuOpen != drawnOpen
        if (!changed && now - lastDrawNs > 50_000_000L) {
            if (Math.abs(dwellFrac - drawnDwell) > 0.01f) changed = true
            if (hoverId == GazePanel.SEEK && Math.abs(hoverU - drawnU) > 6f) changed = true
        }
        if (changed) {
            panel.draw(menuOpen, hoverId, hoverU, dwellFrac, p, posMs, durMs)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTex)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, panel.bitmap, 0)
            drawnHover = hoverId
            drawnPlaying = p
            drawnPosSec = posSec
            drawnU = hoverU
            drawnOpen = menuOpen
            drawnDwell = dwellFrac
            lastDrawNs = now
        }
    }

    private fun drawEye(
        x: Int, y: Int, w: Int, h: Int,
        a: FloatArray, b: FloatArray,
        shiftA: Float, shiftB: Float, mode: Int, lensK: Float
    ) {
        GLES20.glViewport(x, y, w, h)
        val ea = StereoDetect.eyeAspect(layout, videoWidth, videoHeight)
        val va = w.toFloat() / h.toFloat()
        var fx: Float
        var fy: Float
        if (ea > va) {
            fx = 1f
            fy = va / ea
        } else {
            fx = ea / va
            fy = 1f
        }
        fx *= zoom
        fy *= zoom
        GLES20.glUniform4f(uRectA, a[0], a[1], a[2], a[3])
        GLES20.glUniform4f(uRectB, b[0], b[1], b[2], b[3])
        GLES20.glUniform2f(uFit, fx, fy)
        GLES20.glUniform2f(uShifts, shiftA, shiftB)
        GLES20.glUniform2f(uK, 0.12f * lensK, 0.06f * lensK)
        GLES20.glUniform1i(uMode, mode)
        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Zona del video (x, y, ancho, alto; origen arriba-izquierda, 0..1) que corresponde a un ojo. */
    private fun rect(l: Layout, eye: Int): FloatArray = when (l) {
        Layout.LR_FULL, Layout.LR_HALF ->
            if (eye == 0) floatArrayOf(0f, 0f, 0.5f, 1f) else floatArrayOf(0.5f, 0f, 0.5f, 1f)
        Layout.TB ->
            if (eye == 0) floatArrayOf(0f, 0f, 1f, 0.5f) else floatArrayOf(0f, 0.5f, 1f, 0.5f)
        else -> floatArrayOf(0f, 0f, 1f, 1f)
    }

    fun release() {
        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
    }

    private fun buildProgram(fragment: String = FS): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, VS)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("No se pudo enlazar el shader: $log")
        }
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw RuntimeException("No se pudo compilar el shader: $log")
        }
        return s
    }
}
