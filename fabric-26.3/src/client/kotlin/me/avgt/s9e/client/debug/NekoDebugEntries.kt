package me.avgt.s9e.client.debug

import me.avgt.s9e.client.Neko
import me.avgt.s9e.common.debug.TranslationDebug
import me.avgt.s9e.common.translate.OpenAiClient
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer
import net.minecraft.client.gui.components.debug.DebugScreenEntry
import net.minecraft.client.gui.components.debug.DebugScreenEntryList
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk

/**
 * Entries of the mod in the debug screen the `debug options` key opens (F3 + F6 by default).
 *
 * <p>`translation_queue` shows what the translator is working on. `translation_requests` draws
 * nothing: its status is the switch for the request/response traffic log, and both "in overlay"
 * and "always on" mean on.
 */
object NekoDebugEntries {
    private val QUEUE = Identifier.fromNamespaceAndPath("neko-translator", "translation_queue")
    private val REQUESTS = Identifier.fromNamespaceAndPath("neko-translator", "translation_requests")

    /** Called by the mixin once vanilla filled its entry registry, which has no public register. */
    @JvmStatic
    fun registerEntries(entries: MutableMap<Identifier, DebugScreenEntry>) {
        entries[QUEUE] = QueueEntry
        entries[REQUESTS] = RequestLogEntry
    }

    /** Called by the mixin after the entry list was rebuilt, i.e. after every status change. */
    @JvmStatic
    fun syncRequestLogging(list: DebugScreenEntryList) {
        OpenAiClient.requestLogging = list.getStatus(REQUESTS) != DebugScreenEntryStatus.NEVER
    }

    /** What the translator is doing right now, one line per request. */
    private object QueueEntry : DebugScreenEntry {
        override fun display(
            displayer: DebugScreenDisplayer,
            level: Level?,
            clientChunk: LevelChunk?,
            serverChunk: LevelChunk?,
        ) {
            if (!Neko.ready) return

            val lines = ArrayList<String>()
            val jobs = TranslationDebug.snapshot()
            val waiting = jobs.count { it.waiting }

            if (jobs.isEmpty()) {
                lines += "translation queue: idle"
            } else {
                lines += "translation queue: ${jobs.size} (${jobs.size - waiting} running, $waiting waiting)"
                for (job in jobs.take(MAX_JOBS)) {
                    val state = if (job.waiting) "wait" else "run"
                    lines += "  $state ${job.kind} ${job.language} ${job.millis()}ms ${clip(job.text)}"
                }
                if (jobs.size > MAX_JOBS) lines += "  ... ${jobs.size - MAX_JOBS} more"
            }

            lines += "  cache ${Neko.cache.size()}, model ${Neko.config.model.ifBlank { "-" }}, target ${Neko.language()}"
            displayer.addToGroup(QUEUE, lines)
        }
    }

    /** Draws nothing; the status of this entry switches the console traffic log on and off. */
    private object RequestLogEntry : DebugScreenEntry {
        override fun display(
            displayer: DebugScreenDisplayer,
            level: Level?,
            clientChunk: LevelChunk?,
            serverChunk: LevelChunk?,
        ) = Unit

        override fun isAllowed(reducedDebugInfo: Boolean): Boolean = true
    }

    private const val MAX_JOBS = 8
    private const val MAX_TEXT = 60

    private fun clip(text: String): String {
        val flat = text.replace('\n', ' ').trim()
        return if (flat.length <= MAX_TEXT) flat else flat.take(MAX_TEXT) + "..."
    }
}
