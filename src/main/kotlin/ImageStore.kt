package io.github.eilifhl

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Stores uploaded images in memory for processing.
 * In production, you might want to use disk storage or a cache with TTL.
 */
object ImageStore {
    private const val MAX_PROXY_WIDTH = 1200

    data class ImageSession(
        val id: String,
        val fullImage: BufferedImage,
        val proxyImage: BufferedImage
    )

    private val sessions = ConcurrentHashMap<String, ImageSession>()

    /**
     * Store an uploaded image and create a proxy version for fast preview.
     */
    fun storeImage(imageBytes: ByteArray): ImageSession {
        val sessionId = UUID.randomUUID().toString()
        val fullImage = ImageIO.read(ByteArrayInputStream(imageBytes))
            ?: throw IllegalArgumentException("Could not decode image")

        val proxyImage = createProxy(fullImage)

        val session = ImageSession(sessionId, fullImage, proxyImage)
        sessions[sessionId] = session
        return session
    }

    /**
     * Get an existing session by ID.
     */
    fun getSession(sessionId: String): ImageSession? = sessions[sessionId]

    /**
     * Remove a session (cleanup).
     */
    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    /**
     * Create a scaled-down proxy image for fast preview rendering.
     */
    private fun createProxy(original: BufferedImage): BufferedImage {
        if (original.width <= MAX_PROXY_WIDTH) {
            // Image is already small enough, return a copy
            return copyImage(original)
        }

        val scale = MAX_PROXY_WIDTH.toDouble() / original.width
        val newWidth = MAX_PROXY_WIDTH
        val newHeight = (original.height * scale).roundToInt()

        val proxy = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = proxy.createGraphics()
        g2d.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null)
        g2d.dispose()

        return proxy
    }

    private fun copyImage(original: BufferedImage): BufferedImage {
        val copy = BufferedImage(original.width, original.height, BufferedImage.TYPE_INT_ARGB)
        val g2d = copy.createGraphics()
        g2d.drawImage(original, 0, 0, null)
        g2d.dispose()
        return copy
    }

    /**
     * Convert a BufferedImage to JPEG bytes.
     */
    fun toJpegBytes(image: BufferedImage, quality: Float = 0.85f): ByteArray {
        val output = ByteArrayOutputStream()

        // Convert to RGB if necessary (JPEG doesn't support alpha)
        val rgbImage = if (image.type == BufferedImage.TYPE_INT_ARGB) {
            val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val g2d = rgb.createGraphics()
            g2d.drawImage(image, 0, 0, null)
            g2d.dispose()
            rgb
        } else {
            image
        }

        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val param = writer.defaultWriteParam
        param.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = quality

        writer.output = ImageIO.createImageOutputStream(output)
        writer.write(null, javax.imageio.IIOImage(rgbImage, null, null), param)
        writer.dispose()

        return output.toByteArray()
    }

    /**
     * Convert a BufferedImage to PNG bytes (for full quality export).
     */
    fun toPngBytes(image: BufferedImage): ByteArray {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}

