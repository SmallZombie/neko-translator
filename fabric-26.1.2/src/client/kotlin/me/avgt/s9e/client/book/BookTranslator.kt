package me.avgt.s9e.client.book

import java.util.WeakHashMap
import me.avgt.s9e.client.chat.StyledSegment
import me.avgt.s9e.client.chat.StyledText
import me.avgt.s9e.client.util.Feed
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.translate.TranslationCoordinator
import me.avgt.s9e.common.translate.TranslationResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.BookViewScreen
import net.minecraft.network.chat.Component

/**
 * Translation state of the book screens that are currently open.
 *
 * <p>Only the page the player is looking at is translated. Nothing is written to disk: closing
 * the book throws the state away, and reopening it starts from the original page again, which
 * then comes from the ordinary translation cache (or a fresh request).
 *
 * <p>A page behaves like a chat line: grey = original, yellow = translating, green = translated
 * (click to show the original again), red = failed (click to retry).
 */
class BookTranslator(
    private val config: NekoConfig,
    private val coordinator: TranslationCoordinator,
) {
    enum class State { IDLE, TRANSLATING, DONE, FAILED }

    private class Session {
        var page: Int = -1
        var plain: String = ""
        var segments: List<StyledSegment> = emptyList()
        var payload: String = ""
        var state: State = State.IDLE
        var translation: String? = null
        var showingOriginal: Boolean = false

        /** Bumped on every visible change so the screen knows it has to re-split the page text. */
        var version: Int = 0

        /** Bumped when the shown page changes; answers for an older page are dropped. */
        var generation: Int = 0
    }

    private val sessions: MutableMap<BookViewScreen, Session> = WeakHashMap()

    private fun session(screen: BookViewScreen): Session = sessions.getOrPut(screen) { Session() }

    fun version(screen: BookViewScreen): Int = session(screen).version

    /** True while the global `[T]` switch is on; hides the book button when it is off. */
    fun visible(): Boolean = config.enabled

    /**
     * Called once per frame before the page is drawn. A new page resets the state and, when
     * automatic translation is enabled, starts the request right away.
     */
    fun onFrame(screen: BookViewScreen, page: Int, original: Component) {
        val session = session(screen)
        val plain = original.string
        if (session.page == page && session.plain == plain) return

        session.page = page
        session.plain = plain
        session.segments = StyledText.flatten(original)
        session.payload = StyledText.payload(session.segments)
        session.state = State.IDLE
        session.translation = null
        session.showingOriginal = false
        session.version++
        session.generation++

        if (config.enabled && config.autoTranslate) start(screen, session)
    }

    /** The component the page should display right now. */
    fun display(screen: BookViewScreen, original: Component): Component {
        val session = session(screen)
        if (session.state != State.DONE || session.showingOriginal) return original
        val translation = session.translation ?: return original

        return StyledText.rebuild(translation, session.segments)
            ?: Component.literal(StyledText.stripMarkers(translation))
    }

    fun click(screen: BookViewScreen) {
        val session = session(screen)
        when (session.state) {
            State.IDLE -> start(screen, session)
            State.DONE -> {
                session.showingOriginal = !session.showingOriginal
                session.version++
            }
            State.FAILED -> {
                coordinator.forgetFailure(session.payload)
                start(screen, session)
            }
            // While a translation is running a click does nothing, like the chat marker.
            State.TRANSLATING -> Unit
        }
    }

    /** Colour of the `T` glyph on the button, matching the chat marker colours. */
    fun markerColor(screen: BookViewScreen): Int {
        val session = session(screen)
        return when (session.state) {
            State.IDLE -> GRAY
            State.TRANSLATING -> YELLOW
            State.DONE -> if (session.showingOriginal) GRAY else GREEN
            State.FAILED -> RED
        }
    }

    private fun start(screen: BookViewScreen, session: Session) {
        if (session.state == State.TRANSLATING) return
        if (session.payload.isBlank()) return

        session.state = State.TRANSLATING
        session.showingOriginal = false
        session.version++
        session.generation++
        val generation = session.generation

        coordinator.requestForChat(session.payload).whenComplete { result, error ->
            Minecraft.getInstance().execute {
                // The page may have been turned, or the book closed, while the model worked.
                if (session.generation != generation) return@execute
                if (Minecraft.getInstance().screen !== screen) return@execute

                when (result) {
                    is TranslationResult.Ok -> {
                        session.translation = result.text
                        session.state = State.DONE
                    }

                    is TranslationResult.Err -> {
                        session.state = State.FAILED
                        Feed.error(result.message)
                    }

                    null -> {
                        session.state = State.FAILED
                        NekoLog.warn("Book translation failed: ${error?.javaClass?.simpleName}")
                        Feed.error("翻译失败")
                    }
                }
                session.version++
            }
        }
    }

    private companion object {
        const val GRAY = 0xFFAAAAAA.toInt()
        const val YELLOW = 0xFFFFFF55.toInt()
        const val GREEN = 0xFF55FF55.toInt()
        const val RED = 0xFFFF5555.toInt()
    }
}
