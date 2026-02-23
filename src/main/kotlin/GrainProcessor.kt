package io.github.eilifhl.larm

import java.nio.ByteBuffer

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
