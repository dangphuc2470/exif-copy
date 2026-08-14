 package com.phucdnh.exifcopy

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class ExifSettings(
    val randomizeShutter: Boolean = false,
    val shutterMinOffsetPct: Double = -20.0, // ± percentage (e.g., -20% to +20%)
    val shutterMaxOffsetPct: Double = 20.0,
    val shutterFixedValue: String? = null,
    
    val randomizeAperture: Boolean = false,
    val apertureMinOffset: Double = -0.4, // e.g. -0.4 to +0.4 f-stops
    val apertureMaxOffset: Double = 0.4,
    val apertureFixedValue: String? = null,
    
    val randomizeFocalLength: Boolean = false,
    val focalMinOffset: Double = -5.0, // e.g. -5mm to +5mm
    val focalMaxOffset: Double = 5.0,
    val focalFixedValue: String? = null,
    
    val randomizeTime: Boolean = false,
    val timeMinSecs: Long = 10,
    val timeMaxSecs: Long = 60,
    val baseTimeAddSecs: Long = 0,

    val copyCameraInfo: Boolean = true,
    val copyShootingInfo: Boolean = true,
    val copyGpsInfo: Boolean = true,
    val copyDateTaken: Boolean = true,
    val copyCreatedDate: Boolean = true,
    val copyFileName: Boolean = true, // Default to true: auto copy source filename
    val copyXmpInfo: Boolean = true,
    val outputFormat: String = "JPG" // "ORIGINAL", "JPG", "JPEG", "PNG", "WEBP_LOSSY", "WEBP_LOSSLESS"
)

object ExifMetadataHelper {
    private const val TAG = "ExifMetadataHelper"

    fun clearLog(context: Context) {
        try {
            val logFile = File(context.cacheDir, "exif_log.txt")
            if (logFile.exists()) logFile.delete()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun log(context: Context, message: String) {
        Log.d("ExifCopy", message)
        try {
            val logFile = File(context.cacheDir, "exif_log.txt")
            logFile.appendText("[${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())}] $message\n")
        } catch (e: Exception) {
            Log.e("ExifCopy", "Error logging to file", e)
        }
    }

    fun flushLogToPublicStorage(context: Context) {
        try {
            val logFile = File(context.cacheDir, "exif_log.txt")
            if (logFile.exists()) {
                saveLogToPublicPictures(context, logFile)
            }
        } catch (e: Exception) {
            Log.e("ExifCopy", "Error flushing log", e)
        }
    }

    fun getLogContents(context: Context): String {
        return try {
            val logFile = File(context.cacheDir, "exif_log.txt")
            if (logFile.exists()) {
                logFile.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun saveLogToPublicPictures(context: Context, cacheLogFile: File) {
        try {
            val resolver = context.contentResolver
            val mimeType = "text/plain"
            val fileName = "exif_log.txt"
            
            var logUri: Uri? = null
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND (${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?)"
            } else {
                "${MediaStore.MediaColumns.DATA} = ?"
            }
            val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(fileName, "Pictures/ExifCopy/", "Pictures/ExifCopy")
            } else {
                val path = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "ExifCopy/$fileName"
                ).absolutePath
                arrayOf(path)
            }
            
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    logUri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
                }
            }
            
            if (logUri == null) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ExifCopy")
                    }
                }
                logUri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            }
            
            if (logUri != null) {
                resolver.openOutputStream(logUri!!, "wt")?.use { output ->
                    cacheLogFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            // Quietly ignore
        }
    }

    private fun openStreamSafely(context: Context, uri: Uri): InputStream? {
        try {
            val isStream = context.contentResolver.openInputStream(uri)
            if (isStream != null) return isStream
        } catch (e: Exception) {
            log(context, "openInputStream warning: ${e.message}")
        }
        if (uri.path != null) {
            try {
                val file = File(uri.path!!)
                if (file.exists()) {
                    return FileInputStream(file)
                }
            } catch (e: Exception) {
                log(context, "FileInputStream error: ${e.message}")
            }
        }
        return null
    }

    private fun getMimeTypeSafely(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)
        if (type != null && type.isNotBlank()) return type
        val path = uri.path ?: return "image/jpeg"
        return when {
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".webp", ignoreCase = true) -> "image/webp"
            path.endsWith(".heic", ignoreCase = true) || path.endsWith(".heif", ignoreCase = true) -> "image/heic"
            else -> "image/jpeg"
        }
    }

    private val TAGS_TO_COPY = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_SENSING_METHOD,
        ExifInterface.TAG_CUSTOM_RENDERED,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_GAIN_CONTROL,
        ExifInterface.TAG_CONTRAST,
        ExifInterface.TAG_SATURATION,
        ExifInterface.TAG_SHARPNESS,
        ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_XMP
    )


    fun copyMetadata(
        context: Context,
        sourceUri: Uri,
        targetUri: Uri,
        settings: ExifSettings? = null,
        itemIndex: Int = 0,
        replaceOriginal: Boolean = false,
        removeWatermark: Boolean = false,
        watermarkMode: GeminiWatermarkRemover.WatermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA
    ): Uri? {
        return copyMetadataList(
            context = context,
            sourceUri = sourceUri,
            targetUri = targetUri,
            settings = settings,
            itemIndex = itemIndex,
            replaceOriginal = replaceOriginal,
            removeWatermark = removeWatermark,
            watermarkMode = watermarkMode
        ).lastOrNull()
    }

    fun copyMetadataList(
        context: Context,
        sourceUri: Uri,
        targetUri: Uri,
        settings: ExifSettings? = null,
        itemIndex: Int = 0,
        replaceOriginal: Boolean = false,
        removeWatermark: Boolean = false,
        watermarkMode: GeminiWatermarkRemover.WatermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA
    ): List<Uri> {
        val savedUris = mutableListOf<Uri>()
        try {
            log(context, "--- BẮT ĐẦU SAO CHÉP EXIF ---")
            log(context, "Source URI: $sourceUri")
            log(context, "Target URI: $targetUri")
            log(context, "Replace Original: $replaceOriginal")
            log(context, "Settings: $settings")

            // Get tags to copy based on settings (default is true for all)
            val copyCamera = settings?.copyCameraInfo ?: true
            val copyShooting = settings?.copyShootingInfo ?: true
            val copyGps = settings?.copyGpsInfo ?: true
            val copyDateTaken = settings?.copyDateTaken ?: true
            val copyCreated = settings?.copyCreatedDate ?: true
            val copyXmp = settings?.copyXmpInfo ?: true

            val tagsToFilter = HashSet<String>()
            if (copyCamera) {
                tagsToFilter.addAll(listOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_LENS_MAKE,
                    ExifInterface.TAG_LENS_MODEL,
                    ExifInterface.TAG_SOFTWARE
                ))
            }
            if (copyShooting) {
                tagsToFilter.addAll(listOf(
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_ISO_SPEED_RATINGS,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_WHITE_BALANCE,
                    ExifInterface.TAG_EXPOSURE_PROGRAM,
                    ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
                    ExifInterface.TAG_METERING_MODE,
                    ExifInterface.TAG_LIGHT_SOURCE,
                    ExifInterface.TAG_SENSING_METHOD,
                    ExifInterface.TAG_CUSTOM_RENDERED,
                    ExifInterface.TAG_EXPOSURE_MODE,
                    ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
                    ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                    ExifInterface.TAG_SCENE_CAPTURE_TYPE,
                    ExifInterface.TAG_GAIN_CONTROL,
                    ExifInterface.TAG_CONTRAST,
                    ExifInterface.TAG_SATURATION,
                    ExifInterface.TAG_SHARPNESS,
                    ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
                    ExifInterface.TAG_USER_COMMENT
                ))
            }
            if (copyGps) {
                tagsToFilter.addAll(listOf(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_DATESTAMP,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_PROCESSING_METHOD
                ))
            }
            if (copyDateTaken) {
                tagsToFilter.addAll(listOf(
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED
                ))
            }
            if (copyCreated) {
                tagsToFilter.add(ExifInterface.TAG_DATETIME)
            }
            if (copyXmp) {
                tagsToFilter.add(ExifInterface.TAG_XMP)
            }

            // 1. Read EXIF attributes from sourceUri
            val sourceTempFile = File.createTempFile("exif_source_temp", ".jpg", context.cacheDir)
            val inputStream = try {
                context.contentResolver.openInputStream(sourceUri)
            } catch (e: Exception) {
                if (sourceUri.scheme == "file" && sourceUri.path != null) {
                    java.io.FileInputStream(File(sourceUri.path!!))
                } else null
            }
            if (inputStream == null) {
                log(context, "LỖI: Không thể mở stream từ URI: $sourceUri")
                return emptyList()
            }
            inputStream.use { input ->
                sourceTempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            log(context, "Đã sao chép ảnh nguồn sang file temp: ${sourceTempFile.absolutePath}")

            val sourceAttributes = HashMap<String, String>()
            val sourceExif = ExifInterface(sourceTempFile.absolutePath)
            log(context, "Đọc EXIF từ ảnh nguồn:")
            for (tag in TAGS_TO_COPY) {
                val rawVal = sourceExif.getAttribute(tag)
                if (rawVal != null) {
                    log(context, "  [Tag gốc] $tag = $rawVal")
                }
                if (tag in tagsToFilter) {
                    val value = rawVal
                    if (value != null) {
                        sourceAttributes[tag] = value
                    }
                }
            }
            sourceTempFile.delete()

            // 2. Perform adjustments (randomization/fixed values) on the attributes
            if (settings != null) {
                if (copyCamera) {
                    if (sourceAttributes[ExifInterface.TAG_MAKE].isNullOrBlank()) {
                        sourceAttributes[ExifInterface.TAG_MAKE] = "Google"
                        log(context, "Gán mặc định Make = Google")
                    }
                    if (sourceAttributes[ExifInterface.TAG_MODEL].isNullOrBlank()) {
                        sourceAttributes[ExifInterface.TAG_MODEL] = "Pixel 7 Pro"
                        log(context, "Gán mặc định Model = Pixel 7 Pro")
                    }
                }
                if (copyShooting) {
                    if (sourceAttributes[ExifInterface.TAG_ISO_SPEED_RATINGS].isNullOrBlank()) {
                        sourceAttributes[ExifInterface.TAG_ISO_SPEED_RATINGS] = "100"
                        log(context, "Gán mặc định ISO = 100")
                    }

                    // Adjust Shutter Speed (Exposure Time)
                    if (settings.shutterFixedValue != null && settings.shutterFixedValue.isNotBlank()) {
                        sourceAttributes[ExifInterface.TAG_EXPOSURE_TIME] = settings.shutterFixedValue
                        log(context, "Gán fixed Shutter = ${settings.shutterFixedValue}")
                    } else if (settings.randomizeShutter) {
                        val originalStr = sourceAttributes[ExifInterface.TAG_EXPOSURE_TIME] ?: "1/125"
                        val originalValue = parseExposureTime(originalStr)
                        if (originalValue > 0) {
                            val factor = 1.0 + (Random.nextDouble(settings.shutterMinOffsetPct, settings.shutterMaxOffsetPct) / 100.0)
                            val newValue = originalValue * factor
                            sourceAttributes[ExifInterface.TAG_EXPOSURE_TIME] = formatExposureTime(newValue)
                            log(context, "Random Shutter: $originalStr -> ${sourceAttributes[ExifInterface.TAG_EXPOSURE_TIME]}")
                        }
                    }

                    // Adjust Aperture (FNumber)
                    if (settings.apertureFixedValue != null && settings.apertureFixedValue.isNotBlank()) {
                        sourceAttributes[ExifInterface.TAG_F_NUMBER] = settings.apertureFixedValue
                        log(context, "Gán fixed Aperture = ${settings.apertureFixedValue}")
                    } else if (settings.randomizeAperture) {
                        val originalStr = sourceAttributes[ExifInterface.TAG_F_NUMBER] ?: "2.8"
                        val originalValue = originalStr.toDoubleOrNull()
                        if (originalValue != null) {
                            val offset = Random.nextDouble(settings.apertureMinOffset, settings.apertureMaxOffset)
                            val newValue = (originalValue + offset).coerceAtLeast(1.0).coerceAtMost(22.0)
                            sourceAttributes[ExifInterface.TAG_F_NUMBER] = String.format(Locale.US, "%.1f", newValue)
                            log(context, "Random Aperture: $originalStr -> ${sourceAttributes[ExifInterface.TAG_F_NUMBER]}")
                        }
                    }

                    // Adjust Focal Length
                    if (settings.focalFixedValue != null && settings.focalFixedValue.isNotBlank()) {
                        sourceAttributes[ExifInterface.TAG_FOCAL_LENGTH] = settings.focalFixedValue
                        log(context, "Gán fixed Focal Length = ${settings.focalFixedValue}")
                    } else if (settings.randomizeFocalLength) {
                        val originalStr = sourceAttributes[ExifInterface.TAG_FOCAL_LENGTH] ?: "50"
                        val originalValue = parseFocalLength(originalStr)
                        if (originalValue > 0) {
                            val offset = Random.nextDouble(settings.focalMinOffset, settings.focalMaxOffset)
                            val newValue = (originalValue + offset).coerceAtLeast(1.0)
                            sourceAttributes[ExifInterface.TAG_FOCAL_LENGTH] = String.format(Locale.US, "%.1f", newValue)
                            log(context, "Random Focal Length: $originalStr -> ${sourceAttributes[ExifInterface.TAG_FOCAL_LENGTH]}")
                        }
                    }
                }

                // Adjust DateTime
                if (settings.randomizeTime && (copyDateTaken || copyCreated)) {
                    val originalDateTimeStr = sourceAttributes[ExifInterface.TAG_DATETIME_ORIGINAL]
                        ?: sourceAttributes[ExifInterface.TAG_DATETIME]
                    
                    val parsedDate = if (originalDateTimeStr != null) {
                        parseExifDate(originalDateTimeStr)
                    } else {
                        Date() // Default to now if not present
                    }

                    if (parsedDate != null) {
                        val cal = Calendar.getInstance()
                        cal.time = parsedDate
                        // Add base offset plus sequential offset per index
                        val extraSeconds = settings.baseTimeAddSecs + (itemIndex * Random.nextLong(settings.timeMinSecs, settings.timeMaxSecs + 1))
                        cal.add(Calendar.SECOND, extraSeconds.toInt())
                        
                        val newDateStr = formatExifDate(cal.time)
                        if (copyCreated) {
                            sourceAttributes[ExifInterface.TAG_DATETIME] = newDateStr
                        }
                        if (copyDateTaken) {
                            sourceAttributes[ExifInterface.TAG_DATETIME_ORIGINAL] = newDateStr
                            sourceAttributes[ExifInterface.TAG_DATETIME_DIGITIZED] = newDateStr
                        }
                        log(context, "Adjusted DateTime to: $newDateStr")
                    }
                }
            }

            // 3. Write metadata to target
            val copyFileName = settings?.copyFileName ?: false
            val baseFileNameUri = if (copyFileName) sourceUri else targetUri
            log(context, "copyFileName setting: $copyFileName, baseFileNameUri: $baseFileNameUri")
            val rawFileName = getFileName(context, baseFileNameUri) ?: "exif_copied_${System.currentTimeMillis()}"

            // Detect target mime type to decide output format (PNG/JPEG)
            val targetMimeType = getMimeTypeSafely(context, targetUri)
            val originalExt = rawFileName.substringAfterLast(".", "")
            val targetExt = if (originalExt.isNotEmpty()) {
                ".$originalExt"
            } else {
                when {
                    targetMimeType.contains("png", ignoreCase = true) -> ".png"
                    targetMimeType.contains("webp", ignoreCase = true) -> ".webp"
                    targetMimeType.contains("heic", ignoreCase = true) || targetMimeType.contains("heif", ignoreCase = true) -> ".heic"
                    else -> ".jpg"
                }
            }

            // Determine output mime type and extension based on Settings dialog
            val finalFormatSetting = settings?.outputFormat ?: "ORIGINAL"
            val (outputMimeType, tempExt) = when (finalFormatSetting) {
                "JPG" -> "image/jpeg" to ".jpg"
                "JPEG" -> "image/jpeg" to ".jpeg"
                "PNG" -> "image/png" to ".png"
                "WEBP_LOSSY", "WEBP_LOSSLESS" -> "image/webp" to ".webp"
                else -> targetMimeType to targetExt.lowercase(Locale.US)
            }
            log(context, "Target MIME type: $targetMimeType ($targetExt) -> Output MIME type: $outputMimeType ($tempExt)")
            
            // Create a local temp file with matching extension
            val tempFile = File.createTempFile("exif_target_temp", tempExt, context.cacheDir)
            val needsConversion = if (finalFormatSetting == "ORIGINAL") {
                false
            } else {
                when {
                    finalFormatSetting == "WEBP_LOSSLESS" || finalFormatSetting == "WEBP_LOSSY" -> true
                    outputMimeType == targetMimeType -> false
                    outputMimeType == "image/jpeg" && (targetMimeType == "image/jpeg" || targetMimeType == "image/jpg") -> false
                    outputMimeType == "image/png" && targetMimeType == "image/png" -> false
                    else -> true
                }
            }
            if (needsConversion) {
                log(context, "Thực hiện chuyển đổi định dạng ảnh từ $targetMimeType sang $outputMimeType...")
                context.contentResolver.openInputStream(targetUri)?.use { input ->
                    val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        FileOutputStream(tempFile).use { output ->
                            val compressFormat = when (finalFormatSetting) {
                                "PNG" -> android.graphics.Bitmap.CompressFormat.PNG
                                "WEBP_LOSSLESS" -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS
                                    } else {
                                        @Suppress("DEPRECATION")
                                        android.graphics.Bitmap.CompressFormat.WEBP
                                    }
                                }
                                "WEBP_LOSSY" -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                                    } else {
                                        @Suppress("DEPRECATION")
                                        android.graphics.Bitmap.CompressFormat.WEBP
                                    }
                                }
                                else -> android.graphics.Bitmap.CompressFormat.JPEG
                            }
                            bitmap.compress(compressFormat, 100, output)
                        }
                        bitmap.recycle()
                        log(context, "Chuyển đổi định dạng thành công.")
                    } else {
                        throw Exception("Không thể giải mã ảnh để chuyển đổi định dạng")
                    }
                }
            } else {
                openStreamSafely(context, targetUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                log(context, "Sao chép file gốc sang temp thành công.")
            }
            log(context, "Tạo temp file thành công: ${tempFile.absolutePath}")

            val modesToRun = if (removeWatermark && watermarkMode == GeminiWatermarkRemover.WatermarkMode.ALL_MODES) {
                listOf(
                    GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA to "_reverse_alpha",
                    GeminiWatermarkRemover.WatermarkMode.IDW_INPAINT to "_idw_inpaint",
                    GeminiWatermarkRemover.WatermarkMode.OPENCV_INPAINT to "_opencv_inpaint",
                    GeminiWatermarkRemover.WatermarkMode.AI_MODEL to "_ai_model"
                )
            } else {
                listOf(watermarkMode to "")
            }

            for ((subMode, suffix) in modesToRun) {
                val runTempFile = File.createTempFile("exif_temp_run", tempExt, context.cacheDir)
                tempFile.copyTo(runTempFile, overwrite = true)

                if (removeWatermark) {
                    try {
                        val decodeOptions = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inMutable = true
                        }
                        val bitmap = BitmapFactory.decodeFile(runTempFile.absolutePath, decodeOptions)
                        if (bitmap != null) {
                            val result = GeminiWatermarkRemover.processImage(bitmap, subMode)
                            log(context, "Đã xử lý xóa watermark Gemini (${subMode.displayName}, detected=${result.detected}, match=${result.match}).")
                            FileOutputStream(runTempFile).use { output ->
                                val compressFormat = when {
                                    tempExt.equals(".PNG", ignoreCase = true) -> android.graphics.Bitmap.CompressFormat.PNG
                                    tempExt.equals(".WEBP", ignoreCase = true) -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS
                                        } else {
                                            @Suppress("DEPRECATION")
                                            android.graphics.Bitmap.CompressFormat.WEBP
                                        }
                                    }
                                    else -> android.graphics.Bitmap.CompressFormat.JPEG
                                }
                                val quality = if (compressFormat == android.graphics.Bitmap.CompressFormat.PNG) 100 else 95
                                result.bitmap.compress(compressFormat, quality, output)
                            }
                            if (result.bitmap !== bitmap) {
                                result.bitmap.recycle()
                            }
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        log(context, "Lỗi khi xóa watermark Gemini (${subMode.displayName}): ${e.message}")
                    }
                }

                // Write properties to the temp file
                val targetExif = ExifInterface(runTempFile.absolutePath)
                for ((tag, value) in sourceAttributes) {
                    targetExif.setAttribute(tag, value)
                }
                try {
                    targetExif.saveAttributes()
                    log(context, "Đã ghi attributes vào temp file (${subMode.displayName}).")
                } catch (e: Exception) {
                    log(context, "Lưu EXIF attributes không bắt buộc: ${e.message}")
                }

                // Save to public storage (Pictures/ExifCopy)
                val baseName = getBaseName(rawFileName)
                val nameWithSuffix = "$baseName$suffix"
                val fileName = getUniqueFileName(context, nameWithSuffix, tempExt)
                log(context, "Tên file đích cuối cùng: $fileName")
                val savedUri = saveToPublicPictures(context, runTempFile, fileName, outputMimeType)
                if (savedUri != null) {
                    savedUris.add(savedUri)
                    log(context, "Đã lưu bản sao vào Pictures/ExifCopy: $fileName. Output URI: $savedUri")
                }
                runTempFile.delete()
            }

            tempFile.delete()
            flushLogToPublicStorage(context)
        } catch (e: Exception) {
            val fullError = "LỖI TRONG copyMetadataList: ${e.message}\n${Log.getStackTraceString(e)}"
            log(context, fullError)
        }
        return savedUris
    }

    fun cleanGoogleAiMetadata(
        context: Context,
        targetUri: Uri,
        replaceOriginal: Boolean,
        removeWatermark: Boolean = false,
        watermarkMode: GeminiWatermarkRemover.WatermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA
    ): Uri? {
        try {
            log(context, "--- BẮT ĐẦU XÓA NHÃN AI ---")
            log(context, "Target URI: $targetUri")
            log(context, "Replace Original: $replaceOriginal")
            log(context, "Remove Watermark: $removeWatermark")
            log(context, "Watermark Mode: ${watermarkMode.displayName}")

            val targetMimeType = getMimeTypeSafely(context, targetUri)
            val tempExt = when {
                targetMimeType.contains("png", ignoreCase = true) -> ".png"
                targetMimeType.contains("webp", ignoreCase = true) -> ".webp"
                targetMimeType.contains("heic", ignoreCase = true) || targetMimeType.contains("heif", ignoreCase = true) -> ".heic"
                else -> ".jpg"
            }
            
            val tempFile = File.createTempFile("exif_clean_temp", tempExt, context.cacheDir)
            openStreamSafely(context, targetUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val modesToRun = if (removeWatermark && watermarkMode == GeminiWatermarkRemover.WatermarkMode.ALL_MODES) {
                listOf(
                    GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA to "_reverse_alpha",
                    GeminiWatermarkRemover.WatermarkMode.IDW_INPAINT to "_idw_inpaint",
                    GeminiWatermarkRemover.WatermarkMode.OPENCV_INPAINT to "_opencv_inpaint",
                    GeminiWatermarkRemover.WatermarkMode.AI_MODEL to "_ai_model"
                )
            } else {
                listOf(watermarkMode to "")
            }

            var lastSavedUri: Uri? = null
            val rawFileName = getFileName(context, targetUri) ?: "clean_${System.currentTimeMillis()}"
            val baseName = "clean_" + getBaseName(rawFileName)

            for ((subMode, suffix) in modesToRun) {
                val runTempFile = File.createTempFile("exif_clean_run", tempExt, context.cacheDir)
                tempFile.copyTo(runTempFile, overwrite = true)

                if (removeWatermark) {
                    try {
                        val decodeOptions = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inMutable = true
                            inPremultiplied = false
                        }
                        val bitmap = BitmapFactory.decodeFile(runTempFile.absolutePath, decodeOptions)
                        if (bitmap != null) {
                            val result = GeminiWatermarkRemover.processImage(bitmap, subMode)
                            log(context, "Đã xử lý xóa watermark Gemini (${subMode.displayName}, detected=${result.detected}, match=${result.match}).")
                            val compressed = FileOutputStream(runTempFile).use { output ->
                                val compressFormat = when {
                                    tempExt.contains("png", ignoreCase = true) -> android.graphics.Bitmap.CompressFormat.PNG
                                    tempExt.contains("webp", ignoreCase = true) -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS
                                        } else {
                                            @Suppress("DEPRECATION")
                                            android.graphics.Bitmap.CompressFormat.WEBP
                                        }
                                    }
                                    else -> android.graphics.Bitmap.CompressFormat.JPEG
                                }
                                val quality = if (compressFormat == android.graphics.Bitmap.CompressFormat.PNG) 100 else 95
                                val res = result.bitmap.compress(compressFormat, quality, output)
                                output.flush()
                                res
                            }
                            log(context, "Compress result: $compressed, runTempFile size: ${runTempFile.length()}")
                            if (result.bitmap !== bitmap) {
                                result.bitmap.recycle()
                            }
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        log(context, "Lỗi khi xóa watermark Gemini (${subMode.displayName}): ${e.message}")
                    }
                }

                val exif = ExifInterface(runTempFile.absolutePath)
                val xmp = exif.getAttribute(ExifInterface.TAG_XMP)
                if (xmp != null) {
                    var cleanedXmp = xmp
                    val creditRegex = """<photoshop:Credit>[^<]*</photoshop:Credit>""".toRegex()
                    cleanedXmp = cleanedXmp.replace(creditRegex, "")

                    val dstRegex = """<Iptc4xmpExt:DigitalSourceType>[^<]*</Iptc4xmpExt:DigitalSourceType>""".toRegex()
                    cleanedXmp = cleanedXmp.replace(dstRegex, "")

                    val hasExtendedXmpRegex = """<xmpNote:HasExtendedXMP>[^<]*</xmpNote:HasExtendedXMP>""".toRegex()
                    cleanedXmp = cleanedXmp.replace(hasExtendedXmpRegex, "")

                    exif.setAttribute(ExifInterface.TAG_XMP, cleanedXmp)
                    log(context, "Đã làm sạch thẻ XMP AI.")
                }

                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                if (software != null && (software.contains("Google", ignoreCase = true) || software.contains("AI", ignoreCase = true))) {
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Android Camera")
                    log(context, "Ghi đè Software: $software -> Android Camera")
                }

                val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                if (comment != null && (comment.contains("Google", ignoreCase = true) || comment.contains("AI", ignoreCase = true))) {
                    exif.setAttribute(ExifInterface.TAG_USER_COMMENT, null)
                    log(context, "Xóa UserComment: $comment")
                }

                try {
                    exif.saveAttributes()
                    log(context, "Đã lưu attributes đã làm sạch vào temp file (${subMode.displayName}).")
                } catch (e: Exception) {
                    log(context, "Bỏ qua saveAttributes nếu không hỗ trợ EXIF: ${e.message}")
                }

                val nameWithSuffix = "$baseName$suffix"
                val fileName = getUniqueFileName(context, nameWithSuffix, tempExt)
                val savedUri = saveToPublicPictures(context, runTempFile, fileName, targetMimeType)
                log(context, "Đã lưu bản sao vào Pictures/ExifCopy: $fileName. Output URI: $savedUri")
                runTempFile.delete()
                lastSavedUri = savedUri
            }

            tempFile.delete()
            flushLogToPublicStorage(context)
            return lastSavedUri

        } catch (e: Exception) {
            val fullError = "LỖI TRONG cleanGoogleAiMetadata: ${e.message}\n${Log.getStackTraceString(e)}"
            log(context, fullError)
            return null
        }
    }

    fun cleanGoogleAiMetadataList(
        context: Context,
        targetUri: Uri,
        replaceOriginal: Boolean,
        removeWatermark: Boolean = false,
        watermarkMode: GeminiWatermarkRemover.WatermarkMode = GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA
    ): List<Uri> {
        val savedUris = mutableListOf<Uri>()
        try {
            log(context, "--- BẮT ĐẦU XÓA NHÃN AI (LIST) ---")
            val targetMimeType = getMimeTypeSafely(context, targetUri)
            val tempExt = when {
                targetMimeType.contains("png", ignoreCase = true) -> ".png"
                targetMimeType.contains("webp", ignoreCase = true) -> ".webp"
                targetMimeType.contains("heic", ignoreCase = true) || targetMimeType.contains("heif", ignoreCase = true) -> ".heic"
                else -> ".jpg"
            }
            
            val tempFile = File.createTempFile("exif_clean_temp", tempExt, context.cacheDir)
            openStreamSafely(context, targetUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val modesToRun = if (removeWatermark && watermarkMode == GeminiWatermarkRemover.WatermarkMode.ALL_MODES) {
                listOf(
                    GeminiWatermarkRemover.WatermarkMode.REVERSE_ALPHA to "_reverse_alpha",
                    GeminiWatermarkRemover.WatermarkMode.IDW_INPAINT to "_idw_inpaint",
                    GeminiWatermarkRemover.WatermarkMode.OPENCV_INPAINT to "_opencv_inpaint",
                    GeminiWatermarkRemover.WatermarkMode.AI_MODEL to "_ai_model"
                )
            } else {
                listOf(watermarkMode to "")
            }

            val rawFileName = getFileName(context, targetUri) ?: "clean_${System.currentTimeMillis()}"
            val baseName = "clean_" + getBaseName(rawFileName)

            for ((subMode, suffix) in modesToRun) {
                val runTempFile = File.createTempFile("exif_clean_run", tempExt, context.cacheDir)
                tempFile.copyTo(runTempFile, overwrite = true)

                if (removeWatermark) {
                    try {
                        val decodeOptions = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inMutable = true
                            inPremultiplied = false
                        }
                        val bitmap = BitmapFactory.decodeFile(runTempFile.absolutePath, decodeOptions)
                        if (bitmap != null) {
                            val result = GeminiWatermarkRemover.processImage(bitmap, subMode)
                            log(context, "Đã xử lý xóa watermark Gemini (${subMode.displayName}, detected=${result.detected}, match=${result.match}).")
                            FileOutputStream(runTempFile).use { output ->
                                val compressFormat = when {
                                    tempExt.contains("png", ignoreCase = true) -> android.graphics.Bitmap.CompressFormat.PNG
                                    tempExt.contains("webp", ignoreCase = true) -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS
                                        } else {
                                            @Suppress("DEPRECATION")
                                            android.graphics.Bitmap.CompressFormat.WEBP
                                        }
                                    }
                                    else -> android.graphics.Bitmap.CompressFormat.JPEG
                                }
                                val quality = if (compressFormat == android.graphics.Bitmap.CompressFormat.PNG) 100 else 95
                                result.bitmap.compress(compressFormat, quality, output)
                                output.flush()
                            }
                            if (result.bitmap !== bitmap) {
                                result.bitmap.recycle()
                            }
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        log(context, "Lỗi khi xóa watermark Gemini (${subMode.displayName}): ${e.message}")
                    }
                }

                val exif = ExifInterface(runTempFile.absolutePath)
                val xmp = exif.getAttribute(ExifInterface.TAG_XMP)
                if (xmp != null) {
                    var cleanedXmp = xmp
                    val creditRegex = """<photoshop:Credit>[^<]*</photoshop:Credit>""".toRegex()
                    cleanedXmp = cleanedXmp.replace(creditRegex, "")

                    val dstRegex = """<Iptc4xmpExt:DigitalSourceType>[^<]*</Iptc4xmpExt:DigitalSourceType>""".toRegex()
                    cleanedXmp = cleanedXmp.replace(dstRegex, "")

                    val hasExtendedXmpRegex = """<xmpNote:HasExtendedXMP>[^<]*</xmpNote:HasExtendedXMP>""".toRegex()
                    cleanedXmp = cleanedXmp.replace(hasExtendedXmpRegex, "")

                    exif.setAttribute(ExifInterface.TAG_XMP, cleanedXmp)
                }

                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                if (software != null && (software.contains("Google", ignoreCase = true) || software.contains("AI", ignoreCase = true))) {
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Android Camera")
                }

                val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                if (comment != null && (comment.contains("Google", ignoreCase = true) || comment.contains("AI", ignoreCase = true))) {
                    exif.setAttribute(ExifInterface.TAG_USER_COMMENT, null)
                }

                try {
                    exif.saveAttributes()
                } catch (e: Exception) {
                    log(context, "Bỏ qua saveAttributes: ${e.message}")
                }

                val nameWithSuffix = "$baseName$suffix"
                val fileName = getUniqueFileName(context, nameWithSuffix, tempExt)
                val savedUri = saveToPublicPictures(context, runTempFile, fileName, targetMimeType)
                if (savedUri != null) {
                    savedUris.add(savedUri)
                    log(context, "Đã lưu bản sao vào Pictures/ExifCopy: $fileName. Output URI: $savedUri")
                }
                runTempFile.delete()
            }

            tempFile.delete()
            flushLogToPublicStorage(context)
        } catch (e: Exception) {
            log(context, "LỖI TRONG cleanGoogleAiMetadataList: ${e.message}")
        }
        return savedUris
    }

    private fun saveToPublicPictures(context: Context, sourceFile: File, fileName: String, mimeType: String): Uri? {
        try {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ExifCopy")
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)
            sourceFile.copyTo(targetFile, overwrite = true)
            log(context, "Đã sao chép trực tiếp vào file: ${targetFile.absolutePath} (size=${targetFile.length()})")

            val uri = Uri.fromFile(targetFile)
            forceScanFile(context, uri, mimeType, fileName)
            return uri
        } catch (e: Exception) {
            log(context, "Lỗi copy file trực tiếp: ${e.message}, dùng MediaStore fallback")
        }

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ExifCopy")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    resolver.update(imageUri, contentValues, null, null)
                }
                forceScanFile(context, imageUri, mimeType, fileName)
                return imageUri
            } catch (e: Exception) {
                log(context, "Lỗi copy file vào MediaStore URI $imageUri: ${e.message}")
            }
        }
        return null
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index != -1) {
                        return cursor.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            // Quietly ignore
        }
        return null
    }

    private fun forceScanFile(context: Context, uri: Uri, mimeType: String, targetFileName: String? = null) {
        val path = getFilePathFromUri(context, uri)
        if (path != null) {
            log(context, "Yêu cầu MediaScanner quét lại file: $path")
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(path),
                arrayOf(mimeType)
            ) { scannedPath, scannedUri ->
                log(context, "MediaScanner quét xong: $scannedPath -> $scannedUri")
                if (scannedUri != null && targetFileName != null) {
                    try {
                        val updateValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                        }
                        context.contentResolver.update(scannedUri, updateValues, null, null)
                        log(context, "Đã cập nhật lại DISPLAY_NAME của file đã quét: $targetFileName")
                    } catch (e: Exception) {
                        log(context, "Lỗi cập nhật lại DISPLAY_NAME: ${e.message}")
                    }
                }
            }
        } else {
            try {
                context.contentResolver.notifyChange(uri, null)
                log(context, "Đã gửi thông báo notifyChange cho URI: $uri")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        // Strip illegal filesystem characters: \ / : * ? " < > |
        val reservedChars = "[|\\\\?*<\\\":>/]"
        return name.replace(reservedChars.toRegex(), "_")
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        log(context, "Querying name for Uri: $uri")
        var name: String? = null
        
        if (uri.scheme == "file") {
            name = uri.lastPathSegment
            log(context, "URI scheme is file. Name: $name")
            return name?.let { sanitizeFileName(it) }
        }

        // Try extracting ID if it is a PhotoPicker URI and lookup in system MediaStore
        if (uri.authority == "media" && uri.pathSegments.any { it.contains("photopicker") }) {
            val id = uri.lastPathSegment
            if (id != null && id.all { it.isDigit() }) {
                log(context, "Detected PhotoPicker URI. Looking up ID $id in MediaStore...")
                val mediaStoreUri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id)
                try {
                    context.contentResolver.query(
                        mediaStoreUri,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(0)
                            log(context, "Found original name in MediaStore: $name")
                        }
                    }
                } catch (e: Exception) {
                    log(context, "Failed to lookup original name in MediaStore: ${e.message}")
                }
            }
        }

        // Try OpenableColumns.DISPLAY_NAME with exact projection
        if (name.isNullOrBlank()) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        log(context, "Retrieved name via OpenableColumns: $name")
                    }
                }
            } catch (e: Exception) {
                log(context, "Error getting filename via OpenableColumns: ${e.message}")
            }
        }
        
        // Try MediaStore.MediaColumns.DISPLAY_NAME fallback
        if (name.isNullOrBlank()) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        log(context, "Retrieved name via MediaStore: $name")
                    }
                }
            } catch (e: Exception) {
                log(context, "Error getting filename via MediaStore: ${e.message}")
            }
        }
        
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment
            log(context, "Fallback to lastPathSegment: $name")
        }
        
        return name?.let { sanitizeFileName(it) }
    }

    private fun parseExposureTime(shutterStr: String): Double {
        return try {
            if (shutterStr.contains("/")) {
                val parts = shutterStr.split("/")
                if (parts.size == 2) {
                    val num = parts[0].toDoubleOrNull() ?: 0.0
                    val den = parts[1].toDoubleOrNull() ?: 1.0
                    num / den
                } else {
                    0.0
                }
            } else {
                shutterStr.toDoubleOrNull() ?: 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun formatExposureTime(shutter: Double): String {
        return if (shutter < 1.0) {
            val denom = Math.round(1.0 / shutter)
            "1/$denom"
        } else {
            String.format(Locale.US, "%.1f", shutter)
        }
    }

    private fun parseFocalLength(focalStr: String): Double {
        val cleanStr = focalStr.replace("mm", "", ignoreCase = true).trim()
        if (cleanStr.contains("/")) {
            val parts = cleanStr.split("/")
            if (parts.size == 2) {
                val num = parts[0].toDoubleOrNull() ?: 0.0
                val den = parts[1].toDoubleOrNull() ?: 1.0
                return num / den
            }
        }
        return cleanStr.toDoubleOrNull() ?: 0.0
    }

    private fun parseExifDate(dateStr: String): Date? {
        val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        return try {
            format.parse(dateStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatExifDate(date: Date): String {
        val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        return format.format(date)
    }



    private fun getCleanFileName(originalName: String, extension: String): String {
        val nameWithoutExtension = originalName.substringBeforeLast(".")
        return "$nameWithoutExtension$extension"
    }

    private fun getBaseName(originalName: String): String {
        return originalName.substringBeforeLast(".")
    }

    private fun doesFileExistInMediaStore(context: Context, fileName: String): Boolean {
        log(context, "Checking if file exists in MediaStore: $fileName")
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection: String
        val selectionArgs: Array<String>
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND (${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?)"
            selectionArgs = arrayOf(fileName, "Pictures/ExifCopy/", "Pictures/ExifCopy")
        } else {
            val path = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ExifCopy/$fileName"
            ).absolutePath
            selection = "${MediaStore.MediaColumns.DATA} = ?"
            selectionArgs = arrayOf(path)
        }

        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val exists = cursor.count > 0
                log(context, "  doesFileExistInMediaStore returned $exists (count: ${cursor.count})")
                return exists
            }
        } catch (e: Exception) {
            log(context, "  Error checking existence in MediaStore: ${e.message}")
        }
        return false
    }

    private fun getUniqueFileName(context: Context, baseName: String, extension: String): String {
        log(context, "getUniqueFileName for baseName: $baseName, extension: $extension")
        var candidate = "$baseName$extension"
        if (!doesFileExistInMediaStore(context, candidate)) {
            log(context, "  Candidate is unique: $candidate")
            return candidate
        }
        var counter = 2
        while (true) {
            candidate = "${baseName}_$counter$extension"
            if (!doesFileExistInMediaStore(context, candidate)) {
                log(context, "  Found unique candidate after sequence: $candidate")
                return candidate
            }
            counter++
        }
    }
}
