package com.productionboard.scanner.domain

import kotlinx.serialization.Serializable

@Serializable
data class CameraResolutionPreset(val label: String, val width: Int, val height: Int)

val CAMERA_RESOLUTION_PRESETS = listOf(
    CameraResolutionPreset("Standard (1280x720)", 1280, 720),
    CameraResolutionPreset("High (1920x1080)", 1920, 1080),
    CameraResolutionPreset("Maximum (3840x2160)", 3840, 2160),
)

@Serializable
data class AppSettings(
    val boardTemplate: BoardTemplate = BoardTemplate.DEFAULT,
    val ocrConfidenceThreshold: Float = 65f,
    val coverageThreshold: Float = 0.9f,
    val cameraResolution: CameraResolutionPreset = CAMERA_RESOLUTION_PRESETS[1],
    val debugMode: Boolean = false,
    /** Storage.deleteImagesAfterReport: drop the stitched board image once the report is generated. */
    val deleteImagesAfterReport: Boolean = true,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}
