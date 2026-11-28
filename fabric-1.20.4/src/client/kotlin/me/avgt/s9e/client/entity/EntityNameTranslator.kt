package me.avgt.s9e.client.entity

import me.avgt.s9e.client.chat.StyledSegment
import me.avgt.s9e.client.chat.StyledText
import me.avgt.s9e.client.lang.LanguageKeys
import me.avgt.s9e.client.translate.LineGroup
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.translate.TranslationCoordinator
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult

/**
 * Translation of the name tag of the entity the player aims at.
 *
 * <p>One name tag is one unit: it is translated, switched back and marked as a whole, like the
 * text of a sign. The `[T]` behind the name describes the state (grey = original, yellow =
 * working, green = translation shown, red = failed).
 *
 * <p>With automatic translation on, a name tag is translated once the crosshair rested on the
 * entity for [DWELL] ticks, so entities that are merely swept over are left alone. The same text
 * always shares the same state, which means two entities with the same name are translated once
 * and look alike.
 */
class EntityNameTranslator(
    private val config: NekoConfig,
    private val coordinator: TranslationCoordinator,
    private val language: () -> String,
) {
    /** One name tag that is being shown, together with the segments it is built from. */
    private class Unit(val entry: LineGroup, val segments: List<StyledSegment>)

    private val entries = object : LinkedHashMap<String, LineGroup>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LineGroup>): Boolean =
            size > MAX_ENTRIES
    }

    /** The entity the crosshair rested on last tick, and how many ticks it has been resting there. */
    private var aimed: Entity? = null
    private var dwell = 0

    /** Called by the renderer for every name tag: shows the translation and hangs the marker. */
    fun nameTag(component: Component): Component {
        if (!config.enabled) return component

        val unit = unitOf(component) ?: return component
        return decorated(component, unit)
    }

    /** Follows the crosshair and starts the automatic translation of the aimed entity's name. */
    fun tick() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (!config.enabled || !config.autoTranslate || player == null || minecraft.screen != null) {
            clearAim()
            return
        }

        val entity = aimedEntity(minecraft) ?: run {
            clearAim()
            return
        }

        if (entity !== aimed) {
            aimed = entity
            dwell = 0
        }
        dwell++

        if (dwell < DWELL) return

        val name = entity.displayName ?: return
        val unit = unitOf(name) ?: return
        if (unit.entry.state == LineGroup.State.IDLE) unit.entry.start(coordinator)
    }

    /** Handles the `translate entity name` key: translate, show the original, or retry. */
    fun toggleAimed(): Boolean {
        if (!config.enabled) return false

        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null || minecraft.screen != null) return false

        val entity = aimedEntity(minecraft) ?: return false
        val name = entity.displayName ?: return false
        val unit = unitOf(name) ?: return false

        unit.entry.toggle(coordinator)
        return true
    }

    private fun clearAim() {
        aimed = null
        dwell = 0
    }

    /**
     * The entity the crosshair is on. Player names are never translated and an item frame draws
     * the name of its item instead.
     */
    private fun aimedEntity(minecraft: Minecraft): Entity? {
        val entity = (minecraft.hitResult as? EntityHitResult)?.entity ?: return null
        return if (entity is ItemFrame || entity is Player) null else entity
    }

    /** The unit of one name tag, or null when the text is blank or already localized. */
    private fun unitOf(component: Component): Unit? {
        val segments = StyledText.flatten(component)
        val payload = StyledText.payload(segments)
        if (!StyledText.isTranslatable(payload)) return null

        val language = language()
        // A translation is only pointless when the target is what the client already shows.
        val skipLocalized = language == LanguageKeys.clientLanguage()
        if (skipLocalized && !LanguageKeys.hasForeignText(component)) return null

        val entry = entries.getOrPut(LineGroup.key(language, listOf(payload))) {
            LineGroup(listOf(payload), booleanArrayOf(true))
        }
        return Unit(entry, segments)
    }

    /** The name with its translation and the `[T]` marker behind it. */
    private fun decorated(component: Component, unit: Unit): Component {
        val translated = unit.entry.translation(0)
        val text = if (translated == null) {
            component
        } else {
            // The model did not keep the segment markers; show the plain text, like a chat line.
            StyledText.rebuild(translated, unit.segments) ?: Component.literal(StyledText.stripMarkers(translated))
        }

        return text.copy().append(Component.literal(" ")).append(unit.entry.marker())
    }

    private companion object {
        /** How many ticks the crosshair has to rest on an entity before its name is translated. */
        const val DWELL = 4
        const val MAX_ENTRIES = 256
    }
}
