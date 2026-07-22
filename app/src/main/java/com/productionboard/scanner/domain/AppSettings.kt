package com.productionboard.scanner.domain

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val boardTemplate: BoardTemplate = BoardTemplate.DEFAULT,
    val ocrConfidenceThreshold: Float = 65f,
    /** Delete cached photos from app-private storage once an email has been generated from them. */
    val clearPhotosAfterEmail: Boolean = true,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}
