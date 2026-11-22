package com.bareglass.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Estilo visual de BareGlass: negro vino, carmesí y oro rosado. */
object Ui {
    val BG: Int = Color.parseColor("#0A0508")
    val BG_TOP: Int = Color.parseColor("#2A0A18")
    val CARD: Int = Color.parseColor("#1A0A10")
    val LINE: Int = Color.parseColor("#3A1623")
    val TXT: Int = Color.parseColor("#F7EDF0")
    val MUT: Int = Color.parseColor("#B393A0")
    val ACC: Int = Color.parseColor("#E11D48")
    val ACC2: Int = Color.parseColor("#9F1239")
    val GOLD: Int = Color.parseColor("#F4B6C2")
    val ERR: Int = Color.parseColor("#FF6B81")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(ctx: Context, fill: Int, radiusDp: Int, stroke: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(fill)
        d.cornerRadius = dp(ctx, radiusDp).toFloat()
        if (stroke != 0) d.setStroke(dp(ctx, 1), stroke)
        return d
    }

    fun accent(ctx: Context, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACC, ACC2))
        d.cornerRadius = dp(ctx, radiusDp).toFloat()
        return d
    }

    /** Fondo de pantalla: un resplandor vino arriba que se funde a negro. */
    fun screenBg(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG, BG))

    /** Tarjeta destacada con degradado vino → carmesí. */
    fun hero(ctx: Context, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#5A0F2B"), Color.parseColor("#240812")))
        d.cornerRadius = dp(ctx, radiusDp).toFloat()
        d.setStroke(dp(ctx, 1), Color.parseColor("#7A2440"))
        return d
    }

    fun text(ctx: Context, s: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    /** Logotipo «BareGlass»: Bare en blanco y negrita, Glass en cursiva oro rosado. */
    fun brand(ctx: Context, sizeSp: Float): TextView {
        val s = SpannableString("BareGlass")
        s.setSpan(ForegroundColorSpan(TXT), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(GOLD), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.ITALIC), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val t = TextView(ctx)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        t.typeface = Typeface.SERIF
        t.letterSpacing = 0.02f
        return t
    }

    /** primary = botón carmesí; si no, botón oscuro con borde. */
    fun button(ctx: Context, label: String, primary: Boolean, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        b.setTextColor(Color.WHITE)
        b.stateListAnimator = null
        b.minHeight = dp(ctx, 48)
        b.minimumHeight = dp(ctx, 48)
        b.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0)
        b.background = if (primary) accent(ctx, 24) else rounded(ctx, Color.parseColor("#240C15"), 24, LINE)
        b.setOnClickListener { onClick() }
        return b
    }

    fun input(ctx: Context, hint: String, password: Boolean = false, multiline: Boolean = false): EditText {
        val e = EditText(ctx)
        e.hint = hint
        e.setHintTextColor(Color.parseColor("#7A5A66"))
        e.setTextColor(TXT)
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        e.background = rounded(ctx, Color.parseColor("#12060B"), 14, LINE)
        e.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        e.setSingleLine(!multiline)
        if (password) {
            e.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        return e
    }

    fun lp(w: Int, h: Int, top: Int = 0, bottom: Int = 0, left: Int = 0, right: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.setMargins(left, top, right, bottom)
        return p
    }

    fun fmtTime(ms: Long): String {
        if (ms < 0) return "--:--"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
    }
}
