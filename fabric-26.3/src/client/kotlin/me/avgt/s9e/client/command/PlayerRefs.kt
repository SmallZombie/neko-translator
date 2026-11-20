package me.avgt.s9e.client.command

import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo

/** Resolves the target argument of `/t allow`, `/t disallow`, `/t link` and `/t unlink`. */
object PlayerRefs {
    data class PlayerRef(val uuid: UUID, val name: String)

    class SelectorException(message: String) : Exception(message)

    fun online(): List<PlayerRef> {
        val connection = Minecraft.getInstance().connection ?: return emptyList()
        return connection.listedOnlinePlayers.mapNotNull { info ->
            val profile = info.profile
            PlayerRef(profile.id(), profile.name())
        }
    }

    fun self(): PlayerRef? {
        val player = Minecraft.getInstance().player ?: return null
        return PlayerRef(player.uuid, player.gameProfile.name())
    }

    fun resolve(input: String, allowSelf: Boolean, multiple: Boolean): List<PlayerRef> {
        val tokens = input.trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) throw SelectorException("请指定玩家")

        if (!multiple && tokens.size > 1) throw SelectorException("该命令只能指定一名玩家")

        val result = LinkedHashMap<UUID, PlayerRef>()

        for (token in tokens) {
            when {
                token == "@a" -> {
                    for (player in online()) {
                        if (player.uuid != self()?.uuid) result[player.uuid] = player
                    }
                }

                token == "@p" || token == "@s" -> {
                    val self = self() ?: throw SelectorException("当前没有可用的玩家")
                    if (!allowSelf) throw SelectorException("不能对自己使用该命令")
                    result[self.uuid] = self
                }

                token == "@r" -> {
                    val candidates = online().filter { it.uuid != self()?.uuid }
                    if (candidates.isEmpty()) throw SelectorException("没有其他在线玩家")
                    val picked = candidates.random()
                    result[picked.uuid] = picked
                }

                token.startsWith("@") -> throw SelectorException("不支持的玩家选择器 $token，请使用玩家名、@a、@p、@r 或 UUID")

                else -> {
                    val player = byName(token) ?: byUuid(token)
                        ?: throw SelectorException("找不到玩家 $token（仅支持在线玩家名或 UUID）")
                    if (!allowSelf && player.uuid == self()?.uuid) throw SelectorException("不能对自己使用该命令")
                    result[player.uuid] = player
                }
            }
        }

        if (result.isEmpty()) throw SelectorException("没有匹配到任何玩家")
        return result.values.toList()
    }

    private fun byName(name: String): PlayerRef? {
        val players = online()
        return players.firstOrNull { it.name == name }
            ?: players.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun byUuid(raw: String): PlayerRef? {
        val uuid = try {
            UUID.fromString(raw)
        } catch (t: Throwable) {
            return null
        }

        val connection = Minecraft.getInstance().connection
        val info: PlayerInfo? = connection?.getPlayerInfo(uuid)
        val name = info?.profile?.name() ?: uuid.toString()
        return PlayerRef(uuid, name)
    }
}
