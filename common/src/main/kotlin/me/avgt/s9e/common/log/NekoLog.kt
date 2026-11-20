package me.avgt.s9e.common.log

import org.slf4j.LoggerFactory

/** Loader independent logger; the platform modules only supply the slf4j implementation. */
object NekoLog {
    private val logger = LoggerFactory.getLogger("neko-translator")

    fun info(message: String) = logger.info(message)

    fun warn(message: String) = logger.warn(message)

    fun debug(message: String) = logger.debug(message)
}
