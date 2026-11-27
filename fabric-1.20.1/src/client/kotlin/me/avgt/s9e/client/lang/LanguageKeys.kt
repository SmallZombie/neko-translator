package me.avgt.s9e.client.lang

import me.avgt.s9e.common.log.NekoLog
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.contents.LiteralContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceLocation
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
            val id = ResourceLocation(namespace, path)
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

    /** The language code the client currently displays. */
    fun clientLanguage(): String = Minecraft.getInstance().options.languageCode.lowercase()

    /**
     * True when the component still contains text the client language has no translation for: a
     * literal (custom name, lore, text a mod built by hand) or a key only `en_us` provides.
     */
    fun hasForeignText(component: Component): Boolean {
        when (val contents = component.contents) {
            is TranslatableContents -> {
                if (!isLocalized(contents.key)) return true
                for (arg in contents.args) {
                    if (arg is Component && hasForeignText(arg)) return true
                }
            }

            is LiteralContents -> if (contents.text().any { it.isLetter() }) return true

            // `Component.empty()` is a child container without text of its own; the children are
            // checked below, so it must not be judged by its rendered string.
            ComponentContents.EMPTY -> Unit

            // Score, selector, keybind and NBT text is never part of a language file.
            else -> if (component.string.any { it.isLetter() }) return true
        }

        for (sibling in component.siblings) {
            if (hasForeignText(sibling)) return true
        }

        return false
    }
}
