package me.avgt.s9e.client.lang

import me.avgt.s9e.common.log.NekoLog
import net.minecraft.locale.Language
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Translation keys that the *current client language* really defines.
 *
 * <p>The game resolves a key by falling back to `en_us`, so an item whose language file has no
 * entry for the client language still shows a name - the English one. To tell "the client
 * already displays this in its own language" apart from "this fell back to English" the
 * language files have to be read again: the merged [Language] cannot answer that.
 *
 * <p>[reload] is called by the language manager on every resource (and language) reload.
 */
object LanguageKeys {
    @Volatile
    private var keys: Set<String> = emptySet()

    /** Bumped on every reload; caches that depend on the key set compare it. */
    @Volatile
    var generation: Int = 0
        private set

    /** Indexes `assets/<namespace>/lang/<code>.json` for every namespace the current packs provide. */
    fun reload(resourceManager: ResourceManager, languageCode: String) {
        val loaded = HashSet<String>()
        val path = "lang/$languageCode.json"

        for (namespace in resourceManager.namespaces) {
            val id = Identifier.fromNamespaceAndPath(namespace, path)
            for (resource in resourceManager.getResourceStack(id)) {
                try {
                    resource.open().use { stream ->
                        Language.loadFromJson(stream) { key, _ -> loaded.add(key) }
                    }
                } catch (t: Throwable) {
                    NekoLog.warn("Failed to read language file $id: ${t.message}")
                }
            }
        }

        keys = loaded
        generation++
        NekoLog.info("Indexed ${loaded.size} keys for client language $languageCode")
    }

    /** True when the client language itself (not the `en_us` fallback) defines [key]. */
    fun isLocalized(key: String): Boolean = keys.contains(key)
}
