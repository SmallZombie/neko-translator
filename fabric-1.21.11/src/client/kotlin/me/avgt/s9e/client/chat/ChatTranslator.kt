package me.avgt.s9e.client.chat

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import me.avgt.s9e.client.mixin.ChatComponentAccess
import me.avgt.s9e.client.util.Feed
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.translate.TranslationCoordinator
import me.avgt.s9e.common.translate.TranslationResult
import net.minecraft.ChatFormatting
import net.minecraft.client.GuiMessage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier

/**
 * Tracks every chat line, appends the clickable `[T]` marker and swaps the displayed text
 * between the original message and its translation.
 */
class ChatTranslator(
    private val config: NekoConfig,
    private val coordinator: TranslationCoordinator,
) {
    enum class State { IDLE, TRANSLATING, DONE, FAILED }

    private class Entry(
        val id: Long,
        val original: Component,
        val prefix: List<StyledSegment>,
        val body: List<StyledSegment>,
        val rawText: String,
        /** What is sent to the model: plain body text, or the body with segment markers. */
        val translateText: String,
        var state: State = State.IDLE,
        var translation: String? = null,
        var showingOriginal: Boolean = false,
        var gui: GuiMessage? = null,
    )

    private val entries = ConcurrentHashMap<Long, Entry>()
    private val nextId = AtomicLong(1)
    private var pendingEntry: Entry? = null
    private var pendingContent: Component? = null

    /** Called from the [ChatComponent] mixin before the message is stored. */
    fun decorate(contents: Component): Component {
        pruneIfNeeded()
        val parts = StyledText.split(contents)
        val bodyText = StyledText.join(parts.body)

        if (bodyText.isBlank() || parts.raw.trim().startsWith(PREFIX)) {
            pendingEntry = null
            pendingContent = null
            return contents
        }

        val entry = Entry(
            id = nextId.incrementAndGet(),
            original = contents,
            prefix = parts.prefix,
            body = parts.body,
            rawText = parts.raw,
            translateText = StyledText.payload(parts.body),
        )

        entries[entry.id] = entry
        pendingEntry = entry

        val decorated = buildContent(entry)
        pendingContent = decorated
        return decorated
    }

    /** Called from the [ChatComponent] mixin after the message was stored. */
    fun afterAdd(chat: ChatComponent) {
        val entry = pendingEntry
        val content = pendingContent
        pendingEntry = null
        pendingContent = null

        if (entry == null || content == null) return
        if (entry.rawText.trim().startsWith(PREFIX)) {
            entries.remove(entry.id)
            return
        }

        val access = chat as? ChatComponentAccess
        val messages = access?.nekoAllMessages()
        val gui = messages?.firstOrNull { it.content() === content }

        if (gui == null) {
            entries.remove(entry.id)
            return
        }

        entry.gui = gui

        if (config.enabled && config.autoTranslate) {
            start(entry)
        }
    }

    /** Handles a click on a `[T]` marker. */
    fun onClick(id: Long) {
        NekoLog.info("Translate marker clicked (entry=$id)")
        val entry = entries[id]
        if (entry == null) {
            Feed.error("这条消息已经不在聊天记录中了")
            return
        }

        when (entry.state) {
            State.DONE -> {
                entry.showingOriginal = !entry.showingOriginal
                refresh(entry)
            }

            State.FAILED -> {
                coordinator.forgetFailure(entry.translateText)
                start(entry)
            }

            State.IDLE -> start(entry)
            // While a translation is running a click does nothing on purpose.
            State.TRANSLATING -> Unit
        }
    }

    /** Re-renders every tracked line, used when the global [T] switch changes. */
    fun rebuildAll() {
        for (entry in entries.values.toList()) {
            refresh(entry)
        }
    }

    private fun start(entry: Entry) {
        if (entry.state == State.TRANSLATING) return
        entry.state = State.TRANSLATING
        entry.showingOriginal = false
        refresh(entry)

        coordinator.requestForChat(entry.translateText).whenComplete { result, _ ->
            Minecraft.getInstance().execute {
                when (result) {
                    is TranslationResult.Ok -> {
                        entry.translation = result.text
                        entry.state = State.DONE
                        entry.showingOriginal = false
                    }

                    is TranslationResult.Err -> {
                        entry.state = State.FAILED
                        Feed.error(result.message)
                    }

                    null -> {
                        entry.state = State.FAILED
                        Feed.error("翻译失败")
                    }
                }
                refresh(entry)
            }
        }
    }

    private fun refresh(entry: Entry) {
        val minecraft = Minecraft.getInstance()
        if (!minecraft.isSameThread) {
            minecraft.execute { refresh(entry) }
            return
        }

        val gui = entry.gui ?: return
        val access = minecraft.gui.chat as? ChatComponentAccess ?: return
        val messages = access.nekoAllMessages()
        val index = messages.indexOfFirst { it === gui }

        if (index < 0) {
            entries.remove(entry.id)
            entry.gui = null
            return
        }

        val previous = messages[index]
        val replacement = GuiMessage(previous.addedTime(), buildContent(entry), previous.signature(), previous.tag())
        messages[index] = replacement
        entry.gui = replacement
        access.nekoRefresh()
    }

    private fun buildContent(entry: Entry): Component {
        val body = if (entry.state == State.DONE && !entry.showingOriginal && entry.translation != null) {
            translatedBody(entry)
        } else {
            entry.original
        }

        val result = body.copy()
        if (config.enabled) {
            result.append(Component.literal(" "))
            result.append(marker(entry))
        }
        return result
    }

    /** Rebuilds the translated body with the original formatting and re-adds the name prefix. */
    private fun translatedBody(entry: Entry): Component {
        val translation = entry.translation ?: return entry.original
        val body = StyledText.rebuild(translation, entry.body)
            ?: Component.literal(StyledText.stripMarkers(translation))

        if (entry.prefix.isEmpty()) return body

        return Component.empty()
            .append(StyledText.toComponent(entry.prefix))
            .append(body)
    }

    private fun marker(entry: Entry): Component {
        // The colour always describes what is on screen: grey for the original text (never
        // translated, or switched back to it), green only while the translation is displayed.
        val color = when (entry.state) {
            State.IDLE -> ChatFormatting.GRAY
            State.TRANSLATING -> ChatFormatting.YELLOW
            State.DONE -> if (entry.showingOriginal) ChatFormatting.GRAY else ChatFormatting.GREEN
            State.FAILED -> ChatFormatting.RED
        }

        val hover = when (entry.state) {
            State.IDLE -> "点击翻译这条消息"
            State.TRANSLATING -> "翻译中…"
            State.DONE -> if (entry.showingOriginal) "点击显示译文" else "点击显示原文"
            State.FAILED -> "翻译失败，点击重试"
        }

        val style = Style.EMPTY
            .withColor(color)
            .withClickEvent(ClickEvent.Custom(TRANSLATE_CLICK_ID, Optional.of<Tag>(StringTag.valueOf(entry.id.toString()))))
            .withHoverEvent(HoverEvent.ShowText(Component.literal(hover)))

        return Component.literal("[T]").withStyle(style)
    }

    private fun pruneIfNeeded() {
        if (entries.size < MAX_ENTRIES) return
        val sorted = entries.values.sortedBy { it.id }
        for (index in 0 until sorted.size - MAX_ENTRIES + 32) {
            entries.remove(sorted[index].id)
        }
    }

    companion object {
        const val PREFIX = "[Neko Translator]"
        private const val MAX_ENTRIES = 400

        @JvmField
        val TRANSLATE_CLICK_ID: Identifier = Identifier.fromNamespaceAndPath("neko-translator", "translate_marker")
    }
}
