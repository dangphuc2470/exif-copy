package com.phucdnh.exifcopy

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TestUser3Images {

    private val TAG = "TestUser3Images"

    @Test
    fun testProcessUser3Images() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val filenames = listOf("1786530580196.png", "1786517832769.png", "1786530350107.png", "1785684925968.png")

        for (filename in filenames) {
            val srcFile = File("/data/local/tmp/$filename")
            if (!srcFile.exists()) {
                Log.e(TAG, "INPUT FILE NOT FOUND: ${srcFile.absolutePath}")
                continue
            }
            val cacheFile = File(appContext.cacheDir, filename)
            srcFile.copyTo(cacheFile, overwrite = true)

            val originalBitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
            if (originalBitmap == null) {
                Log.e(TAG, "FAILED TO DECODE BITMAP: ${cacheFile.absolutePath}")
                continue
            }
            Log.e(TAG, "=== PROCESSING $filename (${originalBitmap.width}x${originalBitmap.height}) ===")

            val match = GeminiWatermarkRemover.findWatermarkMatch(originalBitmap)
            Log.e(TAG, "DETECTION MATCH FOR $filename: $match")

            val result = GeminiWatermarkRemover.processImage(
                bitmap = originalBitmap,
                mode = GeminiWatermarkRemover.WatermarkMode.AI_MODEL
            )
            Log.e(TAG, "RESULT MATCH FOR $filename: ${result.match}, DETECTED=${result.detected}")

            val outName = "cleaned_kt_$filename"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, outName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Log.e(TAG, "SAVED TO MEDIASTORE: $uri ($outName)")
            }
        }
    }
}
