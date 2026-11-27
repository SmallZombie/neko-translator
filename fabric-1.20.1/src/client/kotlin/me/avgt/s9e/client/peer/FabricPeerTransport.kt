package me.avgt.s9e.client.peer

import java.util.UUID
import me.avgt.s9e.common.log.NekoLog
import me.avgt.s9e.common.peer.PeerTransport
import me.avgt.s9e.net.RelayChannels
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf

/** Fabric implementation of the peer transport: custom channels relayed by the server. */
object FabricPeerTransport : PeerTransport {
    override fun isAvailable(): Boolean = ClientPlayNetworking.canSend(RelayChannels.TO_SERVER)

    override fun isOnline(player: UUID): Boolean =
        Minecraft.getInstance().connection?.getPlayerInfo(player) != null

    override fun playerName(player: UUID): String? =
        Minecraft.getInstance().connection?.getPlayerInfo(player)?.profile?.name

    override fun send(token: Long, target: UUID, message: ByteArray): Boolean {
        if (message.size > RelayChannels.MAX_DATA) {
            NekoLog.warn("Peer message too large (${message.size} bytes)")
            return false
        }

        val minecraft = Minecraft.getInstance()

        if (!minecraft.isSameThread) {
            minecraft.execute { deliver(token, target, message) }
            return true
        }

        return try {
            ClientPlayNetworking.send(RelayChannels.TO_SERVER, buffer(token, target, message))
            true
        } catch (t: Throwable) {
            NekoLog.warn("Failed to send peer message: ${t.message}")
            false
        }
    }

    private fun deliver(token: Long, target: UUID, message: ByteArray) {
        try {
            ClientPlayNetworking.send(RelayChannels.TO_SERVER, buffer(token, target, message))
        } catch (t: Throwable) {
            NekoLog.warn("Failed to send peer message: ${t.message}")
        }
    }

    private fun buffer(token: Long, target: UUID, message: ByteArray): FriendlyByteBuf {
        val buf = PacketByteBufs.create()
        buf.writeLong(token)
        buf.writeUUID(target)
        buf.writeByteArray(message)
        return buf
    }
}
