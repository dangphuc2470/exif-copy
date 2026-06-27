 package com.phucdnh.exifcopy

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
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
    val copyFileName: Boolean = false, // Changed default to false (keeps target filename by default)
    val copyXmpInfo: Boolean = true,
    val outputFormat: String = "ORIGINAL" // "ORIGINAL", "JPG", "JPEG", "PNG"
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
            saveLogToPublicPictures(context, logFile)
        } catch (e: Exception) {
            Log.e("ExifCopy", "Error logging to file", e)
        }
    }

    fun getLogContents(context: Context): String {
        return try {
            val logFile = File(context.cacheDir, "exif_log.txt")
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "Chưa có nhật ký ghi chép."
            }
        } catch (e: Exception) {
            "Lỗi đọc nhật ký: ${e.message}"
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
        settings: ExifSettings?,
        itemIndex: Int, // used to apply incremental random time offset
        replaceOriginal: Boolean
    ): Uri? {
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
            // Create a temporary file for the source image first to ensure ExifInterface can seek/parse 100% correctly
            val sourceTempFile = File.createTempFile("exif_source_temp", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(sourceTempFile).use { output ->
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
                    rawVal?.let { value ->
                        sourceAttributes[tag] = value
                        log(context, "  [Sẽ sao chép] $tag = $value")
                    }
                }
            }
            // Clean up the temp source file
            sourceTempFile.delete()
            log(context, "Tổng số thuộc tính sẽ sao chép: ${sourceAttributes.size}")

            // 2. Perform adjustments (randomization/fixed values) on the attributes
            if (settings != null) {
                // Populate default Make and Model if missing in the source to ensure Google Photos shows the panel
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
            val targetMimeType = context.contentResolver.getType(targetUri) ?: "image/jpeg"
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
                "JPG" -> "image/jpeg" to ".JPG"
                "JPEG" -> "image/jpeg" to ".JPEG"
                "PNG" -> "image/png" to ".PNG"
                else -> targetMimeType to targetExt.uppercase(Locale.US)
            }
            log(context, "Target MIME type: $targetMimeType ($targetExt) -> Output MIME type: $outputMimeType ($tempExt)")
            
            // Create a local temp file with matching extension
            val tempFile = File.createTempFile("exif_target_temp", tempExt, context.cacheDir)
            val needsConversion = if (finalFormatSetting == "ORIGINAL") {
                false
            } else {
                when {
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
                            val compressFormat = if (outputMimeType == "image/png") {
                                android.graphics.Bitmap.CompressFormat.PNG
                            } else {
                                android.graphics.Bitmap.CompressFormat.JPEG
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
                context.contentResolver.openInputStream(targetUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                log(context, "Sao chép file gốc sang temp thành công.")
            }
            log(context, "Tạo temp file thành công: ${tempFile.absolutePath}")

            // Write properties to the temp file
            val targetExif = ExifInterface(tempFile.absolutePath)
            for ((tag, value) in sourceAttributes) {
                targetExif.setAttribute(tag, value)
            }
            targetExif.saveAttributes()
            log(context, "Đã ghi attributes vào temp file và saveAttributes() thành công.")

            // Verify written attributes in the temp file
            val verifyExif = ExifInterface(tempFile.absolutePath)
            log(context, "Xác minh các thuộc tính đã ghi trong temp file:")
            for ((tag, expected) in sourceAttributes) {
                val actual = verifyExif.getAttribute(tag)
                log(context, "  Tag $tag -> Thực tế: $actual, Kỳ vọng: $expected")
            }

            // 4. Save temp file back to its final destination
            if (replaceOriginal && !needsConversion) {
                try {
                    context.contentResolver.openOutputStream(targetUri, "rwt")?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    tempFile.delete()
                    log(context, "Đã ghi đè file gốc thành công.")
                    
                    // Force rename/update DISPLAY_NAME of the original target file to uppercase extension if needed
                    val originalName = getFileName(context, targetUri)
                    val targetNameWithUpper = if (originalName != null) {
                        val baseName = getBaseName(originalName)
                        "$baseName$tempExt"
                    } else null

                    if (targetNameWithUpper != null && originalName != targetNameWithUpper) {
                        try {
                            val updateValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, targetNameWithUpper)
                            }
                            context.contentResolver.update(targetUri, updateValues, null, null)
                            log(context, "Cập nhật display name trước khi quét: $targetNameWithUpper")
                        } catch (ue: Exception) {
                            log(context, "Lỗi cập nhật display name trước khi quét: ${ue.message}")
                        }
                    }

                    // Force scan the overwritten file to update system Gallery
                    forceScanFile(context, targetUri, targetMimeType, targetNameWithUpper)
                    
                    return targetUri
                } catch (e: SecurityException) {
                    log(context, "Lỗi ghi đè file gốc (Permission Denied). Sẽ lưu bản copy.")
                    // Fall back to copy
                }
            }

            // Save to public storage (Pictures/ExifCopy)
            val baseName = getBaseName(rawFileName)
            val fileName = getUniqueFileName(context, baseName, tempExt)
            log(context, "Tên file đích cuối cùng: $fileName")
            val savedUri = saveToPublicPictures(context, tempFile, fileName, outputMimeType)
            log(context, "Đã lưu bản sao vào Pictures/ExifCopy. Output URI: $savedUri")
            tempFile.delete()
            return savedUri

        } catch (e: Exception) {
            val fullError = "LỖI TRONG copyMetadata: ${e.message}\n${Log.getStackTraceString(e)}"
            log(context, fullError)
            return null
        }
    }

    fun cleanGoogleAiMetadata(context: Context, targetUri: Uri, replaceOriginal: Boolean): Uri? {
        try {
            log(context, "--- BẮT ĐẦU XÓA NHÃN AI ---")
            log(context, "Target URI: $targetUri")
            log(context, "Replace Original: $replaceOriginal")

            val targetMimeType = context.contentResolver.getType(targetUri) ?: "image/jpeg"
            val tempExt = when {
                targetMimeType.contains("png", ignoreCase = true) -> ".PNG"
                targetMimeType.contains("webp", ignoreCase = true) -> ".WEBP"
                targetMimeType.contains("heic", ignoreCase = true) || targetMimeType.contains("heif", ignoreCase = true) -> ".HEIC"
                else -> ".JPG"
            }
            
            val tempFile = File.createTempFile("exif_clean_temp", tempExt, context.cacheDir)
            context.contentResolver.openInputStream(targetUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val exif = ExifInterface(tempFile.absolutePath)
            val xmp = exif.getAttribute(ExifInterface.TAG_XMP)
            if (xmp != null) {
                // Strip Google AI specific XMP tags using Regex
                var cleanedXmp = xmp
                
                // Remove Credit
                val creditRegex = """<photoshop:Credit>[^<]*</photoshop:Credit>""".toRegex()
                cleanedXmp = cleanedXmp.replace(creditRegex, "")

                // Remove DigitalSourceType
                val dstRegex = """<Iptc4xmpExt:DigitalSourceType>[^<]*</Iptc4xmpExt:DigitalSourceType>""".toRegex()
                cleanedXmp = cleanedXmp.replace(dstRegex, "")

                // Remove HasExtendedXMP if it refers to Google AI properties
                val hasExtendedXmpRegex = """<xmpNote:HasExtendedXMP>[^<]*</xmpNote:HasExtendedXMP>""".toRegex()
                cleanedXmp = cleanedXmp.replace(hasExtendedXmpRegex, "")

                exif.setAttribute(ExifInterface.TAG_XMP, cleanedXmp)
                log(context, "Đã làm sạch thẻ XMP AI.")
            }

            // Check Software/UserComment for "Google" or "AI"
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

            exif.saveAttributes()
            log(context, "Đã lưu attributes đã làm sạch vào temp file.")

            if (replaceOriginal) {
                try {
                    context.contentResolver.openOutputStream(targetUri, "rwt")?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    tempFile.delete()
                    log(context, "Đã ghi đè file gốc làm sạch thành công.")

                    // Force rename/update DISPLAY_NAME of the original target file to uppercase extension if needed
                    val originalName = getFileName(context, targetUri)
                    val targetNameWithUpper = if (originalName != null) {
                        val baseName = getBaseName(originalName)
                        "$baseName$tempExt"
                    } else null

                    if (targetNameWithUpper != null && originalName != targetNameWithUpper) {
                        try {
                            val updateValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, targetNameWithUpper)
                            }
                            context.contentResolver.update(targetUri, updateValues, null, null)
                            log(context, "Cập nhật display name trước khi quét: $targetNameWithUpper")
                        } catch (ue: Exception) {
                            log(context, "Lỗi cập nhật display name trước khi quét: ${ue.message}")
                        }
                    }

                    forceScanFile(context, targetUri, targetMimeType, targetNameWithUpper)
                    return targetUri
                } catch (e: SecurityException) {
                    log(context, "Lỗi ghi đè file gốc làm sạch (Permission Denied). Sẽ lưu bản copy.")
                }
            }

            val rawFileName = getFileName(context, targetUri) ?: "clean_${System.currentTimeMillis()}"
            val baseName = "clean_" + getBaseName(rawFileName)
            val fileName = getUniqueFileName(context, baseName, tempExt)
            val savedUri = saveToPublicPictures(context, tempFile, fileName, targetMimeType)
            tempFile.delete()
            return savedUri

        } catch (e: Exception) {
            val fullError = "LỖI TRONG cleanGoogleAiMetadata: ${e.message}\n${Log.getStackTraceString(e)}"
            log(context, fullError)
            return null
        }
    }

    private fun saveToPublicPictures(context: Context, sourceFile: File, fileName: String, mimeType: String): Uri? {
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
                } else {
                    val updateValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    }
                    resolver.update(imageUri, updateValues, null, null)
                }
                
                // Force scan the new file with uppercase target filename mapping
                forceScanFile(context, imageUri, mimeType, fileName)
                
                return imageUri
            } catch (e: Exception) {
                val errorMsg = "Lỗi copy file vào MediaStore URI $imageUri: ${e.message}\n${Log.getStackTraceString(e)}"
                log(context, errorMsg)
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
