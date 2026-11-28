package me.avgt.s9e.common.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Local, client-only settings of Neko Translator.
 *
 * <p>The API key lives only in this file on this computer. It is never written into a
 * packet, never sent to another player and never forwarded to the peer relay.
 */
class NekoConfig {
    /** Global switch for the clickable [T] marker (and therefore for automatic translation). */
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var apiBaseUrl: String = ""

    @Volatile
    var model: String = ""

    @Volatile
    var apiKey: String = ""

    /** Master switch: allow other players to obtain translations through this client. */
    @Volatile
    var allowOthers: Boolean = false

    /** Master switch: obtain translations through linked players instead of the local API. */
    @Volatile
    var useOthers: Boolean = false

    /** Translate new chat lines, book pages and item tooltips without a click. Off by default: one click is cheap, API calls are not. */
    @Volatile
    var autoTranslate: Boolean = false

    /** Always translate the title, subtitle and action bar messages; they cannot be toggled one by one. */
    @Volatile
    var translateTitles: Boolean = false

    /** Master switch for boss bar names: they then follow [autoTranslate] and the `translate bossbar` key. */
    @Volatile
    var translateBossbar: Boolean = false

    /** Translate the text of display entities (holograms) that are close enough to be read. */
    @Volatile
    var translateDisplays: Boolean = false

    /** Master switch for the sidebar scoreboard: its title and every line, independent of [autoTranslate]. */
    @Volatile
    var translateScoreboard: Boolean = false

    /** Target language code, or [AUTO_LANGUAGE] to follow the client language. */
    @Volatile
    var targetLanguage: String = AUTO_LANGUAGE

    /** Model thinking level, one of [REASONING_LEVELS]; always sent to the API as `reasoning_effort`. */
    @Volatile
    var reasoningEffort: String = "none"

    /** Players I allowed to obtain translations through me. uuid -> last known name. */
    val allowed: LinkedHashMap<UUID, String> = LinkedHashMap()

    /** Players I request translations from. uuid -> last known name. */
    val linked: LinkedHashMap<UUID, String> = LinkedHashMap()

    fun hasLocalApi(): Boolean = apiBaseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    /** Effective target language; [autoLanguage] is the client language supplied by the platform. */
    fun language(autoLanguage: String): String =
        if (targetLanguage == AUTO_LANGUAGE) autoLanguage else targetLanguage

    fun toJson(): JsonObject {
        val root = JsonObject()
        root.addProperty("version", 1)
        root.addProperty("enabled", enabled)
        root.addProperty("apiBaseUrl", apiBaseUrl)
        root.addProperty("model", model)
        root.addProperty("apiKey", apiKey)
        root.addProperty("allowOthers", allowOthers)
        root.addProperty("useOthers", useOthers)
        root.addProperty("autoTranslate", autoTranslate)
        root.addProperty("translateTitles", translateTitles)
        root.addProperty("translateBossbar", translateBossbar)
        root.addProperty("translateDisplays", translateDisplays)
        root.addProperty("translateScoreboard", translateScoreboard)
        root.addProperty("targetLanguage", targetLanguage)
        root.addProperty("reasoningEffort", reasoningEffort)
        root.add("allowed", playersToJson(allowed))
        root.add("linked", playersToJson(linked))
        return root
    }

    fun applyJson(root: JsonObject) {
        enabled = root.bool("enabled", true)
        apiBaseUrl = root.string("apiBaseUrl", "")
        model = root.string("model", "")
        apiKey = root.string("apiKey", "")
        allowOthers = root.bool("allowOthers", false)
        useOthers = root.bool("useOthers", false)
        autoTranslate = root.bool("autoTranslate", false)
        translateTitles = root.bool("translateTitles", false)
        translateBossbar = root.bool("translateBossbar", false)
        translateDisplays = root.bool("translateDisplays", false)
        translateScoreboard = root.bool("translateScoreboard", false)
        targetLanguage = root.string("targetLanguage", AUTO_LANGUAGE)
        reasoningEffort = root.string("reasoningEffort", "none")
        // Config files written before the level was always sent spell "none" as "off".
        if (reasoningEffort == "off") reasoningEffort = "none"
        playersFromJson(root.getAsJsonArray("allowed"), allowed)
        playersFromJson(root.getAsJsonArray("linked"), linked)
    }

    companion object {
        const val AUTO_LANGUAGE = "auto"

        /** Thinking levels the API accepts, in the order the settings button cycles them. */
        val REASONING_LEVELS = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")

        fun load(path: Path): NekoConfig {
            val config = NekoConfig()
            if (!Files.isRegularFile(path)) return config

            try {
                val text = Files.readString(path)
                val parsed = JsonParser.parseString(text)
                if (parsed.isJsonObject) config.applyJson(parsed.asJsonObject)
            } catch (t: Throwable) {
                NekoConfigLog.warn("Failed to read $path: ${t.message}")
            }

            return config
        }

        fun save(path: Path, config: NekoConfig) {
            try {
                Files.createDirectories(path.parent)
                val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
                Files.writeString(tmp, config.toJson().toString())
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (t: Throwable) {
                NekoConfigLog.warn("Failed to write $path: ${t.message}")
            }
        }

        private fun playersToJson(players: Map<UUID, String>): JsonArray {
            val array = JsonArray()
            players.forEach { (uuid, name) ->
                val entry = JsonObject()
                entry.addProperty("uuid", uuid.toString())
                entry.addProperty("name", name)
                array.add(entry)
            }
            return array
        }

        private fun playersFromJson(array: JsonArray?, target: MutableMap<UUID, String>) {
            target.clear()
            if (array == null) return

            for (element in array) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val uuid = try {
                    UUID.fromString(obj.string("uuid", ""))
                } catch (t: Throwable) {
                    null
                } ?: continue
                target[uuid] = obj.string("name", uuid.toString().substring(0, 8))
            }
        }
    }
}

internal object NekoConfigLog {
    private val logger = org.slf4j.LoggerFactory.getLogger("neko-translator")

    fun warn(message: String) = logger.warn(message)
}

internal fun JsonObject.string(key: String, fallback: String): String {
    val element = this.get(key) ?: return fallback
    return if (element.isJsonPrimitive) element.asString else fallback
}

internal fun JsonObject.bool(key: String, fallback: Boolean): Boolean {
    val element = this.get(key) ?: return fallback
    return if (element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) element.asBoolean else fallback
}
