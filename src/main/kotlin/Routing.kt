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
         * Apply grain effect to the proxy or loupe image with the given parameters.
         * Query params: mode=proxy|loupe, x=0.0-1.0, y=0.0-1.0
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
                val mode = call.request.queryParameters["mode"] ?: "proxy"

                if (mode == "loupe") {
                    val x = (call.request.queryParameters["x"] ?: "0.5").toDouble().coerceIn(0.0, 1.0)
                    val y = (call.request.queryParameters["y"] ?: "0.5").toDouble().coerceIn(0.0, 1.0)

                    val fullImage = session.fullImage
                    val cropSize = 800
                    val startX = ((fullImage.width * x) - cropSize / 2).toInt().coerceIn(0, (fullImage.width - cropSize).coerceAtLeast(0))
                    val startY = ((fullImage.height * y) - cropSize / 2).toInt().coerceIn(0, (fullImage.height - cropSize).coerceAtLeast(0))
                    val actualW = minOf(cropSize, fullImage.width - startX)
                    val actualH = minOf(cropSize, fullImage.height - startY)

                    val crop = fullImage.getSubimage(startX, startY, actualW, actualH)
                    // Use exact params — no scaling for loupe (1:1 pixel view)
                    val processed = ImageProcessingService.applyGrain(crop, params)
                    val jpegBytes = ImageStore.toJpegBytes(processed)

                    call.respondBytes(jpegBytes, ContentType.Image.JPEG)
                } else {
                    // Proxy mode: scale grain size proportionally
                    val scale = session.proxyImage.width.toDouble() / session.fullImage.width.toDouble()
                    val scaledParams = params.copy(size = params.size * scale)

                    val processed = ImageProcessingService.applyGrain(session.proxyImage, scaledParams)
                    val jpegBytes = ImageStore.toJpegBytes(processed)

                    call.respondBytes(jpegBytes, ContentType.Image.JPEG)
                }
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
