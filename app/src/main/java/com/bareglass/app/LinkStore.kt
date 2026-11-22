package com.bareglass.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedLink(val id: String, val url: String, val title: String)

/** Enlaces guardados: en el móvil si no hay cuenta, en el servidor (cifrados) si la hay. Funciones bloqueantes: llamar desde un hilo. */
object LinkStore {
    const val GUEST_LIMIT = 5

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("bg_links", Context.MODE_PRIVATE)

    fun local(ctx: Context): MutableList<SavedLink> {
        val out = ArrayList<SavedLink>()
        val raw = prefs(ctx).getString("items", null) ?: return out
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(SavedLink(o.getString("id"), o.getString("url"), o.optString("title", "")))
            }
        } catch (e: Exception) {
            // datos dañados: se empieza de cero
        }
        return out
    }

    private fun saveLocal(ctx: Context, list: List<SavedLink>) {
        val a = JSONArray()
        for (l in list) a.put(JSONObject().put("id", l.id).put("url", l.url).put("title", l.title))
        prefs(ctx).edit().putString("items", a.toString()).apply()
    }

    fun limit(ctx: Context): Int = if (Session.loggedIn(ctx)) Session.linkLimit(ctx) else GUEST_LIMIT

    fun list(ctx: Context): List<SavedLink> =
        if (Session.loggedIn(ctx)) Api.links(ctx) else local(ctx)

    fun add(ctx: Context, url: String, title: String): SavedLink {
        if (Session.loggedIn(ctx)) return Api.addLink(ctx, url, title)
        val l = local(ctx)
        if (l.any { it.url == url }) return l.first { it.url == url }
        if (l.size >= GUEST_LIMIT) {
            throw ApiError(402, "Sin cuenta puedes guardar $GUEST_LIMIT enlaces. Crea una cuenta gratis para guardar más.")
        }
        val n = SavedLink(UUID.randomUUID().toString().replace("-", ""), url, title)
        l.add(0, n)
        saveLocal(ctx, l)
        return n
    }

    fun remove(ctx: Context, id: String) {
        if (Session.loggedIn(ctx)) {
            Api.deleteLink(ctx, id)
        } else {
            saveLocal(ctx, local(ctx).filter { it.id != id })
        }
    }

    /** Tras iniciar sesión, sube a la cuenta los enlaces guardados sin cuenta y los borra del móvil. */
    fun migrateLocalToAccount(ctx: Context): Int {
        var moved = 0
        val l = local(ctx)
        for (x in l.reversed()) {
            try {
                Api.addLink(ctx, x.url, x.title)
                moved++
            } catch (e: ApiError) {
                if (e.code == 402) break
            }
        }
        if (l.isNotEmpty()) saveLocal(ctx, emptyList())
        return moved
    }
}
