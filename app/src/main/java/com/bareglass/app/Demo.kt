package com.bareglass.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * MODO DEMOSTRACIÓN — solo para ver la estructura de la app sin servidor.
 * Todo vive en el móvil (SharedPreferences). Para eliminarlo: compilar con -Pdemo=false
 * o borrar este archivo, DemoScreens.kt y las referencias a `Demo.` / `BuildConfig.DEMO`.
 */
object Demo {
    const val EMAIL = "demo@bareglass.test"
    const val EMAIL_PREMIUM = "premium@bareglass.test"
    const val PASS = "Prueba123!"
    const val CODE = "BARE-DEMO"
    const val SAMPLE_VIDEO = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    /** Demo activa = compilada con demo y sin servidor real configurado. */
    fun active(ctx: Context): Boolean = BuildConfig.DEMO && Api.base(ctx).isBlank()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("bg_demo", Context.MODE_PRIVATE)

    private fun user(plan: String) = JSONObject().put("pass", PASS).put("plan", plan)

    private fun users(ctx: Context): JSONObject {
        val raw = prefs(ctx).getString("users", null)
        if (raw != null) {
            try {
                return JSONObject(raw)
            } catch (e: Exception) {
                // se vuelve a sembrar
            }
        }
        val u = JSONObject().put(EMAIL, user("free")).put(EMAIL_PREMIUM, user("premium"))
        prefs(ctx).edit().putString("users", u.toString()).apply()
        return u
    }

    private fun saveUsers(ctx: Context, u: JSONObject) = prefs(ctx).edit().putString("users", u.toString()).apply()

    fun limitFor(plan: String) = if (plan == "premium") 5000 else 15

    private fun signIn(ctx: Context, email: String, plan: String) {
        Session.saveTokens(ctx, "demo-access", "demo-refresh-$email")
        Session.saveUser(ctx, email, plan, limitFor(plan))
    }

    fun register(ctx: Context, email: String, pass: String) {
        val e = email.trim().lowercase()
        val u = users(ctx)
        if (u.has(e)) throw ApiError(409, "Ese correo ya está registrado")
        u.put(e, JSONObject().put("pass", pass).put("plan", "free"))
        saveUsers(ctx, u)
        signIn(ctx, e, "free")
    }

    fun login(ctx: Context, email: String, pass: String) {
        val e = email.trim().lowercase()
        val u = users(ctx)
        val o = u.optJSONObject(e)
        if (o == null || o.optString("pass") != pass) throw ApiError(401, "Correo o contraseña incorrectos")
        signIn(ctx, e, o.optString("plan", "free"))
    }

    fun me(ctx: Context) {
        val e = Session.email(ctx)
        val plan = users(ctx).optJSONObject(e)?.optString("plan", "free") ?: "free"
        Session.saveUser(ctx, e, plan, limitFor(plan))
    }

    fun setPlan(ctx: Context, plan: String) {
        val e = Session.email(ctx)
        val u = users(ctx)
        val o = u.optJSONObject(e) ?: return
        o.put("plan", plan)
        saveUsers(ctx, u)
        Session.saveUser(ctx, e, plan, limitFor(plan))
    }

    private fun linksKey(ctx: Context) = "links_" + Session.email(ctx)

    fun links(ctx: Context): List<SavedLink> {
        val raw = prefs(ctx).getString(linksKey(ctx), null) ?: return emptyList()
        val out = ArrayList<SavedLink>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(SavedLink(o.getString("id"), o.getString("url"), o.optString("title", "")))
            }
        } catch (e: Exception) {
            // vacío
        }
        return out
    }

    private fun saveLinks(ctx: Context, l: List<SavedLink>) {
        val a = JSONArray()
        for (x in l) a.put(JSONObject().put("id", x.id).put("url", x.url).put("title", x.title))
        prefs(ctx).edit().putString(linksKey(ctx), a.toString()).apply()
    }

    fun addLink(ctx: Context, url: String, title: String): SavedLink {
        val l = links(ctx).toMutableList()
        val limit = Session.linkLimit(ctx)
        if (l.size >= limit) throw ApiError(402, "Has llegado al límite de $limit enlaces. Hazte Premium para guardar más.")
        val n = SavedLink(UUID.randomUUID().toString().replace("-", ""), url, title)
        l.add(0, n)
        saveLinks(ctx, l)
        return n
    }

    fun deleteLink(ctx: Context, id: String) = saveLinks(ctx, links(ctx).filter { it.id != id })

    fun deleteAccount(ctx: Context) {
        val e = Session.email(ctx)
        val u = users(ctx)
        u.remove(e)
        saveUsers(ctx, u)
        prefs(ctx).edit().remove("links_$e").apply()
        Session.clear(ctx)
    }

    fun export(ctx: Context): String {
        val o = JSONObject()
        o.put("user", JSONObject().put("email", Session.email(ctx)).put("plan", Session.plan(ctx)))
        val a = JSONArray()
        for (l in links(ctx)) a.put(JSONObject().put("url", l.url).put("title", l.title))
        o.put("links", a)
        return o.toString(2)
    }

    /** Canjea un código de licencia de prueba. Devuelve true si era válido. */
    fun redeem(ctx: Context, code: String): Boolean {
        if (!code.trim().equals(CODE, ignoreCase = true) || !Session.loggedIn(ctx)) return false
        setPlan(ctx, "premium")
        return true
    }

    /** Borra todos los datos de prueba y vuelve al principio. */
    fun reset(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        Session.clear(ctx)
        Session.setGuest(ctx, false)
        ctx.getSharedPreferences("bg_app", Context.MODE_PRIVATE).edit().clear().apply()
        ctx.getSharedPreferences("bg_links", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
