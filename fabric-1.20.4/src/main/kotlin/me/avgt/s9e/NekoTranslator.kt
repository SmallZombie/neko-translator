package me.avgt.s9e

import me.avgt.s9e.net.ServerRelay
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

object NekoTranslator : ModInitializer {
	const val MOD_ID: String = "neko-translator"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		// Registers the relay channels on both sides and installs the server side relay.
		// The relay never inspects the payload: it only forwards bytes between players.
		ServerRelay.register()

		LOGGER.info("Neko Translator relay ready")
	}

	fun id(path: String): ResourceLocation = ResourceLocation(MOD_ID, path)
}
