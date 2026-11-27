package me.avgt.s9e.client.peer

import java.util.UUID
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.peer.PeerTransport
import me.avgt.s9e.net.RelayPayloads
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

/** Fabric implementation of the peer transport: custom payloads relayed by the server. */
object FabricPeerTransport : PeerTransport {
    override fun isAvailable(): Boolean = try {
        ClientPlayNetworking.canSend(RelayPayloads.CHANNEL_TO_SERVER)
    } catch (t: Throwable) {
        false
    }

    override fun isOnline(player: UUID): Boolean =
        Minecraft.getInstance().connection?.getPlayerInfo(player) != null

    override fun playerName(player: UUID): String? =
        Minecraft.getInstance().connection?.getPlayerInfo(player)?.profile?.name

    override fun send(token: Long, target: UUID, message: ByteArray): Boolean {
        if (message.size > RelayPayloads.MAX_DATA) {
            NekoLog.warn("Peer message too large (${message.size} bytes)")
            return false
        }

        val payload = RelayPayloads.ToServer(token, target, message)
        val minecraft = Minecraft.getInstance()

        if (!minecraft.isSameThread) {
            minecraft.execute { deliver(payload) }
            return true
        }

        return try {
            ClientPlayNetworking.send(payload)
            true
        } catch (t: Throwable) {
            NekoLog.warn("Failed to send peer message: ${t.message}")
            false
        }
    }

    private fun deliver(payload: RelayPayloads.ToServer) {
        try {
            ClientPlayNetworking.send(payload)
        } catch (t: Throwable) {
            NekoLog.warn("Failed to send peer message: ${t.message}")
        }
    }
}
