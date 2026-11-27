package me.avgt.s9e.net;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Wire payloads used to relay translation traffic between players through the server.
 *
 * <p>The server never interprets {@link #data()} - it only forwards opaque bytes to the
 * target player. This keeps the peer protocol entirely client side while still allowing
 * players connected through a vanilla-style server (with this mod installed) to talk to
 * each other.
 */
public final class RelayPayloads {
	private RelayPayloads() {
	}

	/** Upper bound for the opaque peer message, small enough to stay well below packet limits. */
	public static final int MAX_DATA = 16384;

	public static final ResourceLocation CHANNEL_TO_SERVER = ResourceLocation.fromNamespaceAndPath("neko-translator", "relay_c2s");
	public static final ResourceLocation CHANNEL_TO_CLIENT = ResourceLocation.fromNamespaceAndPath("neko-translator", "relay_s2c");
	public static final ResourceLocation CHANNEL_STATUS = ResourceLocation.fromNamespaceAndPath("neko-translator", "relay_status");

	/** Client -> server: deliver {@code data} to {@code target}. {@code token} is echoed back on failure. */
	public record ToServer(long token, UUID target, byte[] data) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ToServer> TYPE = new CustomPacketPayload.Type<>(CHANNEL_TO_SERVER);

		public static final StreamCodec<RegistryFriendlyByteBuf, ToServer> CODEC = CustomPacketPayload.codec(
				(value, buf) -> value.write(buf),
				ToServer::new);

		public ToServer(final RegistryFriendlyByteBuf buf) {
			this(buf.readLong(), buf.readUUID(), buf.readByteArray(MAX_DATA));
		}

		public void write(final RegistryFriendlyByteBuf buf) {
			buf.writeLong(this.token);
			buf.writeUUID(this.target);
			buf.writeByteArray(this.data);
		}

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: {@code data} sent by {@code from}. */
	public record ToClient(UUID from, byte[] data) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ToClient> TYPE = new CustomPacketPayload.Type<>(CHANNEL_TO_CLIENT);

		public static final StreamCodec<RegistryFriendlyByteBuf, ToClient> CODEC = CustomPacketPayload.codec(
				(value, buf) -> value.write(buf),
				ToClient::new);

		public ToClient(final RegistryFriendlyByteBuf buf) {
			this(buf.readUUID(), buf.readByteArray(MAX_DATA));
		}

		public void write(final RegistryFriendlyByteBuf buf) {
			buf.writeUUID(this.from);
			buf.writeByteArray(this.data);
		}

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: the relay could not reach the target, {@code token} identifies the request. */
	public record Failed(long token, int reason) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Failed> TYPE = new CustomPacketPayload.Type<>(CHANNEL_STATUS);

		public static final StreamCodec<RegistryFriendlyByteBuf, Failed> CODEC = CustomPacketPayload.codec(
				(value, buf) -> value.write(buf),
				Failed::new);

		public Failed(final RegistryFriendlyByteBuf buf) {
			this(buf.readLong(), buf.readByte());
		}

		public void write(final RegistryFriendlyByteBuf buf) {
			buf.writeLong(this.token);
			buf.writeByte(this.reason);
		}

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
