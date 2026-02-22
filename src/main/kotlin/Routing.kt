package io.github.eilifhl

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Routing")

fun Application.configureRouting() {
    routing {
        // Serve static files from resources/static
        staticResources("/", "static")

        get("/health") {
            call.respondText("OK")
        }

        /**
         * POST /upload
         * Upload an image file. Returns session info and a proxy image for preview.
         */
        post("/upload") {
            try {
                val multipart = call.receiveMultipart()
                var imageBytes: ByteArray? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            imageBytes = part.provider().toByteArray()
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                val bytes = imageBytes ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "No image file provided"
                )

                val session = ImageStore.storeImage(bytes)
                logger.info("Image uploaded: session=${session.id}, full=${session.fullImage.width}x${session.fullImage.height}, proxy=${session.proxyImage.width}x${session.proxyImage.height}")

                call.respond(
                    UploadResponse(
                        sessionId = session.id,
                        width = session.fullImage.width,
                        height = session.fullImage.height,
                        proxyWidth = session.proxyImage.width,
                        proxyHeight = session.proxyImage.height
                    )
                )
            } catch (e: Exception) {
                logger.error("Upload failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${e.message}")
            }
        }

        /**
         * GET /proxy/{sessionId}
         * Get the proxy image without any grain applied (for initial display).
         */
        get("/proxy/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing sessionId")

            val session = ImageStore.getSession(sessionId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")

            val jpegBytes = ImageStore.toJpegBytes(session.proxyImage)
            call.respondBytes(jpegBytes, ContentType.Image.JPEG)
        }

        /**
         * POST /preview/{sessionId}
         * Apply grain effect to the proxy image with the given parameters.
         * Request body: JSON GrainParams
         * Response: JPEG image bytes
         */
        post("/preview/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sessionId")

            val session = ImageStore.getSession(sessionId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Session not found")

            try {
                val params = call.receive<GrainParams>()

                val processed = ImageProcessingService.applyGrain(session.proxyImage, params)
                val jpegBytes = ImageStore.toJpegBytes(processed)

                call.respondBytes(jpegBytes, ContentType.Image.JPEG)
            } catch (e: Exception) {
                logger.error("Preview failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Preview failed: ${e.message}")
            }
        }

        /**
         * POST /export/{sessionId}
         * Apply grain effect to the full-resolution image and return it.
         * Request body: JSON GrainParams
         * Response: PNG image bytes (full quality)
         */
        post("/export/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing sessionId")

            val session = ImageStore.getSession(sessionId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Session not found")

            try {
                val params = call.receive<GrainParams>()

                logger.info("Exporting full resolution image: ${session.fullImage.width}x${session.fullImage.height}")
                val processed = ImageProcessingService.applyGrain(session.fullImage, params)
                val pngBytes = ImageStore.toPngBytes(processed)

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "grain_export.png"
                    ).toString()
                )
                call.respondBytes(pngBytes, ContentType.Image.PNG)
            } catch (e: Exception) {
                logger.error("Export failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Export failed: ${e.message}")
            }
        }

        /**
         * DELETE /session/{sessionId}
         * Clean up a session and free memory.
         */
        delete("/session/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing sessionId")

            ImageStore.removeSession(sessionId)
            call.respond(HttpStatusCode.OK, "Session removed")
        }
    }
}
