package me.avgt.s9e.client.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import me.avgt.s9e.client.Neko
import me.avgt.s9e.client.gui.ConfigScreen
import me.avgt.s9e.client.gui.NekoScreens
import me.avgt.s9e.client.util.Feed
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.text.NekoText
import me.avgt.s9e.common.translate.TranslationException
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft

/** Registers the client side `/t` command tree. */
object NekoCommands {
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("t")
                .executes { context ->
                    context.source.sendFeedback(Feed.infoComponent(NekoText.of("neko-translator.command.usage")))
                    1
                }
                .then(
                    ClientCommandManager.literal("config").executes { _ ->
                        NekoLog.info("/t config requested")
                        // Vanilla closes the chat screen right after a submitted command, which
                        // would throw away a screen opened during command execution, so open it
                        // again on the next tick (the immediate call covers non-chat contexts).
                        Minecraft.getInstance().setScreen(ConfigScreen())
                        NekoScreens.open { ConfigScreen() }
                        1
                    }
                )
                .then(
                    ClientCommandManager.literal("allow").then(
                        ClientCommandManager.argument("targets", StringArgumentType.greedyString())
                            .suggests(TARGET_SUGGESTIONS)
                            .executes { context -> changeAllow(context, true) }
                    )
                )
                .then(
                    ClientCommandManager.literal("disallow").then(
                        ClientCommandManager.argument("targets", StringArgumentType.greedyString())
                            .suggests(TARGET_SUGGESTIONS)
                            .executes { context -> changeAllow(context, false) }
                    )
                )
                .then(
                    ClientCommandManager.literal("link").then(
                        ClientCommandManager.argument("target", StringArgumentType.greedyString())
                            .suggests(TARGET_SUGGESTIONS)
                            .executes { context -> link(context) }
                    )
                )
                .then(
                    ClientCommandManager.literal("unlink").then(
                        ClientCommandManager.argument("target", StringArgumentType.greedyString())
                            .suggests(TARGET_SUGGESTIONS)
                            .executes { context -> unlink(context) }
                    )
                )
                .then(ClientCommandManager.literal("status").executes { context -> status(context) })
                .then(ClientCommandManager.literal("refresh").executes { context -> refreshModels(context) })
                .then(ClientCommandManager.literal("clear-cache").executes { context -> clearCache(context) })
        )
    }

    private fun changeAllow(context: CommandContext<FabricClientCommandSource>, add: Boolean): Int {
        val raw = context.getArgument("targets", String::class.java)

        try {
            // 允许时不能选自己（自己不需要委托自己）；取消允许时保留自己，方便清掉旧配置里误加的记录。
            val players = PlayerRefs.resolve(raw, allowSelf = !add, multiple = true)

            if (add) {
                val changed = Neko.peers.allow(players.associate { it.uuid to it.name })
                context.source.sendFeedback(
                    Feed.infoComponent(NekoText.of("neko-translator.command.allow.done", changed, names(players)))
                )
            } else {
                val changed = Neko.peers.disallow(players.map { it.uuid })
                context.source.sendFeedback(
                    Feed.infoComponent(NekoText.of("neko-translator.command.disallow.done", changed, names(players)))
                )
            }
        } catch (e: PlayerRefs.SelectorException) {
            context.source.sendError(Feed.errorComponent(e.text))
        }

        return 1
    }

    private fun link(context: CommandContext<FabricClientCommandSource>): Int {
        val raw = context.getArgument("target", String::class.java)

        try {
            val player = PlayerRefs.resolve(raw, allowSelf = false, multiple = false).first()

            if (Neko.config.linked.containsKey(player.uuid)) {
                context.source.sendFeedback(
                    Feed.infoComponent(NekoText.of("neko-translator.command.link.already", player.name))
                )
                return 1
            }

            context.source.sendFeedback(
                Feed.infoComponent(NekoText.of("neko-translator.command.link.requesting", player.name))
            )

            Neko.peers.link(player.uuid, player.name).whenComplete { error, throwable ->
                Minecraft.getInstance().execute {
                    if (throwable != null) {
                        Feed.error(NekoText.of("neko-translator.command.link.failed", player.name))
                    } else if (error != null) {
                        Feed.error(error)
                    } else {
                        Feed.info(NekoText.of("neko-translator.command.link.done", player.name))
                    }
                }
            }
        } catch (e: PlayerRefs.SelectorException) {
            context.source.sendError(Feed.errorComponent(e.text))
        }

        return 1
    }

    private fun unlink(context: CommandContext<FabricClientCommandSource>): Int {
        val raw = context.getArgument("target", String::class.java)

        try {
            val player = PlayerRefs.resolve(raw, allowSelf = false, multiple = false).first()
            if (Neko.peers.unlink(player.uuid)) {
                context.source.sendFeedback(
                    Feed.infoComponent(NekoText.of("neko-translator.command.unlink.done", player.name))
                )
            } else {
                context.source.sendError(
                    Feed.errorComponent(NekoText.of("neko-translator.command.unlink.missing", player.name))
                )
            }
        } catch (e: PlayerRefs.SelectorException) {
            context.source.sendError(Feed.errorComponent(e.text))
        }

        return 1
    }

    private fun status(context: CommandContext<FabricClientCommandSource>): Int {
        val config = Neko.config
        val source = context.source
        val unset = NekoText.of("neko-translator.command.status.unset")

        source.sendFeedback(Feed.infoComponent(NekoText.of("neko-translator.command.status.enabled", onOff(config.enabled))))
        source.sendFeedback(
            Feed.infoComponent(
                NekoText.of("neko-translator.command.status.api_base", if (config.apiBaseUrl.isBlank()) unset else config.apiBaseUrl)
            )
        )
        source.sendFeedback(
            Feed.infoComponent(
                NekoText.of("neko-translator.command.status.model", if (config.model.isBlank()) unset else config.model)
            )
        )
        source.sendFeedback(
            Feed.infoComponent(
                NekoText.of(
                    "neko-translator.command.status.api_key",
                    if (config.apiKey.isBlank()) unset else NekoText.of("neko-translator.command.status.set"),
                )
            )
        )
        source.sendFeedback(
            Feed.infoComponent(
                NekoText.of(
                    "neko-translator.command.status.allow_others",
                    onOff(config.allowOthers),
                    config.allowed.size,
                )
            )
        )
        source.sendFeedback(
            Feed.infoComponent(
                NekoText.of(
                    "neko-translator.command.status.use_others",
                    onOff(config.useOthers),
                    config.linked.size,
                )
            )
        )
        if (config.linked.isNotEmpty()) {
            source.sendFeedback(
                Feed.infoComponent(
                    NekoText.of("neko-translator.command.status.linked", config.linked.values.joinToString(", "))
                )
            )
        }
        source.sendFeedback(
            Feed.infoComponent(
                NekoText.of(
                    "neko-translator.command.status.auto_translate",
                    onOff(config.autoTranslate),
                    Neko.language(),
                )
            )
        )
        source.sendFeedback(Feed.infoComponent(NekoText.of("neko-translator.command.status.cache", Neko.cache.size())))
        source.sendFeedback(
            Feed.infoComponent(
                NekoText.of(
                    "neko-translator.command.status.relay",
                    if (Neko.peers.relayAvailable) {
                        NekoText.of("neko-translator.command.status.relay.available")
                    } else {
                        NekoText.of("neko-translator.command.status.relay.unavailable")
                    },
                )
            )
        )

        return 1
    }

    private fun refreshModels(context: CommandContext<FabricClientCommandSource>): Int {
        val baseUrl = Neko.config.apiBaseUrl
        val apiKey = Neko.config.apiKey
        context.source.sendFeedback(Feed.infoComponent(NekoText.of("neko-translator.status.fetching")))

        Neko.executor.execute {
            try {
                val models = Neko.http.listModels(baseUrl, apiKey)
                Neko.availableModels = models
                if (Neko.config.model.isBlank() && models.isNotEmpty()) {
                    Neko.config.model = models.first()
                    Neko.saveConfig()
                }

                Minecraft.getInstance().execute {
                    if (models.isEmpty()) {
                        Feed.info(NekoText.of("neko-translator.command.refresh.empty"))
                    } else {
                        Feed.info(
                            NekoText.of(
                                "neko-translator.command.refresh.done",
                                models.size,
                                models.take(3).joinToString(", "),
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                val message = if (t is TranslationException) t.text else NekoText.of("neko-translator.status.failed")
                Minecraft.getInstance().execute { Feed.error(message) }
            }
        }

        return 1
    }

    private fun clearCache(context: CommandContext<FabricClientCommandSource>): Int {
        val size = Neko.cache.size()
        Neko.cache.clear()
        Neko.cache.save(force = true)
        Neko.coordinator.clearFailures()
        context.source.sendFeedback(
            Feed.infoComponent(NekoText.of("neko-translator.command.clear_cache.done", size))
        )
        return 1
    }

    private fun names(players: List<PlayerRefs.PlayerRef>): Any {
        val shown = players.take(10).joinToString(", ") { it.name }
        return if (players.size > 10) {
            NekoText.of("neko-translator.players.names_more", shown, players.size)
        } else {
            shown
        }
    }

    private fun onOff(value: Boolean): NekoText =
        NekoText.of(if (value) "neko-translator.command.on" else "neko-translator.command.off")

    /** Player name / selector completion for `allow`, `disallow`, `link` and `unlink`. */
    private val TARGET_SUGGESTIONS = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        suggestTargets(builder)
    }

    private fun suggestTargets(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val remaining = builder.remaining
        val separator = maxOf(remaining.lastIndexOf(','), remaining.lastIndexOf(' '))
        val prefix = if (separator >= 0) remaining.substring(0, separator + 1) else ""
        val tail = remaining.substring(prefix.length)
        val trimmed = tail.trimStart()
        val offset = builder.start + prefix.length + (tail.length - trimmed.length)
        val target = builder.createOffset(offset)

        if (trimmed.isEmpty()) {
            target.suggest("@a")
            target.suggest("@p")
            target.suggest("@r")
        }

        for (player in PlayerRefs.online()) {
            if (trimmed.isEmpty() || player.name.startsWith(trimmed, ignoreCase = true)) {
                target.suggest(player.name)
            }
        }

        return target.buildFuture()
    }
}
