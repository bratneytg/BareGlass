package com.bareglass.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast

/** Planes y precios. En la demo el pago se simula; con servidor real abre la página de pago. */
class PremiumActivity : Activity() {
    private lateinit var col: LinearLayout
    private lateinit var status: android.widget.TextView

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
        val premium = Session.plan(this) == "premium"

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = Ui.text(this, "←", 24f, Ui.GOLD, true)
        back.setPadding(0, 0, Ui.dp(this, 16), 0)
        back.setOnClickListener { finish() }
        head.addView(back)
        head.addView(Ui.text(this, "★  BareGlass Premium", 22f, Ui.TXT, true).also { it.typeface = android.graphics.Typeface.SERIF })
        col.addView(head, Ui.lp(-1, -2, bottom = Ui.dp(this, 14)))

        val now = Screens.card(this)
        now.addView(Ui.text(this, if (premium) "Tu plan: ★ Premium" else "Tu plan: Gratis", 16f, Ui.GOLD, true))
        now.addView(Ui.text(this, if (Session.loggedIn(this)) Session.email(this) else "Sin cuenta: crea una para suscribirte", 12f, Ui.MUT))
        col.addView(now, Ui.lp(-1, -2, bottom = Ui.dp(this, 14)))

        val feat = Screens.card(this)
        for (f in listOf(
            "Hasta 5.000 enlaces guardados (gratis: 15)",
            "Sincronización en todos tus dispositivos",
            "Colecciones y perfiles de gafas (próximamente)",
            "Copia de seguridad cifrada (próximamente)",
            "Soporte prioritario"
        )) feat.addView(Ui.text(this, "✔  $f", 14f, Ui.TXT), Ui.lp(-1, -2, bottom = Ui.dp(this, 6)))
        col.addView(feat, Ui.lp(-1, -2, bottom = Ui.dp(this, 14)))

        if (!premium) {
            plan("Mensual", "4,99 €", "al mes · cancela cuando quieras", false)
            plan("Anual", "29,99 €", "al año · ≈ 2,50 €/mes · el más elegido", true)
            plan("De por vida", "59,99 €", "pago único · oferta limitada", false)
        } else {
            col.addView(Ui.text(this, "Gracias por apoyar BareGlass ★", 15f, Ui.GOLD, true), Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
            if (Demo.active(this)) {
                col.addView(Ui.button(this, "Volver al plan gratis (simulado)", false) {
                    Demo.setPlan(this, "free")
                    build()
                }, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
            }
        }
        col.addView(Ui.button(this, "🎟  Tengo un código", false) {
            startActivity(Intent(this, MockupActivity::class.java).putExtra("key", "redeem"))
        }, Ui.lp(-1, -2, top = Ui.dp(this, 6), bottom = Ui.dp(this, 10)))
        status = Ui.text(this, "", 13f, Ui.GOLD)
        col.addView(status, Ui.lp(-1, -2, top = Ui.dp(this, 4)))
        col.addView(
            Ui.text(this, "Precios con impuestos incluidos. Renovación automática; se cancela desde tu cuenta.", 11f, Ui.MUT),
            Ui.lp(-1, -2, top = Ui.dp(this, 10))
        )
    }

    private fun plan(name: String, price: String, sub: String, best: Boolean) {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = if (best) Ui.hero(this, 18) else Ui.rounded(this, Ui.CARD, 18, Ui.LINE)
        val p = Ui.dp(this, 16)
        c.setPadding(p, p, p, p)
        c.addView(Ui.text(this, name + (if (best) "   ✦ recomendado" else ""), 15f, Ui.GOLD, true))
        c.addView(Ui.text(this, price, 26f, Ui.TXT, true).also { it.typeface = android.graphics.Typeface.SERIF })
        c.addView(Ui.text(this, sub, 12f, Ui.MUT))
        c.setOnClickListener { buy(name) }
        col.addView(c, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
    }

    private fun buy(plan: String) {
        if (!Session.loggedIn(this)) {
            Toast.makeText(this, "Primero entra o crea una cuenta", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AuthActivity::class.java))
            return
        }
        if (Demo.active(this)) {
            AlertDialog.Builder(this)
                .setTitle("Pago simulado · $plan")
                .setMessage("En la versión real se abre la página de pago segura del procesador y, al terminar, Premium se activa solo. Aquí se activa directamente para que veas cómo queda.")
                .setPositiveButton("Simular pago") { _, _ ->
                    Demo.setPlan(this, "premium")
                    Toast.makeText(this, "¡Premium activado! ★", Toast.LENGTH_LONG).show()
                    build()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        status.text = "Abriendo la página de pago…"
        val app = applicationContext
        Api.async({ Api.checkoutUrl(app) }) { r ->
            r.onSuccess {
                status.text = "Al terminar el pago, vuelve aquí: tu plan se actualizará."
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
            }.onFailure { status.text = Api.message(it) }
        }
    }
}
