package com.productionboard.scanner.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Size as AndroidSize
import com.productionboard.scanner.domain.CameraResolutionPreset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper over CameraX: binds Preview + ImageAnalysis to the given
 * lifecycle, and hands each analyzed frame to [onFrame] as a Bitmap. The
 * camera is always the rear ("environment") lens - this app only ever
 * scans a physical board, never takes selfies. Frames arrive faster than
 * the vision pipeline needs to process them, so [minFrameIntervalMs]
 * throttles at the cheapest possible point (before the YUV-to-Bitmap
 * conversion) - throttled-out frames are closed immediately without
 * decoding.
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var onFrame: ((Bitmap, rotationDegrees: Int) -> Unit)? = null
    private var minFrameIntervalMs: Long = 0
    private var lastProcessedAtMs: Long = 0

    suspend fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        resolution: CameraResolutionPreset,
        minFrameIntervalMs: Long = 160,
        onFrame: (Bitmap, rotationDegrees: Int) -> Unit,
    ) {
        this.onFrame = onFrame
        this.minFrameIntervalMs = minFrameIntervalMs
        this.lastProcessedAtMs = 0
        val provider = getProvider()
        cameraProvider = provider

        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(AndroidSize(resolution.width, resolution.height), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor, ::handleFrame)

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
    }

    private fun handleFrame(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastProcessedAtMs < minFrameIntervalMs) return
            lastProcessedAtMs = now
            val bitmap = YuvToBitmap.convert(image)
            onFrame?.invoke(bitmap, image.imageInfo.rotationDegrees)
        } catch (_: Exception) {
            // A single malformed frame shouldn't take down the analyzer loop -
            // it's simply skipped and the next frame is processed normally.
        } finally {
            image.close()
        }
    }

    fun stop() {
        cameraProvider?.unbindAll()
        onFrame = null
    }

    fun shutdown() {
        stop()
        analysisExecutor.shutdown()
    }

    private suspend fun getProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
}
