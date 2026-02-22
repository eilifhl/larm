package io.github.eilifhl.larm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import javax.imageio.ImageIO
import java.nio.ByteBuffer
import kotlin.math.roundToInt

data class GrainParams(
    val size: Float = 2.5f,
    val intensity: Float = 0.8f,
    val sharpness: Float = 8.0f,
    val saturation: Float = 1.0f,
    val exposure: Float = 0.0f,

    val shadowGrain: Float = 1.2f,
    val midtoneGrain: Float = 1.0f,
    val highlightGrain: Float = 0.6f,
    val tonalSmoothness: Float = 0.15f,

    val depth: Float = 0.4f,
    val chromatic: Float = 2.0f,
    val relief: Float = 0.3f,
    val layers: Float = 3.0f
)

@Composable
fun App() {
    MaterialTheme {
        var params by remember { mutableStateOf(GrainParams()) }
        var status by remember { mutableStateOf("Ready") }
        var isProcessing by remember { mutableStateOf(false) }

        Row(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Larm Controls", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

                SectionHeader("General")
                LabeledSlider("Size", params.size, 0.1f..200.0f) { params = params.copy(size = it) }
                LabeledSlider("Intensity", params.intensity, 0.0f..200.0f) { params = params.copy(intensity = it) }
                LabeledSlider("Sharpness", params.sharpness, 0.0f..20.0f) { params = params.copy(sharpness = it) }
                LabeledSlider("Saturation", params.saturation, 0.0f..2.0f) { params = params.copy(saturation = it) }
                LabeledSlider("Exposure", params.exposure, -2.0f..2.0f) { params = params.copy(exposure = it) }

                SectionHeader("Tonal Distribution")
                LabeledSlider("Shadow Grain", params.shadowGrain, 0.0f..2.0f) { params = params.copy(shadowGrain = it) }
                LabeledSlider("Midtone Grain", params.midtoneGrain, 0.0f..2.0f) { params = params.copy(midtoneGrain = it) }
                LabeledSlider("Highlight Grain", params.highlightGrain, 0.0f..2.0f) { params = params.copy(highlightGrain = it) }
                LabeledSlider("Smoothness", params.tonalSmoothness, 0.01f..1.0f) { params = params.copy(tonalSmoothness = it) }

                SectionHeader("3D & Advanced")
                LabeledSlider("3D Depth", params.depth, 0.0f..1.0f) { params = params.copy(depth = it) }
                LabeledSlider("Chromatic", params.chromatic, 0.0f..500.0f) { params = params.copy(chromatic = it) }
                LabeledSlider("Relief", params.relief, 0.0f..100.0f) { params = params.copy(relief = it) }
                LabeledSlider("Layers", params.layers, 1.0f..5.0f, steps = 3) { params = params.copy(layers = it) }

                Spacer(modifier = Modifier.height(20.dp))
            }

            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(status, modifier = Modifier.padding(bottom = 20.dp))

                Button(
                    enabled = !isProcessing,
                    onClick = {
                        isProcessing = true
                        status = "Processing..."
                        Thread {
                            try {
                                processImage(params)
                                status = "Done! Saved to output_compose.png"
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                                e.printStackTrace()
                            } finally {
                                isProcessing = false
                            }
                        }.start()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Apply Grain")
                }
            }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.body2)
            Text(String.format("%.2f", value), style = MaterialTheme.typography.caption)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colors.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
    Divider()
}

fun processImage(params: GrainParams) {
    val inputImage = ImageIO.read(File("input.jpg")) ?: throw Exception("input.jpg not found")
    val width = inputImage.width
    val height = inputImage.height
    val byteSize = width * height * 3

    val inputBuf = ByteBuffer.allocateDirect(byteSize)
    val outputBuf = ByteBuffer.allocateDirect(byteSize)

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
        size = params.size.toDouble(),
        intensity = params.intensity.toDouble(),
        crystalSharpness = params.sharpness.toDouble(),
        saturation = params.saturation.toDouble(),
        exposure = params.exposure.toDouble(),
        shadowGrain = params.shadowGrain.toDouble(),
        midtoneGrain = params.midtoneGrain.toDouble(),
        highlightGrain = params.highlightGrain.toDouble(),
        tonalSmoothness = params.tonalSmoothness.toDouble(),
        depth = params.depth.toDouble(),
        chromatic = params.chromatic.toDouble(),
        relief = params.relief.toDouble(),
        layers = params.layers.roundToInt()
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
