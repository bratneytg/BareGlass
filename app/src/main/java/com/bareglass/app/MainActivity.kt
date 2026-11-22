package com.bareglass.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Inicio: pega un enlace o abre un archivo y míralo en VR; tus enlaces guardados; cuenta. */
class MainActivity : Activity() {

    private val layouts = arrayOf(
        Layout.MONO to "2D · pantalla gigante",
        Layout.LR_FULL to "3D lado a lado",
        Layout.TB to "3D arriba-abajo",
        Layout.LR_HALF to "3D lado a lado (comprimido)"
    )
    private var layoutIdx = 0

    private lateinit var urlField: EditText
    private lateinit var status: TextView
    private lateinit var linksBox: LinearLayout
    private lateinit var recentBox: LinearLayout
    private lateinit var promo: LinearLayout
    private lateinit var chip: TextView
    private lateinit var btnLayout: android.widget.Button
    private var working = false

    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!gate()) return
        layoutIdx = prefs.getInt("layoutIdx", 0).coerceIn(0, layouts.size - 1)

        val (scroll, col) = Screens.page(this)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val brandCol = LinearLayout(this)
        brandCol.orientation = LinearLayout.VERTICAL
        brandCol.addView(Ui.brand(this, 28f))
        brandCol.addView(Ui.text(this, "Mira sin filtros.", 12f, Ui.GOLD).also { it.typeface = android.graphics.Typeface.SERIF })
        header.addView(brandCol, LinearLayout.LayoutParams(0, -2, 1f))
        chip = Ui.text(this, "", 13f, Ui.TXT, true)
        chip.gravity = Gravity.CENTER
        chip.background = Ui.rounded(this, Ui.CARD, 20, Ui.LINE)
        chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8))
        chip.setOnClickListener { startActivity(Intent(this, if (Session.loggedIn(this)) AccountActivity::class.java else AuthActivity::class.java)) }
        header.addView(chip, LinearLayout.LayoutParams(-2, -2))
        col.addView(header, Ui.lp(-1, -2, bottom = Ui.dp(this, 22)))

        // Tarjeta principal: pegar enlace
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.background = Ui.hero(this, 22)
        val hp = Ui.dp(this, 18)
        hero.setPadding(hp, hp, hp, hp)
        hero.addView(Ui.text(this, "Pega un enlace.\nPonte las gafas.", 22f, Ui.TXT, true).also { it.typeface = android.graphics.Typeface.SERIF })
        hero.addView(Ui.text(this, "Enlace directo (.mp4, .webm, .m3u8) o una página con el video.", 12f, Ui.GOLD), Ui.lp(-1, -2, top = Ui.dp(this, 6), bottom = Ui.dp(this, 12)))
        urlField = Ui.input(this, "https://…")
        urlField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        hero.addView(urlField, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        btnLayout = Ui.button(this, "", false) {
            layoutIdx = (layoutIdx + 1) % layouts.size
            prefs.edit().putInt("layoutIdx", layoutIdx).apply()
            updateLayoutBtn()
        }
        hero.addView(btnLayout, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(Ui.button(this, "Pegar", false) { paste() }, Ui.lp(0, -2, right = Ui.dp(this, 8)).also { it.weight = 1f })
        row.addView(Ui.button(this, "Guardar", false) { saveCurrent() }, Ui.lp(0, -2, right = Ui.dp(this, 8)).also { it.weight = 1f })
        row.addView(Ui.button(this, "▶ Ver en VR", true) { playTyped() }, Ui.lp(0, -2).also { it.weight = 1.4f })
        hero.addView(row)
        status = Ui.text(this, "", 13f, Ui.GOLD)
        hero.addView(status, Ui.lp(-1, -2, top = Ui.dp(this, 8)))
        col.addView(hero, Ui.lp(-1, -2, bottom = Ui.dp(this, 12)))

        col.addView(Ui.button(this, "📂  Abrir un video de mi móvil", false) { openVideo() }, Ui.lp(-1, -2, bottom = Ui.dp(this, 12)))

        promo = Screens.card(this)
        col.addView(promo, Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))

        col.addView(Ui.text(this, "Mis enlaces", 15f, Ui.GOLD, true), Ui.lp(-1, -2, top = Ui.dp(this, 14), bottom = Ui.dp(this, 8)))
        linksBox = LinearLayout(this)
        linksBox.orientation = LinearLayout.VERTICAL
        col.addView(linksBox, Ui.lp(-1, -2))

        col.addView(Ui.text(this, "Recientes de mi móvil", 15f, Ui.GOLD, true), Ui.lp(-1, -2, top = Ui.dp(this, 18), bottom = Ui.dp(this, 8)))
        recentBox = LinearLayout(this)
        recentBox.orientation = LinearLayout.VERTICAL
        col.addView(recentBox, Ui.lp(-1, -2))

        updateLayoutBtn()
        setContentView(scroll)
    }

    /** Aviso +18 y cuenta/invitado antes de entrar. Devuelve false si se ha redirigido a otra pantalla. */
    private fun gate(): Boolean {
        if (!Session.adultConfirmed(this)) {
            startActivity(Intent(this, AgeGateActivity::class.java))
            finish()
            return false
        }
        if (!Session.loggedIn(this) && !Session.guest(this)) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return false
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!Session.adultConfirmed(this) || (!Session.loggedIn(this) && !Session.guest(this))) {
            gate()
            return
        }
        if (!::chip.isInitialized) return
        refreshChip()
        refreshPromo()
        refreshLinks()
        refreshRecents()
        if (Session.loggedIn(this)) {
            val app = applicationContext
            Api.async({ Api.me(app) }) { refreshChip(); refreshPromo() }
        }
    }

    private fun updateLayoutBtn() {
        btnLayout.text = "Formato: " + layouts[layoutIdx].second + "  ⟳"
    }

    private fun refreshChip() {
        chip.text = if (Session.loggedIn(this)) {
            val e = Session.email(this)
            (if (Session.plan(this) == "premium") "★ " else "") + e.substringBefore('@').take(12)
        } else "Entrar"
    }

    private fun refreshPromo() {
        promo.removeAllViews()
        if (Session.loggedIn(this)) {
            if (Session.plan(this) == "premium") {
                promo.visibility = android.view.View.GONE
                return
            }
            promo.visibility = android.view.View.VISIBLE
            promo.addView(Ui.text(this, "✦ Hazte Premium", 16f, Ui.GOLD, true))
            promo.addView(Ui.text(this, "Hasta 5.000 enlaces guardados y sincronizados en todos tus dispositivos.", 13f, Ui.MUT), Ui.lp(-1, -2, top = Ui.dp(this, 4), bottom = Ui.dp(this, 10)))
            promo.addView(Ui.button(this, "Ver Premium", true) { startActivity(Intent(this, AccountActivity::class.java)) })
        } else {
            promo.visibility = android.view.View.VISIBLE
            promo.addView(Ui.text(this, "Crea tu cuenta gratis", 16f, Ui.GOLD, true))
            promo.addView(Ui.text(this, "Guarda más enlaces y sincronízalos entre tus dispositivos. Tus enlaces se guardan cifrados.", 13f, Ui.MUT), Ui.lp(-1, -2, top = Ui.dp(this, 4), bottom = Ui.dp(this, 10)))
            promo.addView(Ui.button(this, "Crear cuenta o entrar", true) { startActivity(Intent(this, AuthActivity::class.java)) })
        }
    }

    private fun setStatus(s: String) {
        status.text = s
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (t.isEmpty()) Toast.makeText(this, "El portapapeles está vacío", Toast.LENGTH_SHORT).show() else urlField.setText(t)
    }

    private fun typedUrl(): String? {
        val t = urlField.text.toString().trim()
        if (t.isEmpty()) {
            setStatus("Pega primero un enlace")
            return null
        }
        return LinkResolver.normalize(t)
    }

    private fun playTyped() {
        val u = typedUrl() ?: return
        playLink(u)
    }

    private fun saveCurrent() {
        val u = typedUrl() ?: return
        val app = applicationContext
        setStatus("Guardando…")
        val title = LinkResolver.labelFor(u)
        Api.async({ LinkStore.add(app, u, title) }) { r ->
            r.onSuccess {
                setStatus("Guardado ✓")
                refreshLinks()
            }.onFailure { limitOrError(it) }
        }
    }

    private fun limitOrError(e: Throwable) {
        setStatus("")
        if (e is ApiError && e.code == 402) {
            AlertDialog.Builder(this)
                .setTitle("Límite de enlaces")
                .setMessage(e.message)
                .setPositiveButton(if (Session.loggedIn(this)) "Ver Premium" else "Crear cuenta") { _, _ ->
                    startActivity(Intent(this, if (Session.loggedIn(this)) AccountActivity::class.java else AuthActivity::class.java))
                }
                .setNegativeButton("Ahora no", null)
                .show()
        } else {
            setStatus(Api.message(e))
        }
    }

    /** Analiza el enlace (directo o página) y abre el visor. */
    private fun playLink(url: String) {
        if (working) return
        working = true
        setStatus("Buscando el video…")
        Api.async({ LinkResolver.resolve(url) }) { r ->
            working = false
            r.onSuccess { list ->
                setStatus("")
                if (list.size == 1) {
                    launch(list[0])
                } else {
                    val names = list.mapIndexed { i, p -> "Video ${i + 1} · ${p.label}" }.toTypedArray()
                    AlertDialog.Builder(this).setTitle("Encontré ${list.size} videos")
                        .setItems(names) { _, i -> launch(list[i]) }
                        .setNegativeButton("Cancelar", null).show()
                }
            }.onFailure {
                setStatus(it.message ?: "No se pudo abrir el enlace")
            }
        }
    }

    private fun launch(p: Playable) {
        val i = Intent(this, PlayerActivity::class.java)
        i.data = Uri.parse(p.url)
        i.putExtra("name", p.label)
        i.putExtra("live", true)
        i.putExtra("vr", true)
        i.putExtra("hls", p.hls)
        i.putExtra("referer", p.referer)
        i.putExtra("layout", layouts[layoutIdx].first.ordinal)
        startActivity(i)
    }

    private fun refreshLinks() {
        val app = applicationContext
        Api.async({ LinkStore.list(app) }) { r ->
            linksBox.removeAllViews()
            r.onFailure {
                linksBox.addView(Ui.text(this, Api.message(it), 13f, Ui.ERR))
            }.onSuccess { items ->
                val cap = LinkStore.limit(this)
                if (items.isEmpty()) {
                    linksBox.addView(Ui.text(this, "Aún no has guardado ningún enlace. Pega uno arriba y pulsa «Guardar».", 13f, Ui.MUT))
                    return@onSuccess
                }
                linksBox.addView(Ui.text(this, "${items.size} de $cap", 12f, Ui.MUT), Ui.lp(-1, -2, bottom = Ui.dp(this, 6)))
                for (l in items) linksBox.addView(linkRow(l), Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
            }
        }
    }

    private fun linkRow(l: SavedLink): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Ui.rounded(this, Ui.CARD, 14, Ui.LINE)
        val p = Ui.dp(this, 14)
        row.setPadding(p, p, p, p)
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val title = if (l.title.isNotBlank()) l.title else LinkResolver.labelFor(l.url)
        texts.addView(Ui.text(this, title, 15f, Ui.TXT, true).also { it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.END })
        texts.addView(Ui.text(this, l.url, 11f, Ui.MUT).also { it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.END })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val del = Ui.text(this, "✕", 18f, Ui.MUT)
        del.setPadding(Ui.dp(this, 12), 0, 0, 0)
        del.setOnClickListener {
            AlertDialog.Builder(this).setMessage("¿Quitar este enlace de tu lista?")
                .setPositiveButton("Quitar") { _, _ ->
                    val app = applicationContext
                    Api.async({ LinkStore.remove(app, l.id) }) { r ->
                        r.onFailure { setStatus(Api.message(it)) }
                        refreshLinks()
                    }
                }.setNegativeButton("Cancelar", null).show()
        }
        row.addView(del)
        row.setOnClickListener { playLink(l.url) }
        return row
    }

    private fun refreshRecents() {
        recentBox.removeAllViews()
        val items = Library.load(this)
        if (items.isEmpty()) {
            recentBox.addView(Ui.text(this, "Aquí aparecerán los videos que abras desde tu móvil.", 13f, Ui.MUT))
            return
        }
        for (e in items) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.background = Ui.rounded(this, Ui.CARD, 14, Ui.LINE)
            val p = Ui.dp(this, 14)
            row.setPadding(p, p, p, p)
            row.addView(Ui.text(this, e.name, 15f, Ui.TXT, true).also { it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(Ui.text(this, if (e.positionMs > 3000L) "Continuar en " + Ui.fmtTime(e.positionMs) else "Sin empezar", 12f, Ui.MUT))
            row.setOnClickListener {
                val i = Intent(this, PlayerActivity::class.java)
                i.data = Uri.parse(e.uri)
                i.putExtra("name", e.name)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(i)
            }
            row.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setMessage("¿Quitar «" + e.name + "» de la lista? (el archivo no se borra)")
                    .setPositiveButton("Quitar") { _, _ ->
                        Library.remove(this, e.uri)
                        refreshRecents()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                true
            }
            recentBox.addView(row, Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
        }
    }

    @Suppress("DEPRECATION")
    private fun openVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "video/*"
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(i, 11)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11 && resultCode == RESULT_OK) {
            val u = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // algunos proveedores no permiten acceso persistente
            }
            var name = "video"
            try {
                contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getString(0)
                        if (n != null) name = n
                    }
                }
            } catch (e: Exception) {
                // sin nombre
            }
            val i = Intent(this, PlayerActivity::class.java)
            i.data = u
            i.putExtra("name", name)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(i)
        }
    }
}
