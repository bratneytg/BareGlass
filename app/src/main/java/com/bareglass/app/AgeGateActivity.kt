package com.bareglass.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout

/** Primera pantalla: aviso de contenido para adultos. */
class AgeGateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, col) = Screens.page(this)
        col.gravity = Gravity.CENTER_HORIZONTAL

        col.addView(Ui.text(this, "18+", 44f, Ui.ACC, true).also { it.gravity = Gravity.CENTER }, Ui.lp(-2, -2, top = Ui.dp(this, 60)))
        col.addView(Ui.brand(this, 38f), Ui.lp(-2, -2, top = Ui.dp(this, 6)))
        col.addView(
            Ui.text(this, "Mira sin filtros.", 16f, Ui.GOLD).also { it.typeface = android.graphics.Typeface.SERIF },
            Ui.lp(-2, -2, top = Ui.dp(this, 4), bottom = Ui.dp(this, 36))
        )

        val card = Screens.card(this)
        card.addView(Ui.text(this, "Solo para adultos", 18f, Ui.TXT, true))
        card.addView(
            Ui.text(
                this,
                "BareGlass es un visor de video en realidad virtual pensado para público adulto. " +
                    "No aloja ni vende videos: reproduce los enlaces y archivos que tú traes.\n\n" +
                    "Para entrar debes tener al menos 18 años (o la mayoría de edad de tu país) y que ver este tipo de contenido sea legal donde vives.",
                14f, Ui.MUT
            ),
            Ui.lp(-1, -2, top = Ui.dp(this, 8))
        )
        col.addView(card, Ui.lp(-1, -2, bottom = Ui.dp(this, 20)))

        col.addView(Ui.button(this, "Tengo 18 años o más · Entrar", true) {
            Session.confirmAdult(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        col.addView(Ui.button(this, "Salir", false) { finishAffinity() }, Ui.lp(-1, -2))
        setContentView(scroll)
    }
}
