package me.avgt.s9e.common.text

/**
 * A user-facing message that is only turned into a component when it is displayed.
 *
 * <p>The key names an entry in `assets/neko-translator/lang/<locale>.json`; the arguments fill
 * its `%s` placeholders. An argument may be another [NekoText], so a message can embed a
 * translated fragment without losing the translation.
 *
 * <p>The common module has no Minecraft dependency, which is why messages travel as keys until
 * the platform layer renders them.
 */
data class NekoText(val key: String, val args: List<Any> = emptyList()) {
    companion object {
        fun of(key: String, vararg args: Any): NekoText = NekoText(key, args.toList())
    }
}
