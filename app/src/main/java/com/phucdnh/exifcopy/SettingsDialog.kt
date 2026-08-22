package com.phucdnh.exifcopy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentSettings: ExifSettings,
    onDismiss: () -> Unit,
    onSave: (ExifSettings) -> Unit,
    isVi: Boolean = true
) {
    var randomizeShutter by remember { mutableStateOf(currentSettings.randomizeShutter) }
    var shutterMinOffsetPct by remember { mutableStateOf(currentSettings.shutterMinOffsetPct) }
    var shutterMaxOffsetPct by remember { mutableStateOf(currentSettings.shutterMaxOffsetPct) }
    var shutterFixedValue by remember { mutableStateOf(currentSettings.shutterFixedValue ?: "") }

    var randomizeAperture by remember { mutableStateOf(currentSettings.randomizeAperture) }
    var apertureMinOffset by remember { mutableStateOf(currentSettings.apertureMinOffset) }
    var apertureMaxOffset by remember { mutableStateOf(currentSettings.apertureMaxOffset) }
    var apertureFixedValue by remember { mutableStateOf(currentSettings.apertureFixedValue ?: "") }

    var randomizeFocalLength by remember { mutableStateOf(currentSettings.randomizeFocalLength) }
    var focalMinOffset by remember { mutableStateOf(currentSettings.focalMinOffset) }
    var focalMaxOffset by remember { mutableStateOf(currentSettings.focalMaxOffset) }
    var focalFixedValue by remember { mutableStateOf(currentSettings.focalFixedValue ?: "") }

    var randomizeIso by remember { mutableStateOf(currentSettings.randomizeIso) }
    var isoMin by remember { mutableStateOf(currentSettings.isoMin) }
    var isoMax by remember { mutableStateOf(currentSettings.isoMax) }
    var isoFixedValue by remember { mutableStateOf(currentSettings.isoFixedValue ?: "") }

    var randomizeTime by remember { mutableStateOf(currentSettings.randomizeTime) }
    var timeMinSecs by remember { mutableStateOf(currentSettings.timeMinSecs) }
    var timeMaxSecs by remember { mutableStateOf(currentSettings.timeMaxSecs) }
    var baseTimeAddSecs by remember { mutableStateOf(currentSettings.baseTimeAddSecs) }

    var copyCameraInfo by remember { mutableStateOf(currentSettings.copyCameraInfo) }
    var copyShootingInfo by remember { mutableStateOf(currentSettings.copyShootingInfo) }
    var copyGpsInfo by remember { mutableStateOf(currentSettings.copyGpsInfo) }
    var copyDateTaken by remember { mutableStateOf(currentSettings.copyDateTaken) }
    var copyCreatedDate by remember { mutableStateOf(currentSettings.copyCreatedDate) }
    var copyFileName by remember { mutableStateOf(currentSettings.copyFileName) }
    var copyXmpInfo by remember { mutableStateOf(currentSettings.copyXmpInfo) }
    var outputFormat by remember { mutableStateOf(currentSettings.outputFormat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isVi) "Cấu hình sao chép & random EXIF" else "EXIF Copy & Randomization Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Metadata Fields to Copy
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isVi) "Thông tin sao chép" else "Copied Metadata", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyCameraInfo, onCheckedChange = { copyCameraInfo = it })
                            Text(if (isVi) "Thông tin máy ảnh & ống kính" else "Camera & lens information")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyShootingInfo, onCheckedChange = { copyShootingInfo = it })
                            Text(if (isVi) "Thông số chụp (ISO, khẩu độ, tốc độ...)" else "Exposure settings (ISO, aperture, shutter...)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyGpsInfo, onCheckedChange = { copyGpsInfo = it })
                            Text(if (isVi) "Vị trí địa lý (GPS)" else "Geographic location (GPS)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyDateTaken, onCheckedChange = { copyDateTaken = it })
                            Text(if (isVi) "Ngày chụp (Date taken)" else "Date taken")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyCreatedDate, onCheckedChange = { copyCreatedDate = it })
                            Text(if (isVi) "Ngày tạo file (Created date)" else "File creation date")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyFileName, onCheckedChange = { copyFileName = it })
                            Text(if (isVi) "Sao chép tên file nguồn" else "Copy source filename")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyXmpInfo, onCheckedChange = { copyXmpInfo = it })
                            Text(if (isVi) "Thông tin XMP & khác" else "XMP & other metadata")
                        }
                    }
                }

                // Section: Output Format (Định dạng đầu ra)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isVi) "Định dạng ảnh đầu ra (Output format)" else "Output Image Format", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val formats = listOf("ORIGINAL", "JPG", "JPEG", "PNG", "WEBP_LOSSY", "WEBP_LOSSLESS")
                        Column {
                            formats.forEach { format ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { outputFormat = format }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = outputFormat == format,
                                        onClick = { outputFormat = format }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val displayName = when (format) {
                                        "ORIGINAL" -> if (isVi) "Giữ nguyên định dạng gốc" else "Keep original format"
                                        "WEBP_LOSSY" -> if (isVi) "WebP (Lossy - Có hao hụt)" else "WebP (Lossy)"
                                        "WEBP_LOSSLESS" -> if (isVi) "WebP (Lossless - Không hao hụt)" else "WebP (Lossless)"
                                        else -> format
                                    }
                                    Text(displayName)
                                }
                            }
                        }
                    }
                }

                // Section: Shutter Speed
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isVi) "Tốc độ màn trập (Shutter speed)" else "Shutter Speed (Exposure time)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shutterFixedValue,
                            onValueChange = { shutterFixedValue = it },
                            label = { Text(if (isVi) "Giá trị cố định (vd: 1/125, 0.008)" else "Fixed value (e.g. 1/125, 0.008)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = shutterFixedValue.isNotEmpty() || (!randomizeShutter)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = randomizeShutter && shutterFixedValue.isEmpty(),
                                onCheckedChange = {
                                    randomizeShutter = it
                                    if (it) shutterFixedValue = ""
                                },
                                enabled = shutterFixedValue.isEmpty()
                            )
                            Text(if (isVi) "Random theo độ lệch (%)" else "Randomize by offset (%)")
                        }
                        if (randomizeShutter && shutterFixedValue.isEmpty()) {
                            Text(if (isVi) "Khoảng: ${shutterMinOffsetPct.toInt()}% đến ${shutterMaxOffsetPct.toInt()}%" else "Range: ${shutterMinOffsetPct.toInt()}% to ${shutterMaxOffsetPct.toInt()}%")
                            RangeSlider(
                                value = shutterMinOffsetPct.toFloat()..shutterMaxOffsetPct.toFloat(),
                                onValueChange = { range ->
                                    shutterMinOffsetPct = range.start.toDouble()
                                    shutterMaxOffsetPct = range.endInclusive.toDouble()
                                },
                                valueRange = -100f..100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section: Aperture (F-Stop)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isVi) "Khẩu độ (Aperture / F-Stop)" else "Aperture (F-Stop)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apertureFixedValue,
                            onValueChange = { apertureFixedValue = it },
                            label = { Text(if (isVi) "Giá trị cố định (vd: 2.8, 8.0)" else "Fixed value (e.g. 2.8, 8.0)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = apertureFixedValue.isNotEmpty() || (!randomizeAperture)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = randomizeAperture && apertureFixedValue.isEmpty(),
                                onCheckedChange = {
                                    randomizeAperture = it
                                    if (it) apertureFixedValue = ""
                                },
                                enabled = apertureFixedValue.isEmpty()
                            )
                            Text(if (isVi) "Random theo độ lệch f-stop" else "Randomize by offset (f-stop)")
                        }
                        if (randomizeAperture && apertureFixedValue.isEmpty()) {
                            Text(String.format(Locale.US, if (isVi) "Khoảng: %.1f đến %.1f" else "Range: %.1f to %.1f", apertureMinOffset, apertureMaxOffset))
                            RangeSlider(
                                value = apertureMinOffset.toFloat()..apertureMaxOffset.toFloat(),
                                onValueChange = { range ->
                                    apertureMinOffset = range.start.toDouble()
                                    apertureMaxOffset = range.endInclusive.toDouble()
                                },
                                valueRange = -4f..4f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section: Focal Length
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isVi) "Tiêu cự (Focal length)" else "Focal Length", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = focalFixedValue,
                            onValueChange = { focalFixedValue = it },
                            label = { Text(if (isVi) "Giá trị cố định (vd: 50, 24)" else "Fixed value (e.g. 50, 24)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = focalFixedValue.isNotEmpty() || (!randomizeFocalLength)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = randomizeFocalLength && focalFixedValue.isEmpty(),
                                onCheckedChange = {
                                    randomizeFocalLength = it
                                    if (it) focalFixedValue = ""
                                },
                                enabled = focalFixedValue.isEmpty()
                            )
                            Text(if (isVi) "Random theo độ lệch (mm)" else "Randomize by offset (mm)")
                        }
                        if (randomizeFocalLength && focalFixedValue.isEmpty()) {
                            Text(if (isVi) "Khoảng: ${focalMinOffset.toInt()}mm đến ${focalMaxOffset.toInt()}mm" else "Range: ${focalMinOffset.toInt()}mm to ${focalMaxOffset.toInt()}mm")
                            RangeSlider(
                                value = focalMinOffset.toFloat()..focalMaxOffset.toFloat(),
                                onValueChange = { range ->
                                    focalMinOffset = range.start.toDouble()
                                    focalMaxOffset = range.endInclusive.toDouble()
                                },
                                valueRange = -50f..50f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section: ISO (Độ nhạy sáng)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isVi) "Độ nhạy sáng (ISO)" else "ISO Sensitivity", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = isoFixedValue,
                            onValueChange = { isoFixedValue = it },
                            label = { Text(if (isVi) "Giá trị cố định (vd: 100, 400, 800)" else "Fixed value (e.g. 100, 400, 800)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = isoFixedValue.isNotEmpty() || (!randomizeIso)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = randomizeIso && isoFixedValue.isEmpty(),
                                onCheckedChange = {
                                    randomizeIso = it
                                    if (it) isoFixedValue = ""
                                },
                                enabled = isoFixedValue.isEmpty()
                            )
                            Text(if (isVi) "Random theo khoảng ISO" else "Randomize within ISO range")
                        }
                        if (randomizeIso && isoFixedValue.isEmpty()) {
                            Text(if (isVi) "Khoảng ISO: $isoMin đến $isoMax" else "ISO Range: $isoMin to $isoMax")
                            RangeSlider(
                                value = isoMin.toFloat()..isoMax.toFloat(),
                                onValueChange = { range ->
                                    isoMin = range.start.toInt()
                                    isoMax = range.endInclusive.toInt()
                                },
                                valueRange = 50f..6400f,
                                steps = 0,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section: Date Time Sequence
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isVi) "Thời gian chụp (Date & Time)" else "Date & Time (Sequential batch)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = randomizeTime,
                                onCheckedChange = { randomizeTime = it }
                            )
                            Text(if (isVi) "Áp dụng độ lệch thời gian tuần tự" else "Apply sequential date offset")
                        }
                        if (randomizeTime) {
                            OutlinedTextField(
                                value = baseTimeAddSecs.toString(),
                                onValueChange = { baseTimeAddSecs = it.toLongOrNull() ?: 0L },
                                label = { Text(if (isVi) "Thời gian gốc cộng thêm (giây)" else "Base offset addition (seconds)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(if (isVi) "Độ trễ tăng dần giữa các ảnh: ${timeMinSecs}s đến ${timeMaxSecs}s" else "Incremental delay between photos: ${timeMinSecs}s to ${timeMaxSecs}s")
                            RangeSlider(
                                value = timeMinSecs.toFloat()..timeMaxSecs.toFloat(),
                                onValueChange = { range ->
                                    timeMinSecs = range.start.toLong()
                                    timeMaxSecs = range.endInclusive.toLong()
                                },
                                valueRange = 1f..600f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    ExifSettings(
                        randomizeShutter = randomizeShutter,
                        shutterMinOffsetPct = shutterMinOffsetPct,
                        shutterMaxOffsetPct = shutterMaxOffsetPct,
                        shutterFixedValue = shutterFixedValue.ifBlank { null },
                        
                        randomizeAperture = randomizeAperture,
                        apertureMinOffset = apertureMinOffset,
                        apertureMaxOffset = apertureMaxOffset,
                        apertureFixedValue = apertureFixedValue.ifBlank { null },
                        
                        randomizeFocalLength = randomizeFocalLength,
                        focalMinOffset = focalMinOffset,
                        focalMaxOffset = focalMaxOffset,
                        focalFixedValue = focalFixedValue.ifBlank { null },

                        randomizeIso = randomizeIso,
                        isoMin = isoMin,
                        isoMax = isoMax,
                        isoFixedValue = isoFixedValue.ifBlank { null },
                        
                        randomizeTime = randomizeTime,
                        timeMinSecs = timeMinSecs,
                        timeMaxSecs = timeMaxSecs,
                        baseTimeAddSecs = baseTimeAddSecs,

                        copyCameraInfo = copyCameraInfo,
                        copyShootingInfo = copyShootingInfo,
                        copyGpsInfo = copyGpsInfo,
                        copyDateTaken = copyDateTaken,
                        copyCreatedDate = copyCreatedDate,
                        copyFileName = copyFileName,
                        copyXmpInfo = copyXmpInfo,
                        outputFormat = outputFormat
                    )
                )
            }) {
                Text(if (isVi) "Lưu" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isVi) "Hủy" else "Cancel")
            }
        }
    )
}
