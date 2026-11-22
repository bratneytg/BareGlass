package com.bareglass.app

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Iniciar sesión / crear cuenta, o seguir sin cuenta. */
class AuthActivity : Activity() {
    private var register = false
    private lateinit var email: EditText
    private lateinit var pass: EditText
    private lateinit var adult: CheckBox
    private lateinit var submit: android.widget.Button
    private lateinit var switchTxt: TextView
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var server: EditText
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, col) = Screens.page(this)

        col.addView(Ui.brand(this, 34f), Ui.lp(-2, -2, top = Ui.dp(this, 20)))
        title = Ui.text(this, "", 16f, Ui.GOLD)
        title.typeface = android.graphics.Typeface.SERIF
        col.addView(title, Ui.lp(-2, -2, top = Ui.dp(this, 4), bottom = Ui.dp(this, 24)))

        if (Demo.active(this)) {
            val d = Screens.card(this)
            d.addView(Ui.text(this, "🧪 Modo demostración", 14f, Ui.GOLD, true))
            d.addView(Ui.text(this, "Cuentas de prueba (se guardan solo en este móvil):", 12f, Ui.MUT), Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
            d.addView(Ui.button(this, "Usuario gratis · " + Demo.EMAIL, false) { fill(Demo.EMAIL) }, Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
            d.addView(Ui.button(this, "Usuario Premium · " + Demo.EMAIL_PREMIUM, false) { fill(Demo.EMAIL_PREMIUM) }, Ui.lp(-1, -2, bottom = Ui.dp(this, 8)))
            d.addView(Ui.text(this, "Contraseña de ambos: " + Demo.PASS, 12f, Ui.MUT))
            col.addView(d, Ui.lp(-1, -2, bottom = Ui.dp(this, 14)))
        }

        val card = Screens.card(this)
        email = Ui.input(this, "Correo electrónico")
        email.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        pass = Ui.input(this, "Contraseña (mínimo 8 caracteres)", password = true)
        adult = CheckBox(this)
        adult.text = "Soy mayor de edad, acepto los términos y la política de privacidad, y consiento que BareGlass guarde mis enlaces cifrados (pueden revelar datos sobre mi vida sexual). Puedo borrarlos cuando quiera."
        adult.setTextColor(Ui.MUT)
        adult.textSize = 13f
        adult.buttonTintList = ColorStateList.valueOf(Ui.ACC)
        status = Ui.text(this, "", 13f, Ui.ERR)
        submit = Ui.button(this, "", true) { send() }

        card.addView(email, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        card.addView(pass, Ui.lp(-1, -2, bottom = Ui.dp(this, 10)))
        card.addView(adult, Ui.lp(-1, -2, bottom = Ui.dp(this, 6)))
        card.addView(status, Ui.lp(-1, -2, bottom = Ui.dp(this, 6)))
        card.addView(submit, Ui.lp(-1, -2))
        col.addView(card, Ui.lp(-1, -2, bottom = Ui.dp(this, 14)))

        switchTxt = Ui.text(this, "", 14f, Ui.GOLD)
        switchTxt.gravity = Gravity.CENTER
        switchTxt.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10))
        switchTxt.setOnClickListener {
            register = !register
            render()
        }
        col.addView(switchTxt, Ui.lp(-1, -2))

        col.addView(Ui.button(this, "Continuar sin cuenta", false) {
            Session.setGuest(this, true)
            goHome()
        }, Ui.lp(-1, -2, top = Ui.dp(this, 8)))
        col.addView(
            Ui.text(this, "Sin cuenta puedes ver enlaces y archivos, y guardar hasta ${LinkStore.GUEST_LIMIT} enlaces en este móvil.", 12f, Ui.MUT)
                .also { it.gravity = Gravity.CENTER },
            Ui.lp(-1, -2, top = Ui.dp(this, 8))
        )

        // Ajuste avanzado: dirección del servidor (para pruebas o si cambia).
        val adv = Ui.text(this, "Servidor (avanzado)", 12f, Ui.MUT)
        adv.gravity = Gravity.CENTER
        adv.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 6))
        server = Ui.input(this, "https://api.tudominio.com")
        server.setText(Api.base(this))
        server.visibility = View.GONE
        adv.setOnClickListener { server.visibility = if (server.visibility == View.GONE) View.VISIBLE else View.GONE }
        col.addView(adv, Ui.lp(-1, -2))
        col.addView(server, Ui.lp(-1, -2))
        if (BuildConfig.DEMO) {
            val map = Ui.text(this, "🧭  Mapa de la app (demo)", 14f, Ui.GOLD, true)
            map.gravity = Gravity.CENTER
            map.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8))
            map.setOnClickListener { startActivity(Intent(this, StructureActivity::class.java)) }
            col.addView(map, Ui.lp(-1, -2))
        }

        render()
        setContentView(scroll)
    }

    private fun fill(e: String) {
        register = false
        render()
        email.setText(e)
        pass.setText(Demo.PASS)
        status.setTextColor(Ui.MUT)
        status.text = "Datos rellenados: pulsa «Entrar»"
    }

    private fun render() {
        title.text = if (register) "Crea tu cuenta" else "Bienvenido de nuevo"
        submit.text = if (register) "Crear cuenta" else "Entrar"
        adult.visibility = if (register) View.VISIBLE else View.GONE
        switchTxt.text = if (register) "¿Ya tienes cuenta? Inicia sesión" else "¿Primera vez? Crea una cuenta gratis"
        status.text = ""
    }

    private fun send() {
        if (busy) return
        Session.setServer(this, server.text.toString())
        if (!Api.configured(this)) {
            status.text = "La cuenta no está disponible todavía. Puedes continuar sin cuenta."
            return
        }
        val e = email.text.toString().trim()
        val p = pass.text.toString()
        if (e.length < 5 || !e.contains("@")) { status.text = "Escribe un correo válido"; return }
        if (p.length < 8) { status.text = "La contraseña necesita al menos 8 caracteres"; return }
        if (register && !adult.isChecked) { status.text = "Debes confirmar que eres mayor de edad"; return }
        busy = true
        status.setTextColor(Ui.MUT)
        status.text = "Un momento…"
        val app = applicationContext
        Api.async({
            if (register) Api.register(app, e, p) else Api.login(app, e, p)
            LinkStore.migrateLocalToAccount(app)
        }) { r ->
            busy = false
            r.onSuccess {
                Session.setGuest(this, false)
                goHome()
            }.onFailure {
                status.setTextColor(Ui.ERR)
                status.text = Api.message(it)
            }
        }
    }

    private fun goHome() {
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
        finish()
    }
}
