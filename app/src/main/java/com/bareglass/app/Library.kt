package com.bareglass.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Entry(val uri: String, val name: String, var positionMs: Long)

/** Lista de videos recientes con la posición de reproducción, guardada en el propio móvil. */
object Library {
    private const val PREFS = "library"
    private const val KEY = "entries"
    private const val MAX = 50

    fun load(ctx: Context): MutableList<Entry> {
        val out = ArrayList<Entry>()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Entry(o.getString("uri"), o.getString("name"), o.optLong("pos", 0L)))
            }
        } catch (e: Exception) {
            // datos dañados: se empieza de cero
        }
        return out
    }

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("uri", e.uri)
            o.put("name", e.name)
            o.put("pos", e.positionMs)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun find(ctx: Context, uri: String): Entry? = load(ctx).firstOrNull { it.uri == uri }

    /** Pone el video al principio de la lista (o lo añade). */
    @Synchronized
    fun touch(ctx: Context, uri: String, name: String) {
        val list = load(ctx)
        val old = list.firstOrNull { it.uri == uri }
        list.removeAll { it.uri == uri }
        list.add(0, Entry(uri, name, old?.positionMs ?: 0L))
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    @Synchronized
    fun setPosition(ctx: Context, uri: String, pos: Long) {
        val list = load(ctx)
        val e = list.firstOrNull { it.uri == uri } ?: return
        e.positionMs = pos
        save(ctx, list)
    }

    @Synchronized
    fun remove(ctx: Context, uri: String) {
        val list = load(ctx)
        list.removeAll { it.uri == uri }
        save(ctx, list)
    }
}
