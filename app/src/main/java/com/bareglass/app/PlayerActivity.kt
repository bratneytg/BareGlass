package com.bareglass.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** Visor: reproduce un archivo del móvil o un enlace de internet en VR (Cardboard), rojo/cian o 2D. */
class PlayerActivity : Activity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: StereoRenderer
    private lateinit var player: ExoPlayer
    private lateinit var overlay: FrameLayout
    private lateinit var settingsPanel: ScrollView
    private lateinit var tvTitle: TextView
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView
    private lateinit var seek: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var btnMode: Button
    private lateinit var btnFormat: Button
    private lateinit var btnSwap: Button
    private lateinit var btnLens: Button
    private lateinit var btnCurve: Button
    private lateinit var lblSep: TextView
    private lateinit var lblZoom: TextView
    private lateinit var lblHeight: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var uriString = ""
    private var name = "video"
    private var vw = 0
    private var vh = 0
    private var layoutPref = Layout.AUTO
    private var live = false
    private var seeking = false
    private var overlayVisible = true
    private val modeNames = arrayOf("VR · Cardboard", "Rojo / cian", "2D")
    private val lensNames = arrayOf("apagada", "suave", "normal", "fuerte")

    private val prefs by lazy { getSharedPreferences("player", MODE_PRIVATE) }
    private var tracker: HeadTracker? = null

    private val ticker = object : Runnable {
        override fun run() {
            val d = player.duration
            val p = player.currentPosition
            if (d > 0 && seek.max != d.toInt()) seek.max = d.toInt()
            if (!seeking) seek.progress = p.toInt()
            tvPos.text = Ui.fmtTime(p)
            tvDur.text = Ui.fmtTime(d)
            btnPlay.text = if (player.isPlaying) "⏸" else "▶"
            renderer.playing = player.isPlaying
            renderer.posMs = p
            renderer.durMs = if (d > 0) d else 0L
            handler.postDelayed(this, 300)
        }
    }
    private val autoHide = Runnable { if (player.isPlaying) showOverlay(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri = intent.data ?: run {
            finish()
            return
        }
        uriString = uri.toString()
        name = intent.getStringExtra("name") ?: displayName(uri)
        live = intent.getBooleanExtra("live", false)
        if (!live) Library.touch(this, uriString, name)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        renderer = StereoRenderer(
            onSurfaceReady = { s: Surface -> runOnUiThread { if (!isFinishing) player.setVideoSurface(s) } },
            requestRender = { glView.requestRender() }
        )
        renderer.viewMode = prefs.getInt("mode", StereoRenderer.MODE_VR)
        renderer.swapEyes = prefs.getBoolean("swap", false)
        renderer.separation = prefs.getFloat("sep2", -0.08f)
        renderer.zoom = prefs.getFloat("zoom", 1f)
        renderer.lensLevel = prefs.getInt("lens", 2)
        renderer.curved = prefs.getBoolean("curve", true)
        renderer.heightScale = prefs.getFloat("hscale", 1.35f)
        val ht = HeadTracker(this)
        tracker = ht
        renderer.headTracker = if (ht.available) ht else null
        renderer.onGaze = { id, frac -> runOnUiThread { handleGaze(id, frac) } }
        layoutPref = Layout.values()[prefs.getInt("layout", 0).coerceIn(0, Layout.values().size - 1)]
        if (live) {
            // Enlace de internet: el usuario elige el formato (2D por defecto: se muestra igual en los dos ojos).
            layoutPref = Layout.values()[intent.getIntExtra("layout", Layout.MONO.ordinal).coerceIn(0, Layout.values().size - 1)]
            if (intent.getBooleanExtra("vr", false)) renderer.viewMode = StereoRenderer.MODE_VR
        }

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        intent.getStringExtra("referer")?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                renderer.playing = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                vw = videoSize.width
                vh = videoSize.height
                applyLayout()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    Library.setPosition(this@PlayerActivity, uriString, 0L)
                    showOverlay(true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "No se pudo reproducir: " + error.errorCodeName, Toast.LENGTH_LONG).show()
                showOverlay(true)
            }
        })

        buildUi()
        applyMode()

        val saved = Library.find(this, uriString)?.positionMs ?: 0L
        val item = MediaItem.Builder().setUri(uri)
        if (live && intent.getBooleanExtra("hls", false)) item.setMimeType(MimeTypes.APPLICATION_M3U8)
        player.setMediaItem(item.build())
        if (saved > 3000L) player.seekTo(saved)
        player.prepare()
        player.playWhenReady = true

        if (renderer.viewMode == StereoRenderer.MODE_VR && !ht.available) {
            Toast.makeText(this, "Tu móvil no tiene giroscopio: el modo VR no podrá seguir tu cabeza.", Toast.LENGTH_LONG).show()
        }
    }

    /** Un control del panel VR se activó mirándolo fijamente. */
    private fun handleGaze(id: Int, frac: Float) {
        when (id) {
            GazePanel.BACK -> player.seekTo(maxOf(0L, player.currentPosition - 10_000L))
            GazePanel.FWD -> {
                val d = player.duration
                val t = player.currentPosition + 10_000L
                player.seekTo(if (d > 0) minOf(t, d - 500L) else t)
            }
            GazePanel.PLAY -> togglePlay()
            GazePanel.CENTER -> renderer.recenterRequested = true
            GazePanel.EXIT -> finish()
            GazePanel.SEEK -> {
                val d = player.duration
                if (d > 0) player.seekTo((d * frac).toLong())
            }
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (n != null) return n
                }
            }
        } catch (e: Exception) {
            // sin nombre disponible
        }
        return uri.lastPathSegment ?: "video"
    }

    // ------------------------------------------------------------------ interfaz

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (renderer.viewMode == StereoRenderer.MODE_VR) togglePlay() else showOverlay(!overlayVisible)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                showOverlay(true)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (renderer.viewMode == StereoRenderer.MODE_VR) return true
                val back = e.x < glView.width / 2f
                player.seekTo(maxOf(0L, player.currentPosition + (if (back) -10_000L else 10_000L)))
                return true
            }
        })
        glView.setOnTouchListener { _, ev ->
            gd.onTouchEvent(ev)
            true
        }

        overlay = FrameLayout(this)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        // barra superior
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setBackgroundColor(Color.parseColor("#99000000"))
        top.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6))
        top.addView(smallButton("←") { finish() })
        tvTitle = Ui.text(this, name, 15f, Color.WHITE, true)
        tvTitle.maxLines = 1
        tvTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        top.addView(tvTitle, Ui.lp(0, -2, left = Ui.dp(this, 10), right = Ui.dp(this, 10)).also { it.weight = 1f })
        top.addView(smallButton("⚙") { settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
        overlay.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // barra inferior
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.setBackgroundColor(Color.parseColor("#99000000"))
        bottom.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 10))

        val seekRow = LinearLayout(this)
        seekRow.orientation = LinearLayout.HORIZONTAL
        seekRow.gravity = Gravity.CENTER_VERTICAL
        tvPos = Ui.text(this, "00:00", 12f, Color.WHITE)
        tvDur = Ui.text(this, "--:--", 12f, Color.WHITE)
        seek = SeekBar(this)
        seek.progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACC)
        seek.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.ACC)
        seek.max = 1000
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) tvPos.text = Ui.fmtTime(progress.toLong())
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                seeking = true
                handler.removeCallbacks(autoHide)
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                seeking = false
                player.seekTo((sb?.progress ?: 0).toLong())
                scheduleHide()
            }
        })
        seekRow.addView(tvPos)
        seekRow.addView(seek, Ui.lp(0, -2, left = Ui.dp(this, 8), right = Ui.dp(this, 8)).also { it.weight = 1f })
        seekRow.addView(tvDur)
        bottom.addView(seekRow, Ui.lp(-1, -2))

        val ctrl = LinearLayout(this)
        ctrl.orientation = LinearLayout.HORIZONTAL
        ctrl.gravity = Gravity.CENTER
        ctrl.addView(smallButton("⏪ 10") { player.seekTo(maxOf(0L, player.currentPosition - 10_000L)) })
        btnPlay = smallButton("⏸") { togglePlay() }
        ctrl.addView(btnPlay, Ui.lp(Ui.dp(this, 90), -2, left = Ui.dp(this, 16), right = Ui.dp(this, 16)))
        ctrl.addView(smallButton("10 ⏩") { player.seekTo(player.currentPosition + 10_000L) })
        bottom.addView(ctrl, Ui.lp(-1, -2, top = Ui.dp(this, 4)))
        overlay.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        // panel de ajustes
        settingsPanel = ScrollView(this)
        settingsPanel.visibility = View.GONE
        settingsPanel.background = Ui.rounded(this, Color.parseColor("#EE1A0A10"), 16, Ui.LINE)
        val sp = LinearLayout(this)
        sp.orientation = LinearLayout.VERTICAL
        sp.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14))
        val gap = Ui.dp(this, 8)

        btnMode = Ui.button(this, "", false) {
            renderer.viewMode = (renderer.viewMode + 1) % 3
            prefs.edit().putInt("mode", renderer.viewMode).apply()
            applyMode()
        }
        btnFormat = Ui.button(this, "", false) {
            val all = Layout.values()
            layoutPref = all[(layoutPref.ordinal + 1) % all.size]
            prefs.edit().putInt("layout", layoutPref.ordinal).apply()
            applyLayout()
        }
        btnSwap = Ui.button(this, "", false) {
            renderer.swapEyes = !renderer.swapEyes
            prefs.edit().putBoolean("swap", renderer.swapEyes).apply()
            refreshTexts()
            glView.requestRender()
        }
        btnLens = Ui.button(this, "", false) {
            renderer.lensLevel = (renderer.lensLevel + 1) % lensNames.size
            prefs.edit().putInt("lens", renderer.lensLevel).apply()
            refreshTexts()
            glView.requestRender()
        }
        btnCurve = Ui.button(this, "", false) {
            renderer.curved = !renderer.curved
            prefs.edit().putBoolean("curve", renderer.curved).apply()
            refreshTexts()
            glView.requestRender()
        }
        sp.addView(btnMode, Ui.lp(-1, -2, bottom = gap))
        sp.addView(btnFormat, Ui.lp(-1, -2, bottom = gap))
        sp.addView(btnSwap, Ui.lp(-1, -2, bottom = gap))
        sp.addView(btnLens, Ui.lp(-1, -2, bottom = gap))
        sp.addView(btnCurve, Ui.lp(-1, -2, bottom = gap))

        lblSep = Ui.text(this, "", 13f, Ui.MUT)
        sp.addView(lblSep)
        val sepBar = SeekBar(this)
        sepBar.max = 200
        sepBar.progress = ((renderer.separation / 0.0025f) + 100f).toInt().coerceIn(0, 200)
        sepBar.progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACC)
        sepBar.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.ACC)
        sepBar.setOnSeekBarChangeListener(simpleSeek { p ->
            renderer.separation = (p - 100) * 0.0025f
            prefs.edit().putFloat("sep2", renderer.separation).apply()
            refreshTexts()
            glView.requestRender()
        })
        sp.addView(sepBar, Ui.lp(-1, -2, bottom = gap))

        lblZoom = Ui.text(this, "", 13f, Ui.MUT)
        sp.addView(lblZoom)
        val zoomBar = SeekBar(this)
        zoomBar.max = 200
        zoomBar.progress = ((renderer.zoom - 0.5f) * 100f).toInt().coerceIn(0, 200)
        zoomBar.progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACC)
        zoomBar.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.ACC)
        zoomBar.setOnSeekBarChangeListener(simpleSeek { p ->
            renderer.zoom = 0.5f + p / 100f
            prefs.edit().putFloat("zoom", renderer.zoom).apply()
            refreshTexts()
            glView.requestRender()
        })
        sp.addView(zoomBar, Ui.lp(-1, -2, bottom = gap))

        lblHeight = Ui.text(this, "", 13f, Ui.MUT)
        sp.addView(lblHeight)
        val heightBar = SeekBar(this)
        heightBar.max = 120
        heightBar.progress = ((renderer.heightScale - 1f) * 100f).toInt().coerceIn(0, 120)
        heightBar.progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACC)
        heightBar.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.ACC)
        heightBar.setOnSeekBarChangeListener(simpleSeek { p ->
            renderer.heightScale = 1f + p / 100f
            prefs.edit().putFloat("hscale", renderer.heightScale).apply()
            refreshTexts()
            glView.requestRender()
        })
        sp.addView(heightBar, Ui.lp(-1, -2, bottom = gap))

        val hint = Ui.text(
            this,
            "En modo VR baja la vista (mirando hacia abajo, hacia donde sea) y aparece el botón «Menú»; mantén la mirada fija para abrirlo. " +
                "Con el menú abierto verás un punto para apuntar; lo mismo para cada control (se rellena una barrita). Se cierra solo a los 5 s o con ✕. " +
                "Tocar la pantalla (o el botón del visor) pausa o reanuda. Mantén pulsado para ver este menú.",
            12f, Ui.MUT
        )
        sp.addView(hint, Ui.lp(-1, -2, top = gap))
        settingsPanel.addView(sp)
        val spLp = FrameLayout.LayoutParams(Ui.dp(this, 300), -2, Gravity.END or Gravity.CENTER_VERTICAL)
        spLp.setMargins(0, Ui.dp(this, 70), Ui.dp(this, 12), Ui.dp(this, 90))
        overlay.addView(settingsPanel, spLp)

        setContentView(root)
        refreshTexts()
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button {
        val b = Ui.button(this, label, false, onClick)
        b.minWidth = Ui.dp(this, 48)
        b.minimumWidth = Ui.dp(this, 48)
        b.minHeight = Ui.dp(this, 40)
        b.minimumHeight = Ui.dp(this, 40)
        return b
    }

    private fun simpleSeek(onChange: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }

    private fun effectiveLayout(): Layout =
        if (layoutPref == Layout.AUTO) StereoDetect.detect(name, vw, vh) else layoutPref

    private fun applyLayout() {
        renderer.videoWidth = vw
        renderer.videoHeight = vh
        renderer.layout = effectiveLayout()
        refreshTexts()
        glView.requestRender()
    }

    @Suppress("DEPRECATION")
    private fun applyMode() {
        val vr = renderer.viewMode == StereoRenderer.MODE_VR
        requestedOrientation = if (vr)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val tracked = vr && renderer.headTracker != null
        glView.renderMode = if (tracked) GLSurfaceView.RENDERMODE_CONTINUOUSLY else GLSurfaceView.RENDERMODE_WHEN_DIRTY
        if (tracked) {
            renderer.recenterRequested = true
            tracker?.start()
        } else {
            tracker?.stop()
        }
        refreshTexts()
        glView.requestRender()
        showOverlay(!vr)
    }

    private fun refreshTexts() {
        btnMode.text = "Modo: " + modeNames[renderer.viewMode.coerceIn(0, 2)]
        val eff = effectiveLayout()
        btnFormat.text = if (layoutPref == Layout.AUTO) "Formato: Auto (" + eff.label + ")" else "Formato: " + layoutPref.label
        btnSwap.text = "Intercambiar ojos: " + (if (renderer.swapEyes) "sí" else "no")
        btnCurve.text = "Pantalla VR: " + (if (renderer.curved) "curva (cóncava)" else "plana")
        btnLens.text = "Corrección de lente: " + lensNames[renderer.lensLevel.coerceIn(0, lensNames.size - 1)]
        lblSep.text = "Convergencia (negativo = imágenes más juntas): " + String.format("%+.0f", renderer.separation * 100f)
        lblHeight.text = "Altura de la pantalla (VR): " + String.format("%.0f%%", renderer.heightScale * 100f) + " · más alto = más imagen arriba y abajo (se estira)"
        lblZoom.text = "Zoom: " + String.format("%.0f%%", renderer.zoom * 100f)
    }

    private fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    private fun showOverlay(show: Boolean) {
        overlayVisible = show
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        handler.removeCallbacks(autoHide)
        if (show) scheduleHide() else settingsPanel.visibility = View.GONE
    }

    private fun scheduleHide() {
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, 4500L)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    // ------------------------------------------------------------------ ciclo de vida

    override fun onResume() {
        super.onResume()
        if (!::glView.isInitialized) return
        glView.onResume()
        hideSystemUi()
        if (renderer.viewMode == StereoRenderer.MODE_VR && renderer.headTracker != null) {
            renderer.recenterRequested = true
            tracker?.start()
        }
        handler.post(ticker)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(autoHide)
        tracker?.stop()
        if (::player.isInitialized) {
            Library.setPosition(this, uriString, player.currentPosition)
            player.pause()
        }
        if (::glView.isInitialized) glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            Library.setPosition(this, uriString, player.currentPosition)
            player.release()
        }
        if (::renderer.isInitialized) renderer.release()
        super.onDestroy()
    }
}
