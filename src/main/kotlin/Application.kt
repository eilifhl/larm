package io.github.eilifhl

import io.github.eilifhl.larm.GrainProcessor
import io.ktor.server.application.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Application")

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    try {
        logger.info("✓ Native library 'larm' loaded successfully!")
    } catch (e: UnsatisfiedLinkError) {
        logger.error("✗ Failed to load native library 'larm': ${e.message}")
        logger.error("  Make sure liblarm.so is in the libs/ folder")
        throw e
    }

    configureSockets()
    configureSerialization()
    configureRouting()
}
