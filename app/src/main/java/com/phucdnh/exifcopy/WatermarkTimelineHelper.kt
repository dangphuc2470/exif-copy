package com.phucdnh.exifcopy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * Model representing a timeline version of Reverse Alpha / Watermark profile.
 */
data class ReverseAlphaTimelineItem(
    val id: String,
    val dateRangeVi: String,
    val dateRangeEn: String,
    val timestampMs: Long,
    val blackAssetPath: String,
    val colorAssetPath: String? = null,
    val descriptionVi: String,
    val descriptionEn: String,
    val watermarkMode: GeminiWatermarkRemover.WatermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG19
) {
    fun getDateRange(isVi: Boolean): String = if (isVi) dateRangeVi else dateRangeEn
    fun getDescription(isVi: Boolean): String = if (isVi) descriptionVi else descriptionEn
}

object WatermarkTimelineHelper {
    val TIMELINE_ITEMS = listOf(
        ReverseAlphaTimelineItem(
            id = "ver_1787285344763",
            dateRangeVi = "Từ 19/08/2026 - Hiện tại",
            dateRangeEn = "From Aug 19, 2026 - Present",
            timestampMs = 1787285344763L,
            blackAssetPath = "watermarks/1787285344763.png",
            colorAssetPath = "watermarks/1787283625606.png",
            descriptionVi = "Watermark Gemini bản cập nhật mới (kích thước ~48px, mốc 19/08/2026)",
            descriptionEn = "Updated Google Gemini watermark (~48px, Aug 19, 2026 milestone)",
            watermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG19
        ),
        ReverseAlphaTimelineItem(
            id = "ver_1786604160723",
            dateRangeVi = "13/08/2026 - 18/08/2026",
            dateRangeEn = "Aug 13, 2026 - Aug 18, 2026",
            timestampMs = 1786604160723L,
            blackAssetPath = "watermarks/1786604160723.png",
            colorAssetPath = "watermarks/1786604243209.png",
            descriptionVi = "Watermark Gemini ngôi sao 4 cánh nhỏ (kích thước ~24px, mốc 13/08/2026)",
            descriptionEn = "Google Gemini 4-point star watermark (~24px, Aug 13, 2026 milestone)",
            watermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG13
        ),
        ReverseAlphaTimelineItem(
            id = "ver_20260607",
            dateRangeVi = "07/06/2026 - 29/07/2026",
            dateRangeEn = "Jun 07, 2026 - Jul 29, 2026",
            timestampMs = 1780790400000L,
            blackAssetPath = "watermarks/gemini_v2_36.png",
            colorAssetPath = "watermarks/sample_20260607.png",
            descriptionVi = "Watermark Gemini V2 Small (~36px, lề rộng 96px)",
            descriptionEn = "Google Gemini V2 small watermark (~36px, large 96px margin)",
            watermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_V2_36
        ),
        ReverseAlphaTimelineItem(
            id = "ver_20260520",
            dateRangeVi = "20/05/2026 - 06/06/2026",
            dateRangeEn = "May 20, 2026 - Jun 06, 2026",
            timestampMs = 1779235200000L,
            blackAssetPath = "watermarks/bg_96_20260520.png",
            colorAssetPath = "watermarks/sample_20260520.png",
            descriptionVi = "Watermark Gemini New Margin (~96px, lề cực rộng 192px)",
            descriptionEn = "Google Gemini New Margin watermark (~96px, ultra-wide 192px margin)",
            watermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_MAY20
        ),
        ReverseAlphaTimelineItem(
            id = "ver_legacy",
            dateRangeVi = "Trước 20/05/2026 (Bản đầu)",
            dateRangeEn = "Pre May 20, 2026 (Legacy Standard)",
            timestampMs = 1770000000000L,
            blackAssetPath = "watermarks/bg_96.png",
            colorAssetPath = "watermarks/bg_48.png",
            descriptionVi = "Watermark Gemini Standard Legacy (~96px/48px, lề chuẩn 64px)",
            descriptionEn = "Google Gemini Standard Legacy (~96px/48px, standard 64px margin)",
            watermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_LEGACY
        )
    )

    private val bitmapCache = mutableMapOf<String, Bitmap>()
    private val croppedCache = mutableMapOf<String, Bitmap>()

    fun loadAssetBitmap(context: Context, assetPath: String): Bitmap? {
        bitmapCache[assetPath]?.let { return it }
        return try {
            context.assets.open(assetPath).use { inputStream ->
                val bmp = BitmapFactory.decodeStream(inputStream)
                if (bmp != null) {
                    bitmapCache[assetPath] = bmp
                }
                bmp
            }
        } catch (e: Exception) {
            Log.e("WatermarkTimeline", "Failed to load asset $assetPath: ${e.message}")
            null
        }
    }

    /**
     * Extracts a tightly zoomed crop directly centered on the watermark star from black sample image,
     * enhancing brightness for crystal-clear preview on mobile UI cards.
     */
    fun getCroppedWatermarkPreview(context: Context, assetPath: String): Bitmap? {
        croppedCache[assetPath]?.let { return it }
        val fullBitmap = loadAssetBitmap(context, assetPath) ?: return null
        return try {
            val w = fullBitmap.width
            val h = fullBitmap.height

            val rawCrop = if (w <= 150 && h <= 150) {
                fullBitmap
            } else {
                val startX = (w * 0.65f).toInt()
                val startY = (h * 0.65f).toInt()
                val subW = w - startX
                val subH = h - startY

                val pixels = IntArray(subW * subH)
                fullBitmap.getPixels(pixels, 0, subW, startX, startY, subW, subH)

                var sumX = 0L
                var sumY = 0L
                var totalWeight = 0L
                var minX = subW
                var maxX = 0
                var minY = subH
                var maxY = 0

                for (y in 0 until subH) {
                    for (x in 0 until subW) {
                        val color = pixels[y * subW + x]
                        val r = (color shr 16) and 0xFF
                        val g = (color shr 8) and 0xFF
                        val b = color and 0xFF
                        val brightness = maxOf(r, g, b)
                        if (brightness > 12) {
                            sumX += (startX + x) * brightness
                            sumY += (startY + y) * brightness
                            totalWeight += brightness
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }

                val (cropX, cropY, cropSize) = if (totalWeight > 0) {
                    val centerX = (sumX / totalWeight).toInt()
                    val centerY = (sumY / totalWeight).toInt()
                    val markW = maxX - minX + 1
                    val markH = maxY - minY + 1
                    val radius = (maxOf(markW, markH) * 0.85f).toInt().coerceIn(36, 72)
                    val cX = (centerX - radius).coerceIn(0, w - radius * 2)
                    val cY = (centerY - radius).coerceIn(0, h - radius * 2)
                    Triple(cX, cY, radius * 2)
                } else {
                    val fbSize = (w * 0.15f).toInt().coerceIn(64, 150)
                    val fbX = (w - fbSize - (w * 0.03f).toInt()).coerceIn(0, w - fbSize)
                    val fbY = (h - fbSize - (h * 0.03f).toInt()).coerceIn(0, h - fbSize)
                    Triple(fbX, fbY, fbSize)
                }

                Bitmap.createBitmap(fullBitmap, cropX, cropY, cropSize, cropSize)
            }
            
            // Enhance brightness for crisp thumbnail visibility
            val cropW = rawCrop.width
            val cropH = rawCrop.height
            val cropPixels = IntArray(cropW * cropH)
            rawCrop.getPixels(cropPixels, 0, cropW, 0, 0, cropW, cropH)
            val enhancedPixels = IntArray(cropPixels.size)
            for (i in cropPixels.indices) {
                val c = cropPixels[i]
                val a = (c shr 24) and 0xFF
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val boost = 2.4f
                val newR = (r * boost).toInt().coerceIn(0, 255)
                val newG = (g * boost).toInt().coerceIn(0, 255)
                val newB = (b * boost).toInt().coerceIn(0, 255)
                enhancedPixels[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
            }

            val enhancedBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            enhancedBitmap.setPixels(enhancedPixels, 0, cropW, 0, 0, cropW, cropH)
            croppedCache[assetPath] = enhancedBitmap
            enhancedBitmap
        } catch (e: Exception) {
            Log.e("WatermarkTimeline", "Failed to crop zoomed watermark from $assetPath: ${e.message}")
            fullBitmap
        }
    }

    /**
     * Pre-computes watermark removal for all available modes on the given image URI.
     * Returns a map of WatermarkMode to cropped result Bitmaps for instant live previews.
     */
    fun precomputeWatermarkPreviews(
        context: Context,
        imageUri: android.net.Uri
    ): Map<GeminiWatermarkRemover.WatermarkMode, Bitmap>? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val w = originalBitmap.width
            val h = originalBitmap.height
            val match = GeminiWatermarkRemover.findWatermarkMatch(originalBitmap)

            val matchTarget = if (match != null && match.score >= 0.15f) {
                match
            } else {
                val longSide = kotlin.math.max(w, h)
                val fallbackSize = if (longSide >= 1600) 96 else 48
                val fallbackMargin = if (longSide >= 1600) 64 else 32
                GeminiWatermarkRemover.DetectionMatch(
                    (w - fallbackMargin - fallbackSize).coerceIn(0, w - fallbackSize),
                    (h - fallbackMargin - fallbackSize).coerceIn(0, h - fallbackSize),
                    fallbackSize,
                    fallbackSize,
                    match?.score ?: 0f,
                    "Fallback"
                )
            }

            val padding = (matchTarget.width * 0.4f).toInt().coerceIn(16, 48)
            val cropX = (matchTarget.x - padding).coerceIn(0, w - 1)
            val cropY = (matchTarget.y - padding).coerceIn(0, h - 1)
            val cropW = (matchTarget.width + padding * 2).coerceAtMost(w - cropX)
            val cropH = (matchTarget.height + padding * 2).coerceAtMost(h - cropY)

            val resultMap = mutableMapOf<GeminiWatermarkRemover.WatermarkMode, Bitmap>()
            val modesToCompute = listOf(
                GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG19,
                GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG13,
                GeminiWatermarkRemover.WatermarkMode.IDW_INPAINT,
                GeminiWatermarkRemover.WatermarkMode.OPENCV_INPAINT,
                GeminiWatermarkRemover.WatermarkMode.AI_MODEL
            )

            for (mode in modesToCompute) {
                try {
                    val result = GeminiWatermarkRemover.processImage(originalBitmap, mode)
                    val cropped = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
                    val cropPixels = IntArray(cropW * cropH)
                    result.bitmap.getPixels(cropPixels, 0, cropW, cropX, cropY, cropW, cropH)
                    cropped.setPixels(cropPixels, 0, cropW, 0, 0, cropW, cropH)
                    resultMap[mode] = cropped
                } catch (e: Exception) {
                    Log.e("WatermarkTimeline", "Pre-computation error for mode $mode: ${e.message}")
                }
            }

            // Assign AI_MODEL preview or REVERSE_ALPHA_AUG19 for ALL_MODES
            resultMap[GeminiWatermarkRemover.WatermarkMode.AI_MODEL]?.let {
                resultMap[GeminiWatermarkRemover.WatermarkMode.ALL_MODES] = it
            } ?: resultMap[GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA_AUG19]?.let {
                resultMap[GeminiWatermarkRemover.WatermarkMode.ALL_MODES] = it
            }

            resultMap
        } catch (e: Exception) {
            Log.e("WatermarkTimeline", "Failed to precompute watermark previews: ${e.message}")
            null
        }
    }
}
