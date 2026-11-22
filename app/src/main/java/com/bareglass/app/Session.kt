package com.bareglass.app

import android.content.Context
import android.content.SharedPreferences

/** Estado de la cuenta en el móvil. Los tokens van cifrados con el Keystore de Android. */
object Session {
    private fun secure(ctx: Context): SharedPreferences {
        return try {
            val key = androidx.security.crypto.MasterKey.Builder(ctx)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                ctx, "bg_session", key,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            ctx.getSharedPreferences("bg_session_plain", Context.MODE_PRIVATE)
        }
    }

    private fun app(ctx: Context): SharedPreferences = ctx.getSharedPreferences("bg_app", Context.MODE_PRIVATE)

    fun adultConfirmed(ctx: Context): Boolean = app(ctx).getBoolean("adult", false)
    fun confirmAdult(ctx: Context) = app(ctx).edit().putBoolean("adult", true).apply()

    /** El usuario eligió usar la app sin cuenta. */
    fun guest(ctx: Context): Boolean = app(ctx).getBoolean("guest", false)
    fun setGuest(ctx: Context, v: Boolean) = app(ctx).edit().putBoolean("guest", v).apply()

    fun serverOverride(ctx: Context): String = app(ctx).getString("server", "") ?: ""
    fun setServer(ctx: Context, v: String) = app(ctx).edit().putString("server", v.trim().trimEnd('/')).apply()

    fun access(ctx: Context): String = secure(ctx).getString("access", "") ?: ""
    fun refresh(ctx: Context): String = secure(ctx).getString("refresh", "") ?: ""
    fun email(ctx: Context): String = app(ctx).getString("email", "") ?: ""
    fun plan(ctx: Context): String = app(ctx).getString("plan", "free") ?: "free"
    fun linkLimit(ctx: Context): Int = app(ctx).getInt("limit", 15)

    fun loggedIn(ctx: Context): Boolean = refresh(ctx).isNotEmpty()

    fun saveTokens(ctx: Context, access: String, refresh: String) {
        secure(ctx).edit().putString("access", access).putString("refresh", refresh).apply()
    }

    fun saveUser(ctx: Context, email: String, plan: String, limit: Int) {
        app(ctx).edit().putString("email", email).putString("plan", plan).putInt("limit", limit).apply()
    }

    fun clear(ctx: Context) {
        secure(ctx).edit().clear().apply()
        app(ctx).edit().remove("email").remove("plan").remove("limit").apply()
    }
}
