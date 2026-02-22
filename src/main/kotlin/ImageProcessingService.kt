package io.github.eilifhl

import io.github.eilifhl.larm.GrainProcessor
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

object ImageProcessingService {

    fun applyGrain(image: BufferedImage, params: GrainParams): BufferedImage {
        val width = image.width
        val height = image.height

        val inputBytes = imageToRgbBytes(image) // Changed to RGB
        val inputBuffer = ByteBuffer.allocateDirect(inputBytes.size)
        inputBuffer.put(inputBytes)
        inputBuffer.flip()

        val outputBuffer = ByteBuffer.allocateDirect(width * height * 3)

        GrainProcessor.applyGrain(
            inputBuf = inputBuffer,
            outputBuf = outputBuffer,
            width = width,
            height = height,
            size = params.size,
            intensity = params.intensity,
            crystalSharpness = params.crystalSharpness,
            saturation = params.saturation,
            exposure = params.exposure,
            shadowGrain = params.shadowGrain,
            midtoneGrain = params.midtoneGrain,
            highlightGrain = params.highlightGrain,
            tonalSmoothness = params.tonalSmoothness,
            depth = params.depth,
            chromatic = params.chromatic,
            relief = params.relief,
            layers = params.layers
        )

        return rgbBytesToImage(outputBuffer, width, height)
    }

    private fun imageToRgbBytes(image: BufferedImage): ByteArray {
        val width = image.width
        val height = image.height
        val bytes = ByteArray(width * height * 3)

        val rgbImage = if (image.type != BufferedImage.TYPE_INT_RGB) {
            val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g2d = converted.createGraphics()
            g2d.drawImage(image, 0, 0, null)
            g2d.dispose()
            converted
        } else {
            image
        }

        var idx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = rgbImage.getRGB(x, y)
                bytes[idx++] = ((argb shr 16) and 0xFF).toByte() // R
                bytes[idx++] = ((argb shr 8) and 0xFF).toByte()  // G
                bytes[idx++] = (argb and 0xFF).toByte()          // B
            }
        }
        return bytes
    }

    private fun rgbBytesToImage(buffer: ByteBuffer, width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        buffer.rewind()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF

                val rgb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                image.setRGB(x, y, rgb)
            }
        }
        return image
    }
}
