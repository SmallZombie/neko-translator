package me.avgt.s9e.common.cache

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import me.avgt.s9e.common.log.NekoLog

/**
 * Persistent translation cache shared by locally produced and peer provided translations.
 *
 * <p>Keys are short hashes of `language + NUL + source text`, so a given sentence is only
 * ever translated once per target language, no matter how often it shows up in chat.
 */
class TranslationCache(private val path: Path) {
    private val entries = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return this.size > MAX_ENTRIES
        }
    }

    @Volatile
    private var dirty = false

    fun get(key: String): String? = synchronized(entries) { entries[key] }

    fun put(key: String, translation: String) {
        if (translation.isBlank()) return
        synchronized(entries) {
            entries[key] = translation
            dirty = true
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            dirty = true
        }
    }

    fun size(): Int = synchronized(entries) { entries.size }

    fun load() {
        if (!Files.isRegularFile(path)) return

        try {
            val root = JsonParser.parseString(Files.readString(path)).asJsonObject
            val cache = root.getAsJsonObject("entries") ?: return
            synchronized(entries) {
                for ((key, value) in cache.entrySet()) {
                    if (value.isJsonPrimitive) entries[key] = value.asString
                }
            }
        } catch (t: Throwable) {
            NekoLog.warn("Failed to read translation cache: ${t.message}")
        }
    }

    fun save(force: Boolean = false) {
        val snapshot = synchronized(entries) {
            if (!dirty && !force) return
            dirty = false
            LinkedHashMap(entries)
        }

        try {
            Files.createDirectories(path.parent)
            val root = JsonObject()
            root.addProperty("version", 1)
            val cache = JsonObject()
            snapshot.forEach { (key, value) -> cache.addProperty(key, value) }
            root.add("entries", cache)
            val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
            Files.writeString(tmp, root.toString())
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            NekoLog.warn("Failed to write translation cache: ${t.message}")
        }
    }

    companion object {
        private const val MAX_ENTRIES = 4000

        /** Stable, short cache key. */
        fun key(text: String, language: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest((language.lowercase() + "\u0000" + text).toByteArray(Charsets.UTF_8))
            val builder = StringBuilder(32)
            for (i in 0 until 16) {
                val value = bytes[i].toInt() and 0xFF
                builder.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
            }
            return builder.toString()
        }

        private const val HEX = "0123456789abcdef"
    }
}
