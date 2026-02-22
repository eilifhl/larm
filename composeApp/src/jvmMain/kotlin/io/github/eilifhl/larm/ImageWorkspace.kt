package io.github.eilifhl.larm

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToInt

class ImageWorkspace(
    val proxyWidth: Int,
    val proxyHeight: Int,
    val proxyScaleFactor: Double,
    val loupeWidth: Int,
    val loupeHeight: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val proxyInputBuffer: ByteBuffer,
    val proxyOutputBuffer: ByteBuffer,
    val loupeInputBuffer: ByteBuffer,
    val loupeOutputBuffer: ByteBuffer,
    val originalImage: BufferedImage,
) {
    companion object {
        private const val MAX_PROXY_WIDTH = 1200
        private const val LOUPE_SIZE = 512

        fun load(file: File): ImageWorkspace {
            val original = ImageIO.read(file)
                ?: throw IllegalArgumentException("Could not read image: ${file.path}")

            val origW = original.width
            val origH = original.height

            // --- Proxy ---
            val scaleFactor = if (origW > MAX_PROXY_WIDTH) {
                MAX_PROXY_WIDTH.toDouble() / origW
            } else {
                1.0
            }
            val proxyW = (origW * scaleFactor).roundToInt()
            val proxyH = (origH * scaleFactor).roundToInt()

            val proxyImage = BufferedImage(proxyW, proxyH, BufferedImage.TYPE_INT_RGB)
            val g2d = proxyImage.createGraphics()
            g2d.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            g2d.drawImage(original, 0, 0, proxyW, proxyH, null)
            g2d.dispose()

            val proxyByteSize = proxyW * proxyH * 3
            val proxyInputBuf = ByteBuffer.allocateDirect(proxyByteSize)
            val proxyOutputBuf = ByteBuffer.allocateDirect(proxyByteSize)
            fillBufferFromImage(proxyImage, proxyInputBuf)

            // --- Loupe (center crop at full resolution) ---
            val loupeW = min(LOUPE_SIZE, origW)
            val loupeH = min(LOUPE_SIZE, origH)
            val loupeX = (origW - loupeW) / 2
            val loupeY = (origH - loupeH) / 2

            val loupeImage = original.getSubimage(loupeX, loupeY, loupeW, loupeH)
            val loupeCopy = BufferedImage(loupeW, loupeH, BufferedImage.TYPE_INT_RGB)
            loupeCopy.graphics.drawImage(loupeImage, 0, 0, null)

            val loupeByteSize = loupeW * loupeH * 3
            val loupeInputBuf = ByteBuffer.allocateDirect(loupeByteSize)
            val loupeOutputBuf = ByteBuffer.allocateDirect(loupeByteSize)
            fillBufferFromImage(loupeCopy, loupeInputBuf)

            return ImageWorkspace(
                proxyWidth = proxyW,
                proxyHeight = proxyH,
                proxyScaleFactor = scaleFactor,
                loupeWidth = loupeW,
                loupeHeight = loupeH,
                originalWidth = origW,
                originalHeight = origH,
                proxyInputBuffer = proxyInputBuf,
                proxyOutputBuffer = proxyOutputBuf,
                loupeInputBuffer = loupeInputBuf,
                loupeOutputBuffer = loupeOutputBuf,
                originalImage = original,
            )
        }

        private fun fillBufferFromImage(image: BufferedImage, buffer: ByteBuffer) {
            buffer.clear()
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val rgb = image.getRGB(x, y)
                    buffer.put(((rgb shr 16) and 0xFF).toByte())
                    buffer.put(((rgb shr 8) and 0xFF).toByte())
                    buffer.put((rgb and 0xFF).toByte())
                }
            }
            buffer.rewind()
        }
    }
}