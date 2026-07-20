package com.productionboard.scanner.photo

import java.io.File

/**
 * One photo the user has taken or picked for this session, held only in
 * memory (and app-private cache on disk) - not persisted across process
 * death. [rotationDegrees] is a user-applied override on top of whatever
 * EXIF orientation correction happens automatically during processing.
 */
data class SelectedPhoto(
    val id: String,
    val file: File,
    val rotationDegrees: Int = 0,
)
