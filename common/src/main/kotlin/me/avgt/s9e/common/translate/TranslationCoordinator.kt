package me.avgt.s9e.common.translate

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import me.avgt.s9e.common.cache.TranslationCache
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.debug.TranslationDebug
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.peer.PeerManager
import me.avgt.s9e.common.text.NekoText

/**
 * Central scheduling point for translations.
 *
 * <p>Two layers of de-duplication are applied:
 * <ul>
 *     <li>[apiJobs] guarantees that one sentence is only sent to the configured API once.
 *     Peer requests arriving while the local player translates the same sentence simply wait
 *     for that job and receive its result - success or failure.</li>
 *     <li>[routeJobs] de-duplicates the local player's own requests, including the ones that are
 *     served by a linked player.</li>
 * </ul>
 *
 * <p>Routing is strict on purpose: when delegation is enabled and a player is linked, a peer
 * failure is reported as a failure and the local API is *not* used behind the player's back.
 */
class TranslationCoordinator(
    private val config: NekoConfig,
    private val cache: TranslationCache,
    private val http: OpenAiClient,
    private val executor: ExecutorService,
    private val autoLanguage: () -> String,
    private val onCacheDirty: () -> Unit,
    private val peers: () -> PeerManager,
) {
    private val apiJobs = ConcurrentHashMap<String, CompletableFuture<TranslationResult>>()
    private val routeJobs = ConcurrentHashMap<String, CompletableFuture<TranslationResult>>()
    private val failures = ConcurrentHashMap<String, Failure>()

    /** Translates a chat line for the local player, delegating to a linked player when enabled. */
    fun requestForChat(text: String): CompletableFuture<TranslationResult> {
        val language = config.language(autoLanguage())
        val key = TranslationCache.key(text, language)
        cache.get(key)?.let { return CompletableFuture.completedFuture(TranslationResult.Ok(it)) }

        routeJobs[key]?.let { return it }

        val promise = CompletableFuture<TranslationResult>()
        val previous = routeJobs.putIfAbsent(key, promise)
        if (previous != null) return previous

        val routed = try {
            route(key, text, language)
        } catch (t: Throwable) {
            NekoLog.warn("Unexpected translation failure: ${t.javaClass.simpleName}: ${t.message}")
            CompletableFuture.completedFuture(TranslationResult.Err(NekoText.of("neko-translator.error.internal")))
        }

        routed.whenComplete { result, _ ->
            promise.complete(result ?: TranslationResult.Err(NekoText.of("neko-translator.error.translate_failed")))
            routeJobs.remove(key, promise)
        }

        return promise
    }

    /** Serves a request coming from another player. Always uses the local API so peer chains cannot loop. */
    fun provideForPeer(text: String, language: String): CompletableFuture<TranslationResult> {
        return localJob(TranslationCache.key(text, language), text, language, "serve")
    }

    /** Forgets a cached failure so the next attempt really hits the network. */
    fun forgetFailure(text: String) {
        failures.remove(TranslationCache.key(text, config.language(autoLanguage())))
    }

    fun clearFailures() {
        failures.clear()
    }

    private fun route(key: String, text: String, language: String): CompletableFuture<TranslationResult> {
        if (config.useOthers && config.linked.isNotEmpty()) {
            val manager = peers()
            val peer = manager.pickLinkedPlayer()
                ?: return CompletableFuture.completedFuture(
                    TranslationResult.Err(NekoText.of("neko-translator.error.peers_offline"))
                )

            val job = TranslationDebug.begin(key, text, language, "peer")

            return manager.translateViaPeer(peer.first, text, language).handle { result, error ->
                if (error != null) {
                    NekoLog.debug("Peer translation failed: ${error.javaClass.simpleName}: ${error.message}")
                }

                val answered = result ?: TranslationResult.Err(NekoText.of("neko-translator.error.peer_failed"))
                if (answered is TranslationResult.Ok) remember(key, answered.text)
                answered
            }.whenComplete { _, _ -> TranslationDebug.end(key) }
        }

        if (!config.hasLocalApi()) {
            return CompletableFuture.completedFuture(
                TranslationResult.Err(NekoText.of("neko-translator.error.not_configured"))
            )
        }

        return localJob(key, text, language)
    }

    private fun localJob(
        key: String,
        text: String,
        language: String,
        kind: String = "api",
    ): CompletableFuture<TranslationResult> {
        cache.get(key)?.let { return CompletableFuture.completedFuture(TranslationResult.Ok(it)) }

        failures[key]?.let { failure ->
            if (failure.expiresAt > System.currentTimeMillis()) {
                return CompletableFuture.completedFuture(TranslationResult.Err(failure.message))
            }
            failures.remove(key, failure)
        }

        apiJobs[key]?.let { return it }

        val promise = CompletableFuture<TranslationResult>()
        val previous = apiJobs.putIfAbsent(key, promise)
        if (previous != null) return previous

        val job = TranslationDebug.begin(key, text, language, kind)
        executor.execute {
            job.start()
            val result = try {
                http.translate(config.apiBaseUrl, config.apiKey, config.model, text, language, config.reasoningEffort)
            } catch (t: Throwable) {
                NekoLog.warn("Translation request crashed: ${t.javaClass.simpleName}: ${t.message}")
                TranslationResult.Err(NekoText.of("neko-translator.error.request_plain"))
            }

            when (result) {
                is TranslationResult.Ok -> remember(key, result.text)
                is TranslationResult.Err -> failures[key] = Failure(System.currentTimeMillis() + FAILURE_TTL_MILLIS, result.message)
            }

            promise.complete(result)
            apiJobs.remove(key, promise)
            TranslationDebug.end(key)
        }

        return promise
    }

    private fun remember(key: String, translation: String) {
        cache.put(key, translation)
        onCacheDirty()
    }

    private data class Failure(val expiresAt: Long, val message: NekoText)

    companion object {
        private const val FAILURE_TTL_MILLIS = 10_000L
    }
}
