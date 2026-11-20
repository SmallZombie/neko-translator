package me.avgt.s9e.common.peer

import java.util.UUID

/**
 * Loader/version specific transport used by [PeerManager].
 *
 * <p>Implementations only move opaque bytes to another player and answer presence questions;
 * all protocol handling stays in the common module.
 */
interface PeerTransport {
    /** Whether the current connection can relay messages (the server must have the mod). */
    fun isAvailable(): Boolean

    /** Whether the player is currently online on this connection. */
    fun isOnline(player: UUID): Boolean

    /** Current name of the player, or {@code null} when the name is unknown. */
    fun playerName(player: UUID): String?

    /**
     * Sends [message] to [target]. [token] is echoed back by the platform when delivery fails,
     * so the caller can fail the matching pending exchange.
     *
     * @return false when the message could not be handed to the transport at all
     */
    fun send(token: Long, target: UUID, message: ByteArray): Boolean
}
