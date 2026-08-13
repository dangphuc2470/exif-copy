package com.phucdnh.exifcopy

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatermarkBatchTest {

    private val TAG = "WatermarkBatchTest"

    @Test
    fun testProcessAllAssetImagesOnDevice() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val contentResolver = appContext.contentResolver

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        val imageUris = mutableListOf<Pair<Uri, String>>()
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                imageUris.add(Pair(uri, name))
            }
        }

        Log.d(TAG, "Found ${imageUris.size} images in MediaStore")
        var processedCount = 0

        for ((uri, name) in imageUris) {
            Log.d(TAG, "--------------------------------------------------")
            Log.d(TAG, "Processing MediaStore image: $name ($uri)")

            val originalBitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }

            if (originalBitmap == null) {
                Log.e(TAG, "Could not decode $name")
                continue
            }

            val result = GeminiWatermarkRemover.processImage(originalBitmap, GeminiWatermarkRemover.WatermarkMode.AI_MODEL)
            Log.d(TAG, "Result for $name: detected=${result.detected}, match=${result.match}")

            // Save cleaned image to MediaStore
            val outValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "cleaned_test_$name")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ExifCopyBatchCleaned")
                }
            }

            val outUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, outValues)
            if (outUri != null) {
                contentResolver.openOutputStream(outUri)?.use { os ->
                    result.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                }
            }

            processedCount++
            if (result.bitmap !== originalBitmap) {
                originalBitmap.recycle()
            }
        }

        Log.d(TAG, "Batch processing complete! Total processed: $processedCount")
        assertTrue(processedCount > 0)
    }
}
