package com.bareglass.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast

/** Muestra una pantalla «maqueta» definida en [Mockups]. */
class MockupActivity : Activity() {
    private val inputs = ArrayList<EditText>()
    private lateinit var key: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        key = intent.getStringExtra("key") ?: ""
        val s = Mockups.all[key]
        if (s == null) {
            finish()
            return
        }
        val (scroll, col) = Screens.page(this)
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 20))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = Ui.text(this, "←", 24f, Ui.GOLD, true)
        back.setPadding(0, 0, Ui.dp(this, 16), 0)
        back.setOnClickListener { finish() }
        head.addView(back)
        head.addView(Ui.text(this, s.icon + "  " + s.title, 22f, Ui.TXT, true).also { it.typeface = android.graphics.Typeface.SERIF })
        col.addView(head, Ui.lp(-1, -2, bottom = Ui.dp(this, 12)))

        val tag = Ui.text(this, if (s.premium) "★ Premium · 🧪 Maqueta" else "🧪 Maqueta · la mayoría de controles aún no hacen nada", 12f, Ui.GOLD)
        tag.background = Ui.rounded(this, Ui.CARD, 14, Ui.LINE)
        tag.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6))
        col.addView(tag, Ui.lp(-2, -2, bottom = Ui.dp(this, 10)))
        col.addView(Ui.text(this, s.intro, 14f, Ui.MUT), Ui.lp(-1, -2, bottom = Ui.dp(this, 16)))

        if (s.premium && Session.plan(this) != "premium") {
            val c = Screens.card(this)
            c.addView(Ui.text(this, "Esta función es de Premium", 15f, Ui.GOLD, true))
            c.addView(Ui.button(this, "Ver planes Premium", true) { startActivity(Intent(this, PremiumActivity::class.java)) }, Ui.lp(-1, -2, top = Ui.dp(this, 8)))
            col.addView(c, Ui.lp(-1, -2, bottom = Ui.dp(this, 12)))
        }

        val prefs = getSharedPreferences("mock_" + key, Context.MODE_PRIVATE)
        for (m in s.items) {
            when (m.kind) {
                MK.TOGGLE -> {
                    val sw = Switch(this)
                    sw.text = m.title
                    sw.setTextColor(Ui.TXT)
                    sw.textSize = 15f
                    sw.isChecked = prefs.getBoolean(m.title, false)
                    sw.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(m.title, v).apply() }
                    sw.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14))
                    sw.background = Ui.rounded(this, Ui.CARD, 14, Ui.LINE)
                    col.addView(sw, Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
                }
                MK.BUTTON -> col.addView(Ui.button(this, m.title, m.action.isNotEmpty()) { run(m) }, Ui.lp(-1, -2, top = Ui.dp(this, 4), bottom = Ui.dp(this, 8)))
                MK.ROW -> {
                    val r = LinearLayout(this)
                    r.orientation = LinearLayout.VERTICAL
                    r.background = Ui.rounded(this, Ui.CARD, 14, Ui.LINE)
                    val p = Ui.dp(this, 14)
                    r.setPadding(p, p, p, p)
                    r.addView(Ui.text(this, m.title, 15f, Ui.TXT, true))
                    if (m.sub.isNotEmpty() && m.action.isEmpty()) r.addView(Ui.text(this, m.sub, 12f, Ui.MUT))
                    else if (m.sub.isNotEmpty()) r.addView(Ui.text(this, m.sub, 12f, Ui.MUT))
                    r.setOnClickListener { _ -> run(m) }
                    col.addView(r, Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
                }
                MK.TEXT -> col.addView(Ui.text(this, m.title, 13f, Ui.MUT), Ui.lp(-1, -2, top = Ui.dp(this, 6), bottom = Ui.dp(this, 8)))
                MK.INPUT -> {
                    val e = Ui.input(this, m.title)
                    inputs.add(e)
                    col.addView(e, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
                }
            }
        }
        setContentView(scroll)
    }

    private fun run(it: MockItem) {
        val a = it.action
        when {
            a == "" -> {
                if (it.kind == MK.ROW && it.sub.isNotEmpty()) {
                    AlertDialog.Builder(this).setTitle(it.title).setMessage(it.sub).setPositiveButton("Vale", null).show()
                } else {
                    Toast.makeText(this, "Maqueta: aquí irá «" + it.title + "»", Toast.LENGTH_SHORT).show()
                }
            }
            a == "share" -> {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, "Prueba BareGlass, tu visor VR privado. Usa mi código BARE-7QX2 al registrarte.")
                startActivity(Intent.createChooser(i, "Invitar amigos"))
            }
            a == "premium" -> startActivity(Intent(this, PremiumActivity::class.java))
            a.startsWith("open:") -> startActivity(Intent(this, MockupActivity::class.java).putExtra("key", a.removePrefix("open:")))
            a.startsWith("legal:") -> {
                val t = Mockups.texts[a.removePrefix("legal:")] ?: "(sin texto)"
                AlertDialog.Builder(this).setTitle(it.title).setMessage(t).setPositiveButton("Cerrar", null).show()
            }
            a == "report" -> Toast.makeText(this, "Denuncia simulada enviada. Gracias.", Toast.LENGTH_LONG).show()
            a == "redeem" -> {
                val code = inputs.firstOrNull()?.text?.toString().orEmpty()
                val ok = Demo.active(this) && Demo.redeem(this, code)
                Toast.makeText(this, if (ok) "¡Código canjeado! Ya eres Premium ★" else "Código no válido o no has iniciado sesión (demo: BARE-DEMO)", Toast.LENGTH_LONG).show()
            }
            a == "export" -> {
                if (!Session.loggedIn(this)) {
                    Toast.makeText(this, "Inicia sesión para descargar tus datos", Toast.LENGTH_SHORT).show()
                    return
                }
                val app = applicationContext
                Api.async({ Api.export(app) }) { r ->
                    r.onSuccess { txt ->
                        val i = Intent(Intent.ACTION_SEND)
                        i.type = "text/plain"
                        i.putExtra(Intent.EXTRA_TEXT, txt)
                        startActivity(Intent.createChooser(i, "Mis datos"))
                    }.onFailure { e -> Toast.makeText(this, Api.message(e), Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}
