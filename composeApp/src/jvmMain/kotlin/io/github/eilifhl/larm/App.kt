package io.github.eilifhl.larm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import java.nio.ByteBuffer

@Composable
fun App() {
    MaterialTheme {
        var status by remember { mutableStateOf("Ready to Larm") }

        Column(Modifier.padding(20.dp)) {
            Text(status)

            Button(onClick = {
                status = "Processing..."
                Thread {
                    processImage()
                    status = "Done! Saved to output_compose.png"
                }.start()
            }) {
                Text("Apply Grain")
            }
        }
    }
}

fun processImage() {
    val inputImage = ImageIO.read(File("input.jpg")) 
    val width = inputImage.width
    val height = inputImage.height
    val byteSize = width * height * 3

    val inputBuf = ByteBuffer.allocateDirect(byteSize)
    val outputBuf = ByteBuffer.allocateDirect(byteSize)

    // Fill Buffer
    for (y in 0 until height) {
        for (x in 0 until width) {
            val rgb = inputImage.getRGB(x, y)
            inputBuf.put(((rgb shr 16) and 0xFF).toByte())
            inputBuf.put(((rgb shr 8) and 0xFF).toByte())
            inputBuf.put((rgb and 0xFF).toByte())
        }
    }
    inputBuf.rewind()

    GrainProcessor.applyGrain(
        inputBuf, outputBuf, width, height,
        2.5, 0.8, 8.0, 1.0, 0.0, 
        1.2, 1.0, 0.6, 0.15, 0.4, 2.0, 0.3, 3
    )

    outputBuf.rewind()
    val outputImage = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val r = outputBuf.get().toInt() and 0xFF
            val g = outputBuf.get().toInt() and 0xFF
            val b = outputBuf.get().toInt() and 0xFF
            val rgb = (r shl 16) or (g shl 8) or b
            outputImage.setRGB(x, y, rgb)
        }
    }
    ImageIO.write(outputImage, "png", File("output_compose.png"))
}
