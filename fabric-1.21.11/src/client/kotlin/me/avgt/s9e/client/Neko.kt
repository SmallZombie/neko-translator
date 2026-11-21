package me.avgt.s9e.client

import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import me.avgt.s9e.client.book.BookTranslator
import me.avgt.s9e.client.chat.ChatTranslator
import me.avgt.s9e.client.item.ItemTranslator
import me.avgt.s9e.client.peer.FabricPeerTransport
import me.avgt.s9e.client.util.Feed
import me.avgt.s9e.common.cache.TranslationCache
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.peer.PeerManager
import me.avgt.s9e.common.translate.OpenAiClient
import me.avgt.s9e.common.translate.TranslationCoordinator
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft

/**
 * Process wide state of the client mod: settings, cache, HTTP client, peer layer and the
 * chat translator. Only the wiring lives here - the logic itself is in the common module.
 */
object Neko {
    private var initialized = false

    /** False until [init] finished; mixin callbacks can fire while the game is still starting. */
    val ready: Boolean get() = initialized && this::items.isInitialized

    lateinit var config: NekoConfig
        private set
    lateinit var cache: TranslationCache
        private set
    lateinit var http: OpenAiClient
        private set
    lateinit var coordinator: TranslationCoordinator
        private set
    lateinit var peers: PeerManager
        private set
    lateinit var chat: ChatTranslator
        private set
    lateinit var books: BookTranslator
        private set
    lateinit var items: ItemTranslator
        private set

    /** Latest model list fetched from the configured endpoint. */
    @Volatile
    var availableModels: List<String> = emptyList()

    val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(3) { runnable ->
        Thread(runnable, "Neko-Translator").apply { isDaemon = true }
    }

    private lateinit var configPath: Path
    private lateinit var cachePath: Path
    private var configSaveTask: ScheduledFuture<*>? = null
    private var cacheSaveTask: ScheduledFuture<*>? = null

    fun init() {
        if (initialized) return
        initialized = true

        val directory = FabricLoader.getInstance().configDir
        configPath = directory.resolve("neko-translator.json")
        cachePath = directory.resolve("neko-translator-cache.json")

        config = NekoConfig.load(configPath)
        cache = TranslationCache(cachePath)
        cache.load()
        http = OpenAiClient()

        peers = PeerManager(config, FabricPeerTransport, { saveConfig() }, { Feed.error(it) }) { coordinator }
        coordinator = TranslationCoordinator(
            config,
            cache,
            http,
            executor,
            { clientLanguage() },
            { scheduleCacheSave() },
        ) { peers }
        chat = ChatTranslator(config, coordinator)
        books = BookTranslator(config, coordinator)
        items = ItemTranslator(config, coordinator) { language() }

        NekoLog.info("Neko Translator loaded (cache entries: ${cache.size()})")
    }

    /** Effective target language of the local client. */
    fun language(): String = config.language(clientLanguage())

    private fun clientLanguage(): String = Minecraft.getInstance().options.languageCode.lowercase()

    fun saveConfig(immediate: Boolean = false) {
        if (!initialized) return

        if (immediate) {
            synchronized(this) {
                configSaveTask?.cancel(false)
                configSaveTask = null
                NekoConfig.save(configPath, config)
            }
            return
        }

        synchronized(this) {
            if (configSaveTask != null) return
            configSaveTask = executor.schedule({
                synchronized(this) {
                    configSaveTask = null
                    NekoConfig.save(configPath, config)
                }
            }, 1L, TimeUnit.SECONDS)
        }
    }

    fun scheduleCacheSave() {
        if (!initialized) return
        synchronized(this) {
            if (cacheSaveTask != null) return
            cacheSaveTask = executor.schedule({
                synchronized(this) { cacheSaveTask = null }
                cache.save()
            }, 5L, TimeUnit.SECONDS)
        }
    }

    fun shutdown() {
        if (!initialized) return
        try {
            NekoConfig.save(configPath, config)
        } catch (t: Throwable) {
            NekoLog.warn("Failed to save config on shutdown: ${t.message}")
        }
        try {
            cache.save(force = true)
        } catch (t: Throwable) {
            NekoLog.warn("Failed to save cache on shutdown: ${t.message}")
        }
        executor.shutdownNow()
    }
}
