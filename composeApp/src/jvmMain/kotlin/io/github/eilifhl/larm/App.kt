package io.github.eilifhl.larm

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import java.awt.FileDialog
import java.awt.Frame
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

/** Signal sent to the render coroutine whenever params or view mode change. */
private data class RenderRequest(
    val params: GrainParams,
    val isLoupeMode: Boolean
)

@OptIn(FlowPreview::class)
@Composable
fun App() {
    MaterialTheme {
        var params by remember { mutableStateOf(GrainParams()) }
        var status by remember { mutableStateOf("Select an image to begin") }
        var isLoading by remember { mutableStateOf(false) }
        var isProcessing by remember { mutableStateOf(false) }
        var workspace by remember { mutableStateOf<ImageWorkspace?>(null) }
        var isLoupeMode by remember { mutableStateOf(false) }
        var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

        val renderFlow = remember { MutableStateFlow(RenderRequest(GrainParams(), false)) }
        val scope = rememberCoroutineScope()

        // Push params 
        LaunchedEffect(params, isLoupeMode) {
            renderFlow.value = RenderRequest(params, isLoupeMode)
        }

        LaunchedEffect(workspace) {
            val ws = workspace ?: return@LaunchedEffect
            renderFlow.debounce(50).collectLatest { request ->
                isProcessing = true
                status = "Rendering preview…"
                val bitmap = withContext(Dispatchers.Default) {
                    renderPreview(ws, request.params, request.isLoupeMode)
                }
                previewBitmap = bitmap
                isProcessing = false
                status = if (request.isLoupeMode) "100% Loupe" else "Fit View"
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {

            // Left panel
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

            // Right panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = !isLoading,
                        onClick = {
                            val file = openFileDialog()
                            if (file != null) {
                                isLoading = true
                                status = "Loading ${file.name}…"
                                previewBitmap = null
                                scope.launch(Dispatchers.Default) {
                                    try {
                                        val ws = ImageWorkspace.load(file)
                                        workspace = ws
                                        status = "Loaded – ${ws.originalWidth}×${ws.originalHeight}"
                                    } catch (e: Exception) {
                                        status = "Error: ${e.message}"
                                        e.printStackTrace()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (workspace == null) "Select Image" else "Change Image")
                    }

                    OutlinedButton(
                        enabled = workspace != null,
                        onClick = { isLoupeMode = !isLoupeMode },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isLoupeMode) "View: 100%" else "View: Fit")
                    }
                }

                Text(
                    text = status,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isProcessing || isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }

                val bmp = previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Preview",
                        contentScale = if (isLoupeMode) ContentScale.None else ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No preview", style = MaterialTheme.typography.h6)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    enabled = workspace != null && !isProcessing,
                    onClick = {
                        val ws = workspace ?: return@Button
                        isProcessing = true
                        status = "Exporting full resolution…"
                        scope.launch(Dispatchers.Default) {
                            try {
                                exportFullResolution(ws, params)
                                status = "Saved to output_compose.png"
                            } catch (e: Exception) {
                                status = "Export error: ${e.message}"
                                e.printStackTrace()
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Export Full Resolution")
                }
            }
        }
    }
}

// ── Rendering helpers ──

/**
 * Render the proxy or loupe through Rust and return an ImageBitmap for Compose.
 */
fun renderPreview(workspace: ImageWorkspace, params: GrainParams, isLoupeMode: Boolean): ImageBitmap {
    val (inputBuf, outputBuf, w, h) = if (isLoupeMode) {
        PreviewBuffers(
            workspace.loupeInputBuffer,
            workspace.loupeOutputBuffer,
            workspace.loupeWidth,
            workspace.loupeHeight
        )
    } else {
        PreviewBuffers(
            workspace.proxyInputBuffer,
            workspace.proxyOutputBuffer,
            workspace.proxyWidth,
            workspace.proxyHeight
        )
    }

    // In proxy mode scale grain size so it looks proportional to the full image
    val effectiveSize = if (isLoupeMode) {
        params.size.toDouble()
    } else {
        params.size.toDouble() * workspace.proxyScaleFactor
    }

    inputBuf.rewind()

    GrainProcessor.applyGrain(
        inputBuf, outputBuf, w, h,
        size = effectiveSize,
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

    return byteBufferToImageBitmap(outputBuf, w, h)
}

private data class PreviewBuffers(
    val input: ByteBuffer,
    val output: ByteBuffer,
    val width: Int,
    val height: Int,
)

/**
 * Convert an RGB byte buffer into a Compose ImageBitmap 
 */
fun byteBufferToImageBitmap(buffer: ByteBuffer, width: Int, height: Int): ImageBitmap {
    buffer.rewind()
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val r = buffer.get().toInt() and 0xFF
            val g = buffer.get().toInt() and 0xFF
            val b = buffer.get().toInt() and 0xFF
            img.setRGB(x, y, (r shl 16) or (g shl 8) or b)
        }
    }
    return img.toComposeImageBitmap()
}

/**
 * Full-resolution export 
 */
fun exportFullResolution(workspace: ImageWorkspace, params: GrainParams) {
    val width = workspace.originalWidth
    val height = workspace.originalHeight
    val byteSize = width * height * 3

    val inputBuf = ByteBuffer.allocateDirect(byteSize)
    val outputBuf = ByteBuffer.allocateDirect(byteSize)

    val img = workspace.originalImage
    for (y in 0 until height) {
        for (x in 0 until width) {
            val rgb = img.getRGB(x, y)
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
    val outputImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
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

// ── UI components ──

fun openFileDialog(): File? {
    val dialog = FileDialog(null as Frame?, "Select Image", FileDialog.LOAD)
    dialog.file = "*.jpg;*.png;*.jpeg"
    dialog.isVisible = true

    return if (dialog.file != null) {
        File(dialog.directory, dialog.file)
    } else {
        null
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
