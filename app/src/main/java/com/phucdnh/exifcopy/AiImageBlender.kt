package com.phucdnh.exifcopy

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AiImageBlender — Blends a low-res AI-edited image with a high-res original.
 *
 * Pipeline:
 * 1. Downscale original to match edited size.
 * 2. Compute per-pixel difference mask (Euclidean RGB distance, threshold = 15).
 * 3. Dilate + Gaussian blur mask to feather edges.
 * 4. Lanczos-3 upscale edited bitmap to original size.
 * 5. Bilinear upscale mask to original size.
 * 6. Alpha-blend: out = orig * (1 - mask) + upscaled * mask.
 */
object AiImageBlender {

    private const val TAG = "AiImageBlender"

    // Default configurations
    // Note: DEFAULT_DIFF_THRESHOLD is compared against a perceptual weighted score,
    // NOT raw RGB Euclidean. Luma diff has 2.5x weight.
    // Good practical range: 20 (aggressive) to 80 (conservative).
    const val DEFAULT_DIFF_THRESHOLD = 35f
    const val DEFAULT_DILATE_RADIUS = 3
    const val DEFAULT_FEATHER_SIGMA = 4f

    data class BlendProgress(
        val step: Int,               // 1..5
        val totalSteps: Int = 5,
        val messageVi: String,
        val messageEn: String,
        val percent: Float,          // 0f..1f
        val maskPreviewBitmap: Bitmap? = null, // Red mask overlay preview bitmap
        val diffPixelCount: Int = 0,
        val totalPixelCount: Int = 0
    ) {
        fun getMessage(isVi: Boolean): String = if (isVi) messageVi else messageEn
    }

    data class MaskPreviewData(
        val previewBitmap: Bitmap,
        val diffPixelCount: Int,
        val totalPixelCount: Int
    )

    data class BlendResult(
        val bitmap: Bitmap,
        val maskPreviewBitmap: Bitmap?,
        val diffPixelCount: Int,
        val totalPixelCount: Int
    )

    /**
     * Fast realtime mask preview for slider interaction.
     * Takes pre-scaled downsampled thumbnail bitmaps and returns the red mask overlay instantly.
     */
    fun generateQuickMaskPreview(
        origLowBitmap: Bitmap,
        editedLowBitmap: Bitmap,
        diffThreshold: Float = DEFAULT_DIFF_THRESHOLD
    ): MaskPreviewData {
        val w = editedLowBitmap.width
        val h = editedLowBitmap.height

        val scaledOrig = if (origLowBitmap.width != w || origLowBitmap.height != h) {
            Bitmap.createScaledBitmap(origLowBitmap, w, h, true)
        } else origLowBitmap

        val origPixels = IntArray(w * h)
        scaledOrig.getPixels(origPixels, 0, w, 0, 0, w, h)
        if (scaledOrig !== origLowBitmap) scaledOrig.recycle()

        val editPixels = IntArray(w * h)
        editedLowBitmap.getPixels(editPixels, 0, w, 0, 0, w, h)

        val (rawMask, diffCount) = computeDiffMask(origPixels, editPixels, w, h, diffThreshold)
        val overlayBitmap = createRedMaskOverlay(editPixels, rawMask, w, h)
        return MaskPreviewData(overlayBitmap, diffCount, w * h)
    }


    /**
     * Main entry point with progress feedback and configurable thresholds.
     */
    fun blendImages(
        originalBitmap: Bitmap,
        editedBitmap: Bitmap,
        diffThreshold: Float = DEFAULT_DIFF_THRESHOLD,
        featherSigma: Float = DEFAULT_FEATHER_SIGMA,
        dilateRadius: Int = DEFAULT_DILATE_RADIUS,
        onProgress: ((BlendProgress) -> Unit)? = null
    ): BlendResult {
        val origW = originalBitmap.width
        val origH = originalBitmap.height
        val editW = editedBitmap.width
        val editH = editedBitmap.height

        Log.d(TAG, "Blending: original=${origW}x${origH}, edited=${editW}x${editH}, threshold=$diffThreshold")

        // Step 1: Downscale original
        onProgress?.invoke(
            BlendProgress(
                step = 1,
                percent = 0.15f,
                messageVi = "Đang đọc & căn chỉnh kích thước ảnh...",
                messageEn = "Reading & aligning image dimensions..."
            )
        )
        val origLow = Bitmap.createScaledBitmap(originalBitmap, editW, editH, true)
        val origLowPixels = IntArray(editW * editH)
        origLow.getPixels(origLowPixels, 0, editW, 0, 0, editW, editH)

        val editedPixels = IntArray(editW * editH)
        editedBitmap.getPixels(editedPixels, 0, editW, 0, 0, editW, editH)

        // Step 2: Compute diff mask & generate Red Mask overlay preview
        onProgress?.invoke(
            BlendProgress(
                step = 2,
                percent = 0.35f,
                messageVi = "Đang phân tích sai biệt & tạo mặt nạ đỏ...",
                messageEn = "Analyzing differences & creating red mask..."
            )
        )
        val (rawMask, diffCount) = computeDiffMask(origLowPixels, editedPixels, editW, editH, diffThreshold)

        // Generate visual red mask overlay bitmap (max 480px width for fast rendering in dialog)
        val maskPreviewBitmap = createRedMaskOverlay(editedPixels, rawMask, editW, editH)

        onProgress?.invoke(
            BlendProgress(
                step = 2,
                percent = 0.45f,
                messageVi = "Đã phát hiện $diffCount điểm ảnh bị sửa đổi!",
                messageEn = "Detected $diffCount modified pixels!",
                maskPreviewBitmap = maskPreviewBitmap,
                diffPixelCount = diffCount,
                totalPixelCount = editW * editH
            )
        )

        origLow.recycle()

        // Step 3: Dilate + Gaussian blur for feathering
        onProgress?.invoke(
            BlendProgress(
                step = 3,
                percent = 0.60f,
                messageVi = "Đang làm mềm đường biên chuyển giao...",
                messageEn = "Smoothing boundary feathering transitions...",
                maskPreviewBitmap = maskPreviewBitmap,
                diffPixelCount = diffCount,
                totalPixelCount = editW * editH
            )
        )
        val dilated = dilateMask(rawMask, editW, editH, dilateRadius)
        val feathered = gaussianBlurMask(dilated, editW, editH, featherSigma)

        // Step 4: Upscale edited image to original resolution.
        onProgress?.invoke(
            BlendProgress(
                step = 4,
                percent = 0.80f,
                messageVi = "Đang Upscale lên ${origW}x${origH}...",
                messageEn = "Upscaling to ${origW}x${origH}...",
                maskPreviewBitmap = maskPreviewBitmap,
                diffPixelCount = diffCount,
                totalPixelCount = editW * editH
            )
        )
        val upscaledBitmap = Bitmap.createScaledBitmap(editedBitmap, origW, origH, true)

        // Step 5: Blend in horizontal STRIPS to avoid allocating 4x fullres IntArrays at once.
        // For a 5472x3648 image: each 64-row strip = ~5MB vs 320MB if all held simultaneously.
        onProgress?.invoke(
            BlendProgress(
                step = 5,
                percent = 0.90f,
                messageVi = "Đang hòa trộn pixel theo từng dải...",
                messageEn = "Blending pixels in memory-efficient strips...",
                maskPreviewBitmap = maskPreviewBitmap,
                diffPixelCount = diffCount,
                totalPixelCount = editW * editH
            )
        )

        // Upscale the mask to full-res (FloatArray: ~80MB for 5K img — acceptable vs 4x IntArray)
        val upscaledMask = bilinearUpscaleMask(feathered, editW, editH, origW, origH)

        val outputBitmap = Bitmap.createBitmap(origW, origH, originalBitmap.config ?: Bitmap.Config.ARGB_8888)
        val outputCanvas = android.graphics.Canvas(outputBitmap)
        // Draw original as base (no extra allocation)
        outputCanvas.drawBitmap(originalBitmap, 0f, 0f, null)

        val STRIP_H = 64
        var y = 0
        while (y < origH) {
            val rows = minOf(STRIP_H, origH - y)
            val size = origW * rows
            val origStrip = IntArray(size)
            originalBitmap.getPixels(origStrip, 0, origW, 0, y, origW, rows)
            val upStrip = IntArray(size)
            upscaledBitmap.getPixels(upStrip, 0, origW, 0, y, origW, rows)
            val maskOff = y * origW
            for (i in 0 until size) {
                val m = upscaledMask[maskOff + i]
                if (m < 0.005f) continue
                val op = origStrip[i]; val up = upStrip[i]
                val oR = (op shr 16) and 0xFF; val oG = (op shr 8) and 0xFF; val oB = op and 0xFF
                val uR = (up shr 16) and 0xFF; val uG = (up shr 8) and 0xFF; val uB = up and 0xFF
                val outR = (oR + (uR - oR) * m + 0.5f).toInt().coerceIn(0, 255)
                val outG = (oG + (uG - oG) * m + 0.5f).toInt().coerceIn(0, 255)
                val outB = (oB + (uB - oB) * m + 0.5f).toInt().coerceIn(0, 255)
                origStrip[i] = ((op shr 24 and 0xFF) shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
            val stripBmp = Bitmap.createBitmap(origStrip, origW, rows, Bitmap.Config.ARGB_8888)
            outputCanvas.drawBitmap(stripBmp, 0f, y.toFloat(), null)
            stripBmp.recycle()
            y += rows
        }
        upscaledBitmap.recycle()

        onProgress?.invoke(
            BlendProgress(
                step = 5,
                percent = 1.0f,
                messageVi = "Hoàn tất xử lý!",
                messageEn = "Processing completed!",
                maskPreviewBitmap = maskPreviewBitmap,
                diffPixelCount = diffCount,
                totalPixelCount = editW * editH
            )
        )

        Log.d(TAG, "Blend done: diffPixels=$diffCount / ${editW * editH}")
        return BlendResult(outputBitmap, maskPreviewBitmap, diffCount, editW * editH)
    }

    /**
     * Creates a visible Red Highlight Mask Overlay on top of the edited image thumbnail.
     * Modified areas will glow in semi-transparent red (0xFFFF2222 with ~65% alpha).
     */
    private fun createRedMaskOverlay(
        basePixels: IntArray,
        mask: FloatArray,
        w: Int,
        h: Int
    ): Bitmap {
        val maxThumbDim = 720
        val scale = if (max(w, h) > maxThumbDim) maxThumbDim.toFloat() / max(w, h) else 1.0f
        val thumbW = (w * scale).roundToInt().coerceAtLeast(1)
        val thumbH = (h * scale).roundToInt().coerceAtLeast(1)

        val overlayPixels = IntArray(thumbW * thumbH)
        val stepX = w.toFloat() / thumbW
        val stepY = h.toFloat() / thumbH

        for (ty in 0 until thumbH) {
            val sy = (ty * stepY).toInt().coerceIn(0, h - 1)
            for (tx in 0 until thumbW) {
                val sx = (tx * stepX).toInt().coerceIn(0, w - 1)
                val baseP = basePixels[sy * w + sx]
                val maskVal = mask[sy * w + sx]

                val bR = (baseP shr 16) and 0xFF
                val bG = (baseP shr 8) and 0xFF
                val bB = baseP and 0xFF

                if (maskVal > 0.05f) {
                    // Blend with bright Neon Red highlight
                    val alpha = (maskVal * 0.70f).coerceIn(0.25f, 0.85f)
                    val r = (bR * (1f - alpha) + 255f * alpha).roundToInt().coerceIn(0, 255)
                    val g = (bG * (1f - alpha) + 30f * alpha).roundToInt().coerceIn(0, 255)
                    val b = (bB * (1f - alpha) + 30f * alpha).roundToInt().coerceIn(0, 255)
                    overlayPixels[ty * thumbW + tx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    // Darken or slightly desaturate non-modified areas for better contrast
                    val gray = (0.299f * bR + 0.587f * bG + 0.114f * bB).roundToInt()
                    val r = (bR * 0.85f + gray * 0.15f).roundToInt().coerceIn(0, 255)
                    val g = (bG * 0.85f + gray * 0.15f).roundToInt().coerceIn(0, 255)
                    val b = (bB * 0.85f + gray * 0.15f).roundToInt().coerceIn(0, 255)
                    overlayPixels[ty * thumbW + tx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        val thumbBitmap = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
        thumbBitmap.setPixels(overlayPixels, 0, thumbW, 0, 0, thumbW, thumbH)
        return thumbBitmap
    }


    private fun computeDiffMask(
        origPixels: IntArray,
        editedPixels: IntArray,
        w: Int,
        h: Int,
        diffThreshold: Float = DEFAULT_DIFF_THRESHOLD
    ): Pair<FloatArray, Int> {
        val n = w * h
        val mask = FloatArray(n)

        // ---------------------------------------------------------------
        // Step 1: Compute global RGB shift (median via histogram buckets)
        // to compensate for whole-image color grading / exposure changes.
        // We sample every 4th pixel for speed.
        // ---------------------------------------------------------------
        val histR = IntArray(512)   // -255..255 → offset 255
        val histG = IntArray(512)
        val histB = IntArray(512)
        val stride = 4
        val samples = (n + stride - 1) / stride
        for (idx in 0 until n step stride) {
            val op = origPixels[idx]
            val ep = editedPixels[idx]
            histR[((op shr 16 and 0xFF) - (ep shr 16 and 0xFF)) + 255]++
            histG[((op shr 8  and 0xFF) - (ep shr 8  and 0xFF)) + 255]++
            histB[((op        and 0xFF) - (ep        and 0xFF)) + 255]++
        }
        fun medianFromHist(hist: IntArray): Float {
            val half = samples / 2
            var acc = 0
            for (i in hist.indices) {
                acc += hist[i]
                if (acc >= half) return (i - 255).toFloat()
            }
            return 0f
        }
        val globalDR = medianFromHist(histR)
        val globalDG = medianFromHist(histG)
        val globalDB = medianFromHist(histB)

        // ---------------------------------------------------------------
        // Step 2: Per-pixel local difference after removing global shift.
        // Use perceptual weighting: luminance diff gets 2x weight over
        // chroma diff, so hue-only changes (color grading) produce lower
        // scores than structural changes (new objects, retouching).
        // ---------------------------------------------------------------
        var diffCount = 0
        val tSq = diffThreshold * diffThreshold  // compare against squared to avoid sqrt

        for (i in 0 until n) {
            val op = origPixels[i]
            val ep = editedPixels[i]
            val oR = (op shr 16 and 0xFF).toFloat()
            val oG = (op shr 8  and 0xFF).toFloat()
            val oB = (op        and 0xFF).toFloat()
            val eR = (ep shr 16 and 0xFF).toFloat()
            val eG = (ep shr 8  and 0xFF).toFloat()
            val eB = (ep        and 0xFF).toFloat()

            // Local diff after subtracting global shift
            val dr = (oR - eR) - globalDR
            val dg = (oG - eG) - globalDG
            val db = (oB - eB) - globalDB

            // Perceptual luminance delta (BT.709 luma weights)
            val dLum = 0.2126f * dr + 0.7152f * dg + 0.0722f * db

            // Chroma delta = what's left after removing luma component
            val dCr = dr - dLum * 0.2126f
            val dCg = dg - dLum * 0.7152f
            val dCb = db - dLum * 0.0722f
            val chromaSq = dCr * dCr + dCg * dCg + dCb * dCb

            // Weighted combined score: luminance carries more weight
            val distSq = (dLum * dLum) * 2.5f + chromaSq * 1.0f

            if (distSq > tSq) {
                // Soft confidence: how far above threshold (capped at 4x for full red)
                val confidence = (sqrt(distSq) / diffThreshold).coerceIn(1f, 4f)
                mask[i] = ((confidence - 1f) / 3f * 0.75f + 0.25f).coerceIn(0.25f, 1f)
                diffCount++
            } else {
                // Soft ghost for near-threshold pixels (helps with feathered edges)
                mask[i] = (sqrt(distSq) / diffThreshold).coerceIn(0f, 1f) * 0.12f
            }
        }
        return Pair(mask, diffCount)
    }


    private fun dilateMask(mask: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val result = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var maxVal = 0f
                val y0 = max(0, y - radius)
                val y1 = min(h - 1, y + radius)
                val x0 = max(0, x - radius)
                val x1 = min(w - 1, x + radius)
                for (ny in y0..y1) {
                    for (nx in x0..x1) {
                        val v = mask[ny * w + nx]
                        if (v > maxVal) maxVal = v
                    }
                }
                result[y * w + x] = maxVal
            }
        }
        return result
    }

    private fun gaussianBlurMask(mask: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val radius = (sigma * 3).toInt().coerceAtLeast(1)
        val kernel = FloatArray(2 * radius + 1)
        var kernelSum = 0f
        for (i in kernel.indices) {
            val x = (i - radius).toFloat()
            kernel[i] = kotlin.math.exp(-(x * x) / (2f * sigma * sigma)).toFloat()
            kernelSum += kernel[i]
        }
        for (i in kernel.indices) kernel[i] /= kernelSum

        val temp = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in kernel.indices) {
                    val nx = (x + k - radius).coerceIn(0, w - 1)
                    sum += mask[y * w + nx] * kernel[k]
                }
                temp[y * w + x] = sum
            }
        }

        val result = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in kernel.indices) {
                    val ny = (y + k - radius).coerceIn(0, h - 1)
                    sum += temp[ny * w + x] * kernel[k]
                }
                result[y * w + x] = sum.coerceIn(0f, 1f)
            }
        }
        return result
    }

    private fun lanczosKernel(x: Float): Float {
        if (x == 0f) return 1f
        val a = 3f
        if (abs(x) >= a) return 0f
        val pix = Math.PI.toFloat() * x
        return (a * sin(pix) * sin(pix / a)) / (pix * pix)
    }

    private fun lanczosUpscale(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): IntArray {
        val result = IntArray(dstW * dstH)
        val scaleX = srcW.toFloat() / dstW.toFloat()
        val scaleY = srcH.toFloat() / dstH.toFloat()
        val radius = 3

        for (dstY in 0 until dstH) {
            val srcY = (dstY + 0.5f) * scaleY - 0.5f
            val srcYi = srcY.toInt()

            for (dstX in 0 until dstW) {
                val srcX = (dstX + 0.5f) * scaleX - 0.5f
                val srcXi = srcX.toInt()

                var sumR = 0f; var sumG = 0f; var sumB = 0f; var sumW = 0f

                for (ky in -radius + 1..radius) {
                    val ny = (srcYi + ky).coerceIn(0, srcH - 1)
                    val wy = lanczosKernel(srcY - (srcYi + ky))

                    for (kx in -radius + 1..radius) {
                        val nx = (srcXi + kx).coerceIn(0, srcW - 1)
                        val wx = lanczosKernel(srcX - (srcXi + kx))
                        val w = wx * wy
                        val p = srcPixels[ny * srcW + nx]
                        sumR += ((p shr 16) and 0xFF) * w
                        sumG += ((p shr 8) and 0xFF) * w
                        sumB += (p and 0xFF) * w
                        sumW += w
                    }
                }

                val r: Int
                val g: Int
                val b: Int
                if (sumW > 1e-6f) {
                    r = (sumR / sumW).roundToInt().coerceIn(0, 255)
                    g = (sumG / sumW).roundToInt().coerceIn(0, 255)
                    b = (sumB / sumW).roundToInt().coerceIn(0, 255)
                } else {
                    val fallback = srcPixels[srcYi.coerceIn(0, srcH - 1) * srcW + srcXi.coerceIn(0, srcW - 1)]
                    r = (fallback shr 16) and 0xFF
                    g = (fallback shr 8) and 0xFF
                    b = fallback and 0xFF
                }
                result[dstY * dstW + dstX] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return result
    }

    private fun bilinearUpscaleMask(
        mask: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        val result = FloatArray(dstW * dstH)
        val scaleX = srcW.toFloat() / dstW.toFloat()
        val scaleY = srcH.toFloat() / dstH.toFloat()

        for (dstY in 0 until dstH) {
            val srcY = ((dstY + 0.5f) * scaleY - 0.5f).coerceIn(0f, (srcH - 1).toFloat())
            val y0 = srcY.toInt().coerceAtMost(srcH - 2)
            val y1 = y0 + 1
            val dy = srcY - y0

            for (dstX in 0 until dstW) {
                val srcX = ((dstX + 0.5f) * scaleX - 0.5f).coerceIn(0f, (srcW - 1).toFloat())
                val x0 = srcX.toInt().coerceAtMost(srcW - 2)
                val x1 = x0 + 1
                val dx = srcX - x0

                val v = mask[y0 * srcW + x0] * (1 - dy) * (1 - dx) +
                        mask[y0 * srcW + x1] * (1 - dy) * dx +
                        mask[y1 * srcW + x0] * dy * (1 - dx) +
                        mask[y1 * srcW + x1] * dy * dx
                result[dstY * dstW + dstX] = v.coerceIn(0f, 1f)
            }
        }
        return result
    }

    private fun alphaBlend(
        origPixels: IntArray,
        upscaledPixels: IntArray,
        mask: FloatArray,
        w: Int,
        h: Int
    ): IntArray {
        val result = IntArray(w * h)
        for (i in 0 until w * h) {
            val m = mask[i]
            val op = origPixels[i]
            val up = upscaledPixels[i]

            val oR = (op shr 16) and 0xFF
            val oG = (op shr 8) and 0xFF
            val oB = op and 0xFF
            val oA = (op shr 24) and 0xFF

            val uR = (up shr 16) and 0xFF
            val uG = (up shr 8) and 0xFF
            val uB = up and 0xFF

            val outR = (oR * (1f - m) + uR * m).roundToInt().coerceIn(0, 255)
            val outG = (oG * (1f - m) + uG * m).roundToInt().coerceIn(0, 255)
            val outB = (oB * (1f - m) + uB * m).roundToInt().coerceIn(0, 255)

            result[i] = (oA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
        return result
    }
}
