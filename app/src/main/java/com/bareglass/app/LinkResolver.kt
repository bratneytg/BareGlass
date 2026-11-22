package com.bareglass.app

import java.net.HttpURLConnection
import java.net.URL

/** Un video reproducible que salió de un enlace. */
class Playable(val url: String, val hls: Boolean, val referer: String?, val label: String)

/** Convierte lo que pega el usuario en algo que el reproductor entiende. No descarga ni guarda nada. */
object LinkResolver {
    const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    private val VIDEO_EXT = setOf("mp4", "m4v", "webm", "mkv", "mov", "m3u8", "ts", "mpd")

    fun extOf(url: String): String = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()

    fun normalize(input: String): String {
        val t = input.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    fun isHls(url: String): Boolean = extOf(url) == "m3u8"

    fun labelFor(url: String): String {
        val host = try { URL(url).host.removePrefix("www.") } catch (e: Exception) { "" }
        val file = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        return if (file.isNotEmpty() && file.contains('.')) "$host · $file" else host
    }

    /** true si el enlace ya apunta a un archivo de video por su extensión. */
    fun looksDirect(url: String): Boolean = extOf(url) in VIDEO_EXT

    private fun open(url: String, referer: String?): HttpURLConnection {
        var cur = url
        for (hop in 0 until 8) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "*/*")
            if (referer != null) c.setRequestProperty("Referer", referer)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IllegalStateException("Redirección sin destino")
                c.disconnect()
                cur = HlsParser.resolve(cur, loc)
                continue
            }
            if (code !in 200..299) {
                c.disconnect()
                throw IllegalStateException("El servidor respondió con el error $code")
            }
            return c
        }
        throw IllegalStateException("Demasiadas redirecciones")
    }

    enum class Kind { HLS, HTML, MEDIA, UNKNOWN }

    /** Decide qué es lo que devolvió el servidor mirando el tipo y los primeros bytes (la extensión del enlace puede mentir). */
    fun sniff(contentType: String?, head: String): Kind {
        val type = (contentType ?: "").lowercase()
        val h = head.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (h.startsWith("#EXTM3U") || type.contains("mpegurl")) return Kind.HLS
        if (type.contains("html") || h.startsWith("<!doctype html", ignoreCase = true) || h.startsWith("<html", ignoreCase = true)) return Kind.HTML
        if (type.startsWith("video/") || type.startsWith("audio/") || type.contains("matroska") || type.contains("mp4")) return Kind.MEDIA
        if (type.contains("octet-stream") || type.isEmpty()) return Kind.MEDIA
        return Kind.UNKNOWN
    }

    private fun readUpTo(c: HttpURLConnection, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        val ins = c.inputStream
        while (out.size() < limit) {
            val n = ins.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Bloqueante. Devuelve los videos encontrados (uno si el enlace es directo, varios si es una página). */
    fun resolve(input: String): List<Playable> {
        val url = normalize(input)
        val direct = looksDirect(url)
        val c = try {
            open(url, null)
        } catch (e: Exception) {
            // Algunos servidores rechazan esta comprobación pero sirven bien al reproductor.
            if (direct) return listOf(Playable(url, isHls(url), null, labelFor(url)))
            throw e
        }
        try {
            val type = c.contentType
            val kind = sniff(type, String(readUpTo(c, 2048), Charsets.UTF_8))
            when (kind) {
                Kind.HLS -> return listOf(Playable(url, true, null, labelFor(url)))
                Kind.MEDIA -> return listOf(Playable(url, false, null, labelFor(url)))
                Kind.UNKNOWN -> {
                    if (direct) return listOf(Playable(url, isHls(url), null, labelFor(url)))
                    throw IllegalArgumentException("Ese enlace no parece un video ni una página con video.")
                }
                Kind.HTML -> {}
            }
        } finally {
            c.disconnect()
        }
        // Es una página: la descargamos de nuevo completa y buscamos los videos dentro.
        val c2 = open(url, null)
        try {
            val html = String(readUpTo(c2, 4_000_000), Charsets.UTF_8)
            val found = PageScan.findVideoLinks(url, html)
            if (found.isEmpty()) {
                throw IllegalArgumentException(
                    "No encontré ningún video en esa página. Puede que el reproductor lo cargue con scripts o protegido; " +
                        "en ese caso usa el enlace directo del archivo (.mp4, .webm, .m3u8)."
                )
            }
            return found.map { Playable(it, isHls(it), url, labelFor(it)) }
        } finally {
            c2.disconnect()
        }
    }
}
