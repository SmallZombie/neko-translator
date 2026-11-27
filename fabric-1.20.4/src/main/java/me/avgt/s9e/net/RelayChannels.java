package me.avgt.s9e.net;

import net.minecraft.resources.ResourceLocation;

/**
 * Channel ids of the peer relay.
 *
 * <p>The server never interprets the bytes it forwards: it only routes them to the target
 * player. This keeps the peer protocol entirely client side while still allowing players
 * connected through a vanilla-style server (with this mod installed) to talk to each other.
 */
public final class RelayChannels {
	private RelayChannels() {
	}

	/** Upper bound for the opaque peer message, small enough to stay well below packet limits. */
	public static final int MAX_DATA = 16384;

	public static final ResourceLocation TO_SERVER = new ResourceLocation("neko-translator", "relay_c2s");
	public static final ResourceLocation TO_CLIENT = new ResourceLocation("neko-translator", "relay_s2c");
	public static final ResourceLocation STATUS = new ResourceLocation("neko-translator", "relay_status");
}
