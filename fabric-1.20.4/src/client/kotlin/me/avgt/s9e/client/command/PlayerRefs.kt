package me.avgt.s9e.client.command

import java.util.UUID
import me.avgt.s9e.common.text.NekoText
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo

/** Resolves the target argument of `/t allow`, `/t disallow`, `/t link` and `/t unlink`. */
object PlayerRefs {
    data class PlayerRef(val uuid: UUID, val name: String)

    class SelectorException(val text: NekoText) : Exception(text.key)

    fun online(): List<PlayerRef> {
        val connection = Minecraft.getInstance().connection ?: return emptyList()
        return connection.listedOnlinePlayers.mapNotNull { info ->
            val profile = info.profile
            PlayerRef(profile.id, profile.name)
        }
    }

    fun self(): PlayerRef? {
        val player = Minecraft.getInstance().player ?: return null
        return PlayerRef(player.uuid, player.gameProfile.name)
    }

    fun resolve(input: String, allowSelf: Boolean, multiple: Boolean): List<PlayerRef> {
        val tokens = input.trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) throw SelectorException(NekoText.of("neko-translator.players.select_required"))

        if (!multiple && tokens.size > 1) {
            throw SelectorException(NekoText.of("neko-translator.players.single_only"))
        }

        val result = LinkedHashMap<UUID, PlayerRef>()

        for (token in tokens) {
            when {
                token == "@a" -> {
                    for (player in online()) {
                        if (player.uuid != self()?.uuid) result[player.uuid] = player
                    }
                }

                token == "@p" || token == "@s" -> {
                    val self = self() ?: throw SelectorException(NekoText.of("neko-translator.players.no_player"))
                    if (!allowSelf) throw SelectorException(NekoText.of("neko-translator.players.no_self"))
                    result[self.uuid] = self
                }

                token == "@r" -> {
                    val candidates = online().filter { it.uuid != self()?.uuid }
                    if (candidates.isEmpty()) {
                        throw SelectorException(NekoText.of("neko-translator.players.no_others_online"))
                    }
                    val picked = candidates.random()
                    result[picked.uuid] = picked
                }

                token.startsWith("@") -> throw SelectorException(
                    NekoText.of("neko-translator.players.unsupported_selector", token)
                )

                else -> {
                    val player = byName(token) ?: byUuid(token)
                        ?: throw SelectorException(NekoText.of("neko-translator.players.not_found", token))
                    if (!allowSelf && player.uuid == self()?.uuid) {
                        throw SelectorException(NekoText.of("neko-translator.players.no_self"))
                    }
                    result[player.uuid] = player
                }
            }
        }

        if (result.isEmpty()) throw SelectorException(NekoText.of("neko-translator.players.no_match"))
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
        val name = info?.profile?.name ?: uuid.toString()
        return PlayerRef(uuid, name)
    }
}
