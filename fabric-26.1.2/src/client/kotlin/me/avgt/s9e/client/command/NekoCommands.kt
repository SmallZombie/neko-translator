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
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft

/** Registers the client side `/t` command tree. */
object NekoCommands {
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommands.literal("t")
                .executes { context ->
                    context.source.sendFeedback(Feed.infoComponent("用法：/t config | allow <玩家> | disallow <玩家> | link <玩家> | unlink <玩家> | status | refresh | clear-cache"))
                    1
                }
                .then(
                    ClientCommands.literal("config").executes { _ ->
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
                    ClientCommands.literal("allow").then(
                        ClientCommands.argument("targets", StringArgumentType.greedyString())
                            .suggests(TARGET_SUGGESTIONS)
                            .executes { context -> changeAllow(context, true) }
                    )
                )
                .then(
                    ClientCommands.literal("disallow").then(
                        ClientCommands.argument("targets", StringArgumentType.greedyString())
                            .suggests(TARGET_SUGGESTIONS)
                            .executes { context -> changeAllow(context, false) }
                    )
                )
                .then(
                    ClientCommands.literal("link").then(
                        ClientCommands.argument("target", StringArgumentType.greedyString())
                            .suggests(TARGET_SUGGESTIONS)
                            .executes { context -> link(context) }
                    )
                )
                .then(
                    ClientCommands.literal("unlink").then(
                        ClientCommands.argument("target", StringArgumentType.greedyString())
                            .suggests(TARGET_SUGGESTIONS)
                            .executes { context -> unlink(context) }
                    )
                )
                .then(ClientCommands.literal("status").executes { context -> status(context) })
                .then(ClientCommands.literal("refresh").executes { context -> refreshModels(context) })
                .then(ClientCommands.literal("clear-cache").executes { context -> clearCache(context) })
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
                    Feed.infoComponent("已允许 ${changed} 名玩家通过你获取翻译：${names(players)}")
                )
            } else {
                val changed = Neko.peers.disallow(players.map { it.uuid })
                context.source.sendFeedback(
                    Feed.infoComponent("已取消 ${changed} 名玩家的许可：${names(players)}")
                )
            }
        } catch (e: PlayerRefs.SelectorException) {
            context.source.sendError(Feed.errorComponent(e.message ?: "参数错误"))
        }

        return 1
    }

    private fun link(context: CommandContext<FabricClientCommandSource>): Int {
        val raw = context.getArgument("target", String::class.java)

        try {
            val player = PlayerRefs.resolve(raw, allowSelf = false, multiple = false).first()

            if (Neko.config.linked.containsKey(player.uuid)) {
                context.source.sendFeedback(Feed.infoComponent("你已经链接了 ${player.name}"))
                return 1
            }

            context.source.sendFeedback(Feed.infoComponent("正在请求与 ${player.name} 建立链接…"))

            Neko.peers.link(player.uuid, player.name).whenComplete { error, throwable ->
                Minecraft.getInstance().execute {
                    if (throwable != null) {
                        Feed.error("链接 ${player.name} 失败")
                    } else if (error != null) {
                        Feed.error(error)
                    } else {
                        Feed.info("已链接 ${player.name}")
                    }
                }
            }
        } catch (e: PlayerRefs.SelectorException) {
            context.source.sendError(Feed.errorComponent(e.message ?: "参数错误"))
        }

        return 1
    }

    private fun unlink(context: CommandContext<FabricClientCommandSource>): Int {
        val raw = context.getArgument("target", String::class.java)

        try {
            val player = PlayerRefs.resolve(raw, allowSelf = false, multiple = false).first()
            if (Neko.peers.unlink(player.uuid)) {
                context.source.sendFeedback(Feed.infoComponent("已取消与 ${player.name} 的链接"))
            } else {
                context.source.sendError(Feed.errorComponent("你没有链接 ${player.name}"))
            }
        } catch (e: PlayerRefs.SelectorException) {
            context.source.sendError(Feed.errorComponent(e.message ?: "参数错误"))
        }

        return 1
    }

    private fun status(context: CommandContext<FabricClientCommandSource>): Int {
        val config = Neko.config
        val source = context.source

        source.sendFeedback(Feed.infoComponent("总开关：${onOff(config.enabled)}"))
        source.sendFeedback(Feed.infoComponent("API 地址：${config.apiBaseUrl.ifBlank { "未设置" }}"))
        source.sendFeedback(Feed.infoComponent("模型：${config.model.ifBlank { "未设置" }}"))
        source.sendFeedback(Feed.infoComponent("API 密钥：${if (config.apiKey.isBlank()) "未设置" else "已设置"}"))
        source.sendFeedback(Feed.infoComponent("允许他人委托我翻译：${onOff(config.allowOthers)}，已允许 ${config.allowed.size} 人"))
        source.sendFeedback(Feed.infoComponent("委托他人获取翻译：${onOff(config.useOthers)}，已链接 ${config.linked.size} 人"))
        if (config.linked.isNotEmpty()) {
            source.sendFeedback(Feed.infoComponent("链接列表：${config.linked.values.joinToString(", ")}"))
        }
        source.sendFeedback(Feed.infoComponent("自动翻译：${onOff(config.autoTranslate)}，目标语言：${Neko.language()}"))
        source.sendFeedback(Feed.infoComponent("缓存：${Neko.cache.size()} 条"))
        source.sendFeedback(Feed.infoComponent("服务器中继：${if (Neko.peers.relayAvailable) "可用" else "不可用 (服务器未安装模组)"}"))

        return 1
    }

    private fun refreshModels(context: CommandContext<FabricClientCommandSource>): Int {
        val baseUrl = Neko.config.apiBaseUrl
        val apiKey = Neko.config.apiKey
        context.source.sendFeedback(Feed.infoComponent("正在获取模型列表…"))

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
                        Feed.info("接口没有返回任何模型")
                    } else {
                        Feed.info("已获取 ${models.size} 个模型，例如 ${models.take(3).joinToString(", ")}")
                    }
                }
            } catch (t: Throwable) {
                Minecraft.getInstance().execute { Feed.error(t.message ?: "获取模型列表失败") }
            }
        }

        return 1
    }

    private fun clearCache(context: CommandContext<FabricClientCommandSource>): Int {
        val size = Neko.cache.size()
        Neko.cache.clear()
        Neko.cache.save(force = true)
        Neko.coordinator.clearFailures()
        context.source.sendFeedback(Feed.infoComponent("已清空 $size 条翻译缓存"))
        return 1
    }

    private fun names(players: List<PlayerRefs.PlayerRef>): String {
        val shown = players.take(10).joinToString(", ") { it.name }
        return if (players.size > 10) "$shown 等 ${players.size} 人" else shown
    }

    private fun onOff(value: Boolean): String = if (value) "开" else "关"

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
