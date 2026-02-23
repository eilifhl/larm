package io.github.eilifhl

import kotlinx.serialization.Serializable

@Serializable
data class GrainParams(
    val size: Double = 2.5,
    val intensity: Double = 0.5,
    val crystalSharpness: Double = 0.5,
    val saturation: Double = 1.0,
    val exposure: Double = 0.0,
    val shadowGrain: Double = 1.0,
    val midtoneGrain: Double = 1.0,
    val highlightGrain: Double = 1.0,
    val tonalSmoothness: Double = 0.5,
    val depth: Double = 0.5,
    val chromatic: Double = 0.0,
    val relief: Double = 0.0,
    val layers: Int = 1
)

@Serializable
data class UploadResponse(
    val sessionId: String,
    val width: Int,
    val height: Int,
    val proxyWidth: Int,
    val proxyHeight: Int
)

