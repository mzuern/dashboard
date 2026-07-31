package com.productionboard.scanner.photo

import java.io.File

/**
 * One photo/scan frame in the current session. Guided scan frames also
 * carry their accumulated live-tracking position in the 320px analysis
 * coordinate system so the mosaic builder has a strong initial placement.
 */
data class SelectedPhoto(
    val id: String,
    val file: File,
    val rotationDegrees: Int = 0,
    val scanX: Double? = null,
    val scanY: Double? = null,
)
