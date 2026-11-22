package com.bareglass.app

import java.net.URI

/** Lectura mínima de listas HLS (.m3u8). No depende de Android para poder probarse con tests. */
object HlsParser {

    class Variant(val url: String, val bandwidth: Long, val width: Int, val height: Int, val audioGroup: String?)

    class Master(val variants: List<Variant>, val audioGroups: Map<String, String>)

    class Media(
        val segments: List<String>,
        val initUrl: String?,
        val encrypted: Boolean,
        val live: Boolean,
        val durationSec: Double
    )

    fun isMaster(text: String): Boolean = text.contains("#EXT-X-STREAM-INF")

    fun resolve(base: String, ref: String): String =
        try {
            URI(base).resolve(ref.trim()).toString()
        } catch (e: Exception) {
            ref.trim()
        }

    /** Valor de un atributo tipo KEY=valor o KEY="valor" dentro de una línea de etiqueta. */
    fun attr(line: String, key: String): String? {
        val m = Regex("(?:^|[:,])" + Regex.escape(key) + "=(\"([^\"]*)\"|[^,]*)").find(line) ?: return null
        val g = m.groupValues
        return if (g[2].isNotEmpty() || g[1].startsWith("\"")) g[2] else g[1]
    }

    fun parseMaster(text: String, base: String): Master {
        val lines = text.lines().map { it.trim() }
        val variants = ArrayList<Variant>()
        val audio = LinkedHashMap<String, String>()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.startsWith("#EXT-X-MEDIA:") && attr(l, "TYPE") == "AUDIO") {
                val gid = attr(l, "GROUP-ID")
                val uri = attr(l, "URI")
                if (gid != null && uri != null) {
                    val isDefault = attr(l, "DEFAULT") == "YES"
                    if (!audio.containsKey(gid) || isDefault) audio[gid] = resolve(base, uri)
                }
            } else if (l.startsWith("#EXT-X-STREAM-INF:")) {
                var j = i + 1
                while (j < lines.size && (lines[j].isEmpty() || lines[j].startsWith("#"))) j++
                if (j < lines.size) {
                    val bw = attr(l, "BANDWIDTH")?.toLongOrNull() ?: 0L
                    val res = attr(l, "RESOLUTION")
                    var w = 0
                    var h = 0
                    if (res != null && res.contains("x")) {
                        w = res.substringBefore("x").toIntOrNull() ?: 0
                        h = res.substringAfter("x").toIntOrNull() ?: 0
                    }
                    variants.add(Variant(resolve(base, lines[j]), bw, w, h, attr(l, "AUDIO")))
                    i = j
                }
            }
            i++
        }
        variants.sortByDescending { it.bandwidth }
        return Master(variants, audio)
    }

    fun parseMedia(text: String, base: String): Media {
        val segs = ArrayList<String>()
        var init: String? = null
        var enc = false
        var endList = false
        var dur = 0.0
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#EXT-X-MAP:")) {
                val u = attr(l, "URI")
                if (u != null) init = resolve(base, u)
            } else if (l.startsWith("#EXT-X-KEY:")) {
                val method = attr(l, "METHOD")
                if (method != null && method != "NONE") enc = true
            } else if (l.startsWith("#EXT-X-ENDLIST")) {
                endList = true
            } else if (l.startsWith("#EXTINF:")) {
                dur += l.substringAfter(":").substringBefore(",").trim().toDoubleOrNull() ?: 0.0
            } else if (!l.startsWith("#")) {
                segs.add(resolve(base, l))
            }
        }
        return Media(segs, init, enc, !endList, dur)
    }
}

/** Busca enlaces a video dentro del código de una página web. */
object PageScan {
    fun findVideoLinks(pageUrl: String, html: String): List<String> {
        val text = html
            .replace("\\/", "/")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("&amp;", "&")
        val exts = "mp4|m3u8|webm|mkv|mov|m4v"
        val out = LinkedHashSet<String>()
        // enlaces absolutos en cualquier parte del código
        Regex("https?://[^\\s\"'<>\\\\()]+?\\.(?:" + exts + ")(?:\\?[^\\s\"'<>\\\\()]*)?", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { out.add(it.value) }
        // atributos con rutas relativas o que empiezan por //
        Regex("(?:src|href|data-src|content|file|source|url|contentUrl)\\s*[=:]\\s*[\"']([^\"']+\\.(?:" + exts + ")(?:\\?[^\"']*)?)[\"']", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { out.add(HlsParser.resolve(pageUrl, it.groupValues[1])) }
        return out.filter { it.startsWith("http") }.take(8)
    }
}
