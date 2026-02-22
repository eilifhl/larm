package com.example.app

import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO

object GrainProcessor {
    init {
        System.loadLibrary("larm") 
    }

    external fun applyGrain(
        inputBuf: ByteBuffer,
        outputBuf: ByteBuffer,
        width: Int,
        height: Int,
        size: Double,
        intensity: Double,
        crystalSharpness: Double,
        saturation: Double,
        exposure: Double,
        shadowGrain: Double,
        midtoneGrain: Double,
        highlightGrain: Double,
        tonalSmoothness: Double,
        depth: Double,
        chromatic: Double,
        relief: Double,
        layers: Int
    )
}

fun main() {
    val inputPath = "/home/eilif/Pictures/pfp-konf.jpg" 
    val outputPath = "output_kotlin.png"

    println("1. Loading Image...")
    val inputImage = ImageIO.read(File(inputPath))
    val width = inputImage.width
    val height = inputImage.height
    val byteSize = width * height * 3

    println("2. Allocating Direct Memory ($byteSize bytes)...")
    val inputBuf = ByteBuffer.allocateDirect(byteSize)
    val outputBuf = ByteBuffer.allocateDirect(byteSize)

    println("3. Converting Image to Bytes...")
    for (y in 0 until height) {
        for (x in 0 until width) {
            val rgb = inputImage.getRGB(x, y)
            // Java bytes are signed (-128 to 127), so we cast. Rust treats them as u8 (0-255).
            inputBuf.put(((rgb shr 16) and 0xFF).toByte()) // R
            inputBuf.put(((rgb shr 8) and 0xFF).toByte())  // G
            inputBuf.put((rgb and 0xFF).toByte())          // B
        }
    }
    inputBuf.rewind() 

    println("4. Calling Rust Native Library...")
    val startTime = System.currentTimeMillis()
    
    GrainProcessor.applyGrain(
        inputBuf, outputBuf, width, height,
        size = 200.0,
        intensity = 0.8,
        crystalSharpness = 8.0,
        saturation = 1.0,
        exposure = 0.0,
        shadowGrain = 1.2,
        midtoneGrain = 1.0,
        highlightGrain = 0.6,
        tonalSmoothness = 0.15,
        depth = 0.4,
        chromatic = 2.0,
        relief = 0.3,
        layers = 3
    )
    
    val duration = System.currentTimeMillis() - startTime
    println("   Rust finished in ${duration}ms")

    println("5. reconstructing Image...")
    outputBuf.rewind()
    val outputImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    for (y in 0 until height) {
        for (x in 0 until width) {
            // We must mask with 0xFF to fix the signed byte issue
            val r = outputBuf.get().toInt() and 0xFF
            val g = outputBuf.get().toInt() and 0xFF
            val b = outputBuf.get().toInt() and 0xFF
            
            val rgb = (r shl 16) or (g shl 8) or b
            outputImage.setRGB(x, y, rgb)
        }
    }

    println("6. Saving to $outputPath")
    ImageIO.write(outputImage, "png", File(outputPath))
    println("Done!")
}
