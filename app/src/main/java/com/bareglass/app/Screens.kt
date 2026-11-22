package com.bareglass.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView

object Screens {
    /** Página con fondo vino y una columna con scroll. */
    fun page(a: Activity): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(a)
        scroll.background = Ui.screenBg()
        scroll.isFillViewport = true
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(a, 20)
        col.setPadding(pad, Ui.dp(a, 32), pad, pad)
        scroll.addView(col)
        return Pair(scroll, col)
    }

    fun card(a: Activity): LinearLayout {
        val c = LinearLayout(a)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.rounded(a, Ui.CARD, 18, Ui.LINE)
        val p = Ui.dp(a, 16)
        c.setPadding(p, p, p, p)
        return c
    }

    fun info(a: Activity, title: String, msg: String) {
        AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("Vale", null).show()
    }
}
