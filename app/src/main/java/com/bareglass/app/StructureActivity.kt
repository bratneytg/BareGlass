package com.bareglass.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast

/** MODO DEMO: mapa con todas las pantallas y funciones, para recorrer la estructura de la app. */
class StructureActivity : Activity() {
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, c) = Screens.page(this)
        col = c
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        col.removeAllViews()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = Ui.text(this, "←", 24f, Ui.GOLD, true)
        back.setPadding(0, 0, Ui.dp(this, 16), 0)
        back.setOnClickListener { finish() }
        head.addView(back)
        head.addView(Ui.text(this, "🧭  Mapa de la app", 22f, Ui.TXT, true).also { it.typeface = android.graphics.Typeface.SERIF })
        col.addView(head, Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
        col.addView(
            Ui.text(this, "Versión de demostración. Toca cualquier pantalla para abrirla.\n✅ funciona · 🟡 parcial · 🧪 maqueta (solo estructura)", 13f, Ui.MUT),
            Ui.lp(-1, -2, bottom = Ui.dp(this, 14))
        )

        val who = Screens.card(this)
        who.addView(Ui.text(this, if (Session.loggedIn(this)) "Sesión: ${Session.email(this)}  ·  " + (if (Session.plan(this) == "premium") "★ Premium" else "Gratis") else if (Session.guest(this)) "Sesión: invitado (sin cuenta)" else "Sesión: sin iniciar", 14f, Ui.GOLD, true))
        col.addView(who, Ui.lp(-1, -2, bottom = Ui.dp(this, 14)))

        section("Entrada")
        row("✅", "Entrar / crear cuenta", "Con cuentas de prueba locales") { startActivity(Intent(this, AuthActivity::class.java)) }
        row("✅", "Inicio", "Pegar enlace, guardar, abrir archivo") { startActivity(Intent(this, MainActivity::class.java)) }

        section("Visor VR")
        row("✅", "Video de ejemplo en VR", "Enlace de muestra (necesita internet)") { sample() }
        row("✅", "Abrir un video de mi móvil", "Elige un archivo y míralo en VR") {
            val i = Intent(this, MainActivity::class.java)
            startActivity(i)
            Toast.makeText(this, "Pulsa «Abrir un video de mi móvil»", Toast.LENGTH_LONG).show()
        }
        row("🟡", "Ajustes dentro del visor", "Mantén la mirada en el menú: separación, zoom, altura, curva") { sample() }

        section("Cuenta y Premium")
        row("✅", "Mi cuenta", "Plan, exportar datos, cerrar sesión, borrar") { startActivity(Intent(this, AccountActivity::class.java)) }
        row("🟡", "Planes Premium", "Pago simulado en la demo") { startActivity(Intent(this, PremiumActivity::class.java)) }
        mock("🧪", "redeem")
        mock("🧪", "invite")
        mock("🧪", "devices")
        mock("🧪", "backup")

        section("Biblioteca (Premium)")
        mock("🧪", "collections")
        mock("🧪", "profiles")

        section("Seguridad y legal")
        mock("🧪", "settings")
        mock("🧪", "pin")
        mock("🧪", "legal")
        mock("🧪", "report")

        section("Ayuda")
        mock("🧪", "help")
        mock("🧪", "about")

        section("Herramientas de la demo")
        row("🔑", "Entrar como usuario de prueba", Demo.EMAIL + " · plan gratis") { loginAs(Demo.EMAIL) }
        row("★", "Entrar como usuario Premium", Demo.EMAIL_PREMIUM + " · plan Premium") { loginAs(Demo.EMAIL_PREMIUM) }
        row("🚪", "Cerrar sesión", "") {
            val app = applicationContext
            Api.async({ Api.logout(app) }) {
                Session.setGuest(this, false)
                build()
            }
        }
        row("♻", "Reiniciar la demo", "Borra usuarios, enlaces y vuelve a la pantalla de entrada") {
            AlertDialog.Builder(this)
                .setMessage("Se borrarán los datos de prueba y la app volverá al principio.")
                .setPositiveButton("Reiniciar") { _, _ ->
                    Demo.reset(this)
                    val i = Intent(this, AuthActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(i)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun loginAs(email: String) {
        try {
            Demo.login(this, email, Demo.PASS)
            Session.setGuest(this, false)
            Toast.makeText(this, "Sesión iniciada como $email", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
        build()
    }

    private fun sample() {
        val i = Intent(this, PlayerActivity::class.java)
        i.data = Uri.parse(Demo.SAMPLE_VIDEO)
        i.putExtra("name", "Video de ejemplo")
        i.putExtra("live", true)
        i.putExtra("vr", true)
        i.putExtra("hls", false)
        i.putExtra("referer", "")
        i.putExtra("layout", Layout.MONO.ordinal)
        startActivity(i)
    }

    private fun mock(tag: String, key: String) {
        val s = Mockups.all[key] ?: return
        row(tag, s.icon + "  " + s.title + (if (s.premium) "  ★" else ""), s.intro) {
            startActivity(Intent(this, MockupActivity::class.java).putExtra("key", key))
        }
    }

    private fun section(t: String) {
        col.addView(Ui.text(this, t, 14f, Ui.GOLD, true), Ui.lp(-1, -2, top = Ui.dp(this, 14), bottom = Ui.dp(this, 8)))
    }

    private fun row(tag: String, title: String, sub: String, onClick: () -> Unit) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = Ui.rounded(this, Ui.CARD, 14, Ui.LINE)
        val p = Ui.dp(this, 14)
        r.setPadding(p, p, p, p)
        r.addView(Ui.text(this, tag, 18f, Ui.TXT), Ui.lp(-2, -2, right = Ui.dp(this, 12)))
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.addView(Ui.text(this, title, 15f, Ui.TXT, true))
        if (sub.isNotEmpty()) t.addView(Ui.text(this, sub, 12f, Ui.MUT))
        r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(Ui.text(this, "›", 20f, Ui.MUT))
        r.setOnClickListener { onClick() }
        col.addView(r, Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
    }
}
