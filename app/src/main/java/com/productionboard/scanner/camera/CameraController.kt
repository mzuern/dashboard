package com.productionboard.scanner.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX preview + throttled analysis frames for the guided board scan. */
class CameraController(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var onFrame: ((Bitmap) -> Unit)? = null
    private var lastFrameAt = 0L

    suspend fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        minFrameIntervalMs: Long = 180,
        onFrame: (Bitmap) -> Unit,
    ) {
        this.onFrame = onFrame
        val cameraProvider = getProvider()
        provider = cameraProvider

        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val selector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1920, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { image ->
            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameAt >= minFrameIntervalMs) {
                    lastFrameAt = now
                    this.onFrame?.invoke(YuvToBitmap.convert(image))
                }
            } catch (_: Exception) {
                // Skip a bad frame; the next preview frame will arrive immediately.
            } finally {
                image.close()
            }
        }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
    }

    fun stop() {
        provider?.unbindAll()
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
