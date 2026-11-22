package com.bareglass.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView

/** Cuenta: plan, suscripción, exportar y borrar datos, cerrar sesión. */
class AccountActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, col) = Screens.page(this)
        col.addView(Ui.brand(this, 26f), Ui.lp(-2, -2, bottom = Ui.dp(this, 18)))

        val card = Screens.card(this)
        info = Ui.text(this, "", 15f, Ui.TXT)
        card.addView(info)
        col.addView(card, Ui.lp(-1, -2, bottom = Ui.dp(this, 14)))

        status = Ui.text(this, "", 13f, Ui.GOLD)

        if (Session.plan(this) != "premium") {
            col.addView(Ui.button(this, "✦ Hazte Premium", true) { checkout() }, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        } else {
            col.addView(Ui.button(this, "Gestionar suscripción", false) { checkout() }, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        }
        col.addView(Ui.button(this, "Actualizar estado de mi plan", false) { refresh() }, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        col.addView(Ui.button(this, "Descargar mis datos", false) { export() }, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        col.addView(Ui.button(this, "Cerrar sesión", false) { logout() }, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        col.addView(Ui.button(this, "Eliminar mi cuenta y mis datos", false) { confirmDelete() }, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        col.addView(status, Ui.lp(-1, -2, top = Ui.dp(this, 6)))
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (!Session.loggedIn(this)) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        renderInfo()
        refresh()
    }

    private fun renderInfo() {
        val prem = Session.plan(this) == "premium"
        info.text = Session.email(this) + "\nPlan: " + (if (prem) "★ Premium" else "Gratis") +
            "\nEnlaces guardables: hasta " + Session.linkLimit(this)
    }

    private fun refresh() {
        val app = applicationContext
        Api.async({ Api.me(app) }) { r ->
            r.onSuccess { renderInfo() }.onFailure { status.text = Api.message(it) }
        }
    }

    private fun checkout() {
        val app = applicationContext
        status.text = "Abriendo la página de pago…"
        Api.async({ Api.checkoutUrl(app) }) { r ->
            r.onSuccess {
                status.text = "Al terminar el pago, vuelve aquí y pulsa «Actualizar estado de mi plan»."
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
            }.onFailure { status.text = Api.message(it) }
        }
    }

    private fun export() {
        val app = applicationContext
        status.text = "Preparando tus datos…"
        Api.async({ Api.export(app) }) { r ->
            r.onSuccess {
                status.text = ""
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_SUBJECT, "Mis datos de BareGlass")
                i.putExtra(Intent.EXTRA_TEXT, it)
                startActivity(Intent.createChooser(i, "Guardar o enviar mis datos"))
            }.onFailure { status.text = Api.message(it) }
        }
    }

    private fun logout() {
        val app = applicationContext
        Api.async({ Api.logout(app) }) {
            Session.setGuest(this, false)
            toAuth()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar cuenta")
            .setMessage("Se borrarán tu cuenta, tus enlaces guardados y tus sesiones. No se puede deshacer.")
            .setPositiveButton("Eliminar todo") { _, _ ->
                val app = applicationContext
                Api.async({ Api.deleteAccount(app) }) { r ->
                    r.onSuccess {
                        Session.setGuest(this, false)
                        toAuth()
                    }.onFailure { status.text = Api.message(it) }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toAuth() {
        val i = Intent(this, AuthActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(i)
    }
}
