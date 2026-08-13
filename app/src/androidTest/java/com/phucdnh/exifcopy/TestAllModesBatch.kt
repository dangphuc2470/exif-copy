package com.phucdnh.exifcopy

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestAllModesBatch {

    private val TAG = "TestAllModesBatch"

    @Test
    fun testAllModesOnKeyImages() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val contentResolver = appContext.contentResolver

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
            null, null, null
        )

        val targetNames = listOf("1785528874538", "1786294359628", "1785216492252", "1785529212364", "1783673019365")

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (it.moveToNext()) {
                val name = it.getString(nameCol)
                Log.d(TAG, "FOUND MEDIASTORE IMAGE: $name")
                if (targetNames.any { t -> name.contains(t) && !name.contains("clean_") && !name.contains("mode_") }) {
                    val id = it.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    Log.d(TAG, "RUNNING cleanGoogleAiMetadata WITH ALL_THREE FOR: $name ($uri)")

                    val resultUri = ExifMetadataHelper.cleanGoogleAiMetadata(
                        context = appContext,
                        targetUri = uri,
                        replaceOriginal = false,
                        removeWatermark = true,
                        watermarkMode = GeminiWatermarkRemover.WatermarkMode.ALL_THREE
                    )

                    Log.d(TAG, "FINISHED ALL_THREE PROCESSING FOR $name: resultUri = $resultUri")
                }
            }
        }
    }

    @Test
    fun testAppPrivateStorageProcessing() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val destFile = java.io.File("/sdcard/1783673019365.png")

        if (destFile.exists()) {
            val uri = Uri.fromFile(destFile)
            val bitmap = android.graphics.BitmapFactory.decodeFile(destFile.absolutePath)
            val match = GeminiWatermarkRemover.findWatermarkMatch(bitmap)
            Log.d(TAG, "MATCH FOR 1783673019365: $match")

            val resultUri = ExifMetadataHelper.cleanGoogleAiMetadata(
                context = appContext,
                targetUri = uri,
                replaceOriginal = false,
                removeWatermark = true,
                watermarkMode = GeminiWatermarkRemover.WatermarkMode.ALL_THREE
            )

            Log.d(TAG, "TEST FINISHED FOR 1783673019365.png: resultUri = $resultUri")
        }
    }
}
