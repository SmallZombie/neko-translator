package me.avgt.s9e.common.peer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.text.NekoText
import me.avgt.s9e.common.translate.TranslationCoordinator
import me.avgt.s9e.common.translate.TranslationResult

/**
 * Peer to peer layer: allow list, link list and the small request/response protocol that
 * travels through the platform relay.
 *
 * <p>Privacy rules enforced here:
 * <ul>
 *     <li>the API key and the API endpoint never leave this client;</li>
 *     <li>a translation failure is answered with a plain "failed" flag, never with the reason;</li>
 *     <li>requests are only served when {@code allowOthers} is on and the requester was
 *     explicitly allowed with {@code /t allow}.</li>
 * </ul>
 */
class PeerManager(
    private val config: NekoConfig,
    private val transport: PeerTransport,
    private val onConfigChanged: () -> Unit,
    private val onLocalError: (NekoText) -> Unit,
    private val coordinator: () -> TranslationCoordinator,
) {
    private val tokens = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, Pending>()

    val relayAvailable: Boolean
        get() = transport.isAvailable()

    /** First linked player that is currently online, or {@code null}. */
    fun pickLinkedPlayer(): Pair<UUID, String>? =
        config.linked.entries.firstOrNull { transport.isOnline(it.key) }?.let { it.key to it.value }

    fun translateViaPeer(target: UUID, text: String, language: String): CompletableFuture<TranslationResult> {
        if (!relayAvailable) {
            return CompletableFuture.completedFuture(
                TranslationResult.Err(NekoText.of("neko-translator.error.relay_missing"))
            )
        }

        val message = JsonObject()
        message.addProperty("t", TYPE_REQUEST)
        message.addProperty("text", text.take(MAX_TEXT))
        message.addProperty("lang", language)

        return exchange(target, message, Kind.REQUEST, REQUEST_TIMEOUT_SECONDS)
            .handle { reply, error ->
                when {
                    error != null -> TranslationResult.Err(describeFailure(error))
                    reply == null -> TranslationResult.Err(NekoText.of("neko-translator.error.peer_no_response"))
                    reply.relayFailed -> TranslationResult.Err(NekoText.of("neko-translator.error.peer_offline"))
                    !reply.ok && reply.denied -> TranslationResult.Err(NekoText.of("neko-translator.error.peer_denied"))
                    !reply.ok -> TranslationResult.Err(NekoText.of("neko-translator.error.peer_translate_failed"))
                    reply.text.isNullOrBlank() -> TranslationResult.Err(NekoText.of("neko-translator.error.peer_empty"))
                    else -> TranslationResult.Ok(reply.text)
                }
            }
    }

    /** Resolves with {@code null} when the link was established, or with the reason it was not. */
    fun link(target: UUID, name: String): CompletableFuture<NekoText?> {
        if (config.linked.containsKey(target)) {
            return CompletableFuture.completedFuture(null)
        }

        if (!relayAvailable) {
            return CompletableFuture.completedFuture(NekoText.of("neko-translator.error.link_unavailable"))
        }

        val message = JsonObject()
        message.addProperty("t", TYPE_LINK)

        return exchange(target, message, Kind.LINK, LINK_TIMEOUT_SECONDS)
            .handle { reply, error ->
                when {
                    error != null -> describeFailure(error)
                    reply == null -> NekoText.of("neko-translator.error.peer_no_response")
                    reply.relayFailed -> NekoText.of("neko-translator.error.peer_offline")
                    !reply.ok -> NekoText.of("neko-translator.error.link_denied", name)
                    else -> {
                        config.linked[target] = name
                        onConfigChanged()
                        null
                    }
                }
            }
    }

    fun unlink(target: UUID): Boolean = config.linked.remove(target) != null

    fun allow(targets: Map<UUID, String>): Int {
        var added = 0
        for ((uuid, name) in targets) {
            config.allowed[uuid] = name
            added++
        }
        if (added > 0) onConfigChanged()
        return added
    }

    fun disallow(targets: Collection<UUID>): Int {
        var removed = 0
        for (uuid in targets) {
            if (config.allowed.remove(uuid) != null) removed++
        }
        if (removed > 0) onConfigChanged()
        return removed
    }

    /** Handles a payload that arrived through the relay. */
    fun handleIncoming(from: UUID, data: ByteArray) {
        val root = try {
            JsonParser.parseString(String(data, Charsets.UTF_8)).asJsonObject
        } catch (t: Throwable) {
            NekoLog.warn("Dropped malformed peer message from $from")
            return
        }

        when (root.string(TYPE_KEY)) {
            TYPE_REQUEST -> handleRequest(from, root)
            TYPE_RESPONSE -> handleResponse(root, Kind.REQUEST)
            TYPE_LINK -> handleLinkRequest(from, root)
            TYPE_LINK_RESPONSE -> handleResponse(root, Kind.LINK)
            else -> NekoLog.debug("Dropped unknown peer message from $from")
        }
    }

    /** The relay reported that the target could not be reached. */
    fun handleRelayFailure(token: Long) {
        val entry = pending.remove(token) ?: return
        entry.future.complete(PeerReply(false, null, denied = false, relayFailed = true))
    }

    private fun handleRequest(from: UUID, root: JsonObject) {
        val token = root.long(ID_KEY) ?: return
        val text = root.string(TEXT_KEY) ?: return
        val language = root.string(LANGUAGE_KEY) ?: "en_us"

        if (!config.allowOthers || !config.allowed.containsKey(from)) {
            reply(from, token, ok = false, text = null, denied = true)
            return
        }

        if (text.isBlank() || text.length > MAX_TEXT) {
            reply(from, token, ok = false, text = null, denied = false)
            return
        }

        coordinator().provideForPeer(text, language).whenComplete { result, _ ->
            when (result) {
                is TranslationResult.Ok -> reply(from, token, ok = true, text = result.text, denied = false)
                is TranslationResult.Err -> {
                    // The reason is only reported locally; the requester just sees "failed".
                    onLocalError(NekoText.of("neko-translator.error.serve", nameOf(from), result.message))
                    reply(from, token, ok = false, text = null, denied = false)
                }
                null -> reply(from, token, ok = false, text = null, denied = false)
            }
        }
    }

    private fun nameOf(uuid: UUID): String =
        transport.playerName(uuid) ?: uuid.toString().substring(0, 8)

    private fun handleLinkRequest(from: UUID, root: JsonObject) {
        val token = root.long(ID_KEY) ?: return
        val allowed = config.allowOthers && config.allowed.containsKey(from)
        val message = JsonObject()
        message.addProperty("t", TYPE_LINK_RESPONSE)
        message.addProperty(ID_KEY, token)
        message.addProperty("ok", allowed)
        send(0L, from, message)
    }

    private fun handleResponse(root: JsonObject, kind: Kind) {
        val token = root.long(ID_KEY) ?: return
        val entry = pending[token] ?: return
        if (entry.kind != kind) return

        pending.remove(token)
        val ok = root.get("ok")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        val text = root.string(TEXT_KEY)
        val denied = root.get("denied")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        entry.future.complete(PeerReply(ok, text, denied, relayFailed = false))
    }

    private fun reply(target: UUID, token: Long, ok: Boolean, text: String?, denied: Boolean) {
        val message = JsonObject()
        message.addProperty("t", TYPE_RESPONSE)
        message.addProperty(ID_KEY, token)
        message.addProperty("ok", ok)
        if (text != null) message.addProperty(TEXT_KEY, text)
        if (denied) message.addProperty("denied", true)
        send(0L, target, message)
    }

    private fun exchange(
        target: UUID,
        message: JsonObject,
        kind: Kind,
        timeoutSeconds: Long,
    ): CompletableFuture<PeerReply> {
        val token = tokens.incrementAndGet()
        message.addProperty(ID_KEY, token)

        val future = CompletableFuture<PeerReply>()
        pending[token] = Pending(future, kind)

        if (!send(token, target, message)) {
            pending.remove(token)
            future.complete(PeerReply(false, null, denied = false, relayFailed = true))
            return future
        }

        future.orTimeout(timeoutSeconds, TimeUnit.SECONDS).whenComplete { _, _ -> pending.remove(token) }
        return future
    }

    private fun send(token: Long, target: UUID, message: JsonObject): Boolean {
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        return transport.send(token, target, bytes)
    }

    private fun describeFailure(error: Throwable): NekoText {
        val cause = unwrap(error)
        return if (cause is TimeoutException) {
            NekoText.of("neko-translator.error.peer_timeout")
        } else {
            NekoText.of("neko-translator.error.peer_response_failed")
        }
    }

    private fun unwrap(error: Throwable): Throwable {
        var current = error
        while (current is CompletionException && current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    private data class Pending(val future: CompletableFuture<PeerReply>, val kind: Kind)

    private data class PeerReply(val ok: Boolean, val text: String?, val denied: Boolean, val relayFailed: Boolean)

    private enum class Kind { REQUEST, LINK }

    private fun JsonObject.string(key: String): String? {
        val element = this.get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    private fun JsonObject.long(key: String): Long? {
        val element = this.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return try {
            element.asLong
        } catch (t: Throwable) {
            null
        }
    }

    companion object {
        private const val TYPE_KEY = "t"
        private const val ID_KEY = "id"
        private const val TEXT_KEY = "text"
        private const val LANGUAGE_KEY = "lang"

        private const val TYPE_REQUEST = "req"
        private const val TYPE_RESPONSE = "res"
        private const val TYPE_LINK = "link"
        private const val TYPE_LINK_RESPONSE = "linkres"

        private const val MAX_TEXT = 4000
        private const val REQUEST_TIMEOUT_SECONDS = 45L
        private const val LINK_TIMEOUT_SECONDS = 30L
    }
}
