package com.bareglass.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiError(val code: Int, message: String) : Exception(message)

/** Cliente de la API de BareGlass (HTTP + JSON, sin librerías externas). */
object Api {
    private val main = Handler(Looper.getMainLooper())

    fun base(ctx: Context): String {
        val o = Session.serverOverride(ctx)
        return if (o.isNotEmpty()) o else BuildConfig.API_BASE
    }

    fun configured(ctx: Context): Boolean = base(ctx).isNotBlank() || Demo.active(ctx)

    /** Ejecuta [work] en un hilo aparte y entrega el resultado en el hilo de la interfaz. */
    fun <T> async(work: () -> T, done: (Result<T>) -> Unit) {
        Thread {
            val r = try {
                Result.success(work())
            } catch (e: Throwable) {
                Result.failure(e)
            }
            main.post { done(r) }
        }.start()
    }

    fun message(e: Throwable): String = when (e) {
        is ApiError -> e.message ?: "Error del servidor"
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            "No se pudo conectar con el servidor. Revisa tu conexión."
        else -> e.message ?: "Error inesperado"
    }

    private fun raw(ctx: Context, method: String, path: String, body: JSONObject?, token: String?): String {
        val conn = URL(base(ctx) + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { String(it.readBytes()) } ?: ""
            if (code !in 200..299) {
                var msg = "Error $code"
                try {
                    val d = JSONObject(text).opt("detail")
                    if (d is String) msg = d else if (code == 422) msg = "Revisa los datos que has escrito"
                } catch (e: Exception) {
                    // cuerpo no JSON
                }
                throw ApiError(code, msg)
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun storeAuth(ctx: Context, json: String) {
        val o = JSONObject(json)
        Session.saveTokens(ctx, o.getString("access"), o.getString("refresh"))
        storeUser(ctx, o.getJSONObject("user"))
    }

    private fun storeUser(ctx: Context, u: JSONObject) {
        Session.saveUser(ctx, u.getString("email"), u.getString("plan"), u.getInt("link_limit"))
    }

    /** Llamada con sesión: si el token caducó, lo renueva una vez y repite. */
    @Synchronized
    private fun authed(ctx: Context, method: String, path: String, body: JSONObject? = null): String {
        try {
            return raw(ctx, method, path, body, Session.access(ctx))
        } catch (e: ApiError) {
            if (e.code != 401) throw e
        }
        val rt = Session.refresh(ctx)
        if (rt.isEmpty()) throw ApiError(401, "Inicia sesión de nuevo")
        try {
            storeAuth(ctx, raw(ctx, "POST", "/v1/auth/refresh", JSONObject().put("refresh", rt), null))
        } catch (e: ApiError) {
            if (e.code == 401) Session.clear(ctx)
            throw ApiError(e.code, "Tu sesión caducó. Inicia sesión de nuevo.")
        }
        return raw(ctx, method, path, body, Session.access(ctx))
    }

    fun register(ctx: Context, email: String, password: String) {
        if (Demo.active(ctx)) return Demo.register(ctx, email, password)
        storeAuth(ctx, raw(ctx, "POST", "/v1/auth/register",
            JSONObject().put("email", email).put("password", password).put("consent", true), null))
    }

    fun login(ctx: Context, email: String, password: String) {
        if (Demo.active(ctx)) return Demo.login(ctx, email, password)
        storeAuth(ctx, raw(ctx, "POST", "/v1/auth/login",
            JSONObject().put("email", email).put("password", password), null))
    }

    fun logout(ctx: Context) {
        val rt = Session.refresh(ctx)
        try {
            if (rt.isNotEmpty() && !Demo.active(ctx)) raw(ctx, "POST", "/v1/auth/logout", JSONObject().put("refresh", rt), null)
        } catch (e: Exception) {
            // sin conexión: se cierra la sesión igualmente en el móvil
        }
        Session.clear(ctx)
    }

    fun me(ctx: Context) {
        if (Demo.active(ctx)) return Demo.me(ctx)
        storeUser(ctx, JSONObject(authed(ctx, "GET", "/v1/me")))
    }

    fun deleteAccount(ctx: Context) {
        if (Demo.active(ctx)) return Demo.deleteAccount(ctx)
        authed(ctx, "DELETE", "/v1/me")
        Session.clear(ctx)
    }

    fun export(ctx: Context): String = if (Demo.active(ctx)) Demo.export(ctx) else authed(ctx, "GET", "/v1/me/export")

    fun checkoutUrl(ctx: Context): String =
        JSONObject(authed(ctx, "POST", "/v1/billing/checkout")).getString("url")

    fun links(ctx: Context): List<SavedLink> {
        if (Demo.active(ctx)) return Demo.links(ctx)
        val arr = JSONArray(authed(ctx, "GET", "/v1/links"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SavedLink(o.getString("id"), o.getString("url"), o.optString("title", ""))
        }
    }

    fun addLink(ctx: Context, url: String, title: String): SavedLink {
        if (Demo.active(ctx)) return Demo.addLink(ctx, url, title)
        val o = JSONObject(authed(ctx, "POST", "/v1/links", JSONObject().put("url", url).put("title", title)))
        return SavedLink(o.getString("id"), o.getString("url"), o.optString("title", ""))
    }

    fun deleteLink(ctx: Context, id: String) {
        if (Demo.active(ctx)) return Demo.deleteLink(ctx, id)
        authed(ctx, "DELETE", "/v1/links/$id")
    }
}
