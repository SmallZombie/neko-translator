package me.avgt.s9e.net;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the peer relay. It is intentionally tiny: route a message to one player, or
 * tell the sender that the target is unreachable. No peer data is stored or logged.
 */
public final class ServerRelay {
	/** Reason code: target is not online, is the sender, or does not have the mod. */
	public static final int REASON_UNREACHABLE = 0;
	/** Reason code: the payload was rejected by validation. */
	public static final int REASON_REJECTED = 1;

	private ServerRelay() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(RelayChannels.TO_SERVER, (server, sender, handler, buf, responseSender) -> {
			long token = buf.readLong();
			UUID target = buf.readUUID();
			byte[] data = buf.readByteArray(RelayChannels.MAX_DATA);

			// The handler runs on the network thread, so the actual routing is moved to the
			// server thread.
			server.execute(() -> forward(server, sender, token, target, data));
		});
	}

	private static void forward(
			final MinecraftServer server,
			final ServerPlayer sender,
			final long token,
			final UUID targetId,
			final byte[] data) {
		if (data.length == 0) {
			fail(sender, token, REASON_REJECTED);
			return;
		}

		ServerPlayer target = null;

		for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
			if (candidate.getUUID().equals(targetId)) {
				target = candidate;
				break;
			}
		}

		if (target == null || target.getUUID().equals(sender.getUUID())) {
			fail(sender, token, REASON_UNREACHABLE);
			return;
		}

		if (!ServerPlayNetworking.canSend(target, RelayChannels.TO_CLIENT)) {
			// The target is online but has no channel registered (mod missing on their side).
			fail(sender, token, REASON_UNREACHABLE);
			return;
		}

		FriendlyByteBuf out = PacketByteBufs.create();
		out.writeUUID(sender.getUUID());
		out.writeByteArray(data);
		ServerPlayNetworking.send(target, RelayChannels.TO_CLIENT, out);
	}

	private static void fail(final ServerPlayer sender, final long token, final int reason) {
		if (!ServerPlayNetworking.canSend(sender, RelayChannels.STATUS)) {
			return;
		}

		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeLong(token);
		buf.writeByte(reason);
		ServerPlayNetworking.send(sender, RelayChannels.STATUS, buf);
	}
}
