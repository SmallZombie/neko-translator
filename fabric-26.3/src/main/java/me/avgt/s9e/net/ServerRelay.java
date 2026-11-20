package me.avgt.s9e.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the peer relay. It is intentionally tiny: route a payload to one player,
 * or tell the sender that the target is unreachable. No peer data is stored or logged.
 */
public final class ServerRelay {
	/** Reason code: target is not online, is the sender, or does not have the mod. */
	public static final int REASON_UNREACHABLE = 0;
	/** Reason code: the payload was rejected by validation. */
	public static final int REASON_REJECTED = 1;

	private ServerRelay() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(RelayPayloads.ToServer.TYPE, RelayPayloads.ToServer.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RelayPayloads.ToClient.TYPE, RelayPayloads.ToClient.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RelayPayloads.Failed.TYPE, RelayPayloads.Failed.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RelayPayloads.ToServer.TYPE, (payload, context) -> {
			ServerPlayer sender = context.player();
			MinecraftServer server = context.server();

			if (payload.data() == null || payload.data().length == 0 || payload.target() == null) {
				fail(sender, payload.token(), REASON_REJECTED);
				return;
			}

			ServerPlayer target = null;

			for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
				if (candidate.getUUID().equals(payload.target())) {
					target = candidate;
					break;
				}
			}

			if (target == null || target.getUUID().equals(sender.getUUID())) {
				fail(sender, payload.token(), REASON_UNREACHABLE);
				return;
			}

			if (!ServerPlayNetworking.canSend(target, RelayPayloads.ToClient.TYPE)) {
				// The target is online but has no channel registered (mod missing on their side).
				fail(sender, payload.token(), REASON_UNREACHABLE);
				return;
			}

			ServerPlayNetworking.send(target, new RelayPayloads.ToClient(sender.getUUID(), payload.data()));
		});
	}

	private static void fail(final ServerPlayer sender, final long token, final int reason) {
		if (ServerPlayNetworking.canSend(sender, RelayPayloads.Failed.TYPE)) {
			ServerPlayNetworking.send(sender, new RelayPayloads.Failed(token, reason));
		}
	}
}
