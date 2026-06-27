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
    onSave: (ExifSettings) -> Unit
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
        title = { Text("Cấu hình Sao chép & Random EXIF") },
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
                        Text("Thông tin sao chép", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyCameraInfo, onCheckedChange = { copyCameraInfo = it })
                            Text("Thông tin máy ảnh & ống kính")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyShootingInfo, onCheckedChange = { copyShootingInfo = it })
                            Text("Thông số chụp (ISO, Khẩu, Tốc...)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyGpsInfo, onCheckedChange = { copyGpsInfo = it })
                            Text("Vị trí địa lý (GPS)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyDateTaken, onCheckedChange = { copyDateTaken = it })
                            Text("Ngày chụp (Date Taken)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyCreatedDate, onCheckedChange = { copyCreatedDate = it })
                            Text("Ngày tạo file (Created Date)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyFileName, onCheckedChange = { copyFileName = it })
                            Text("Sao chép tên file nguồn")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copyXmpInfo, onCheckedChange = { copyXmpInfo = it })
                            Text("Thông tin XMP & khác")
                        }
                    }
                }

                // Section: Output Format (Định dạng đầu ra)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Định dạng ảnh đầu ra (Output Format)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val formats = listOf("ORIGINAL", "JPG", "JPEG", "PNG")
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
                                        "ORIGINAL" -> "Giữ nguyên định dạng gốc"
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
                        Text("Shutter Speed (Exposure Time)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shutterFixedValue,
                            onValueChange = { shutterFixedValue = it },
                            label = { Text("Fixed value (e.g. 1/125, 0.008)") },
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
                            Text("Randomize by offset (%)")
                        }
                        if (randomizeShutter && shutterFixedValue.isEmpty()) {
                            Text("Range: ${shutterMinOffsetPct.toInt()}% to ${shutterMaxOffsetPct.toInt()}%")
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
                        Text("Aperture (F-Number)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apertureFixedValue,
                            onValueChange = { apertureFixedValue = it },
                            label = { Text("Fixed value (e.g. 2.8, 8.0)") },
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
                            Text("Randomize by offset (f-stop value)")
                        }
                        if (randomizeAperture && apertureFixedValue.isEmpty()) {
                            Text(String.format(Locale.US, "Range: %.1f to %.1f", apertureMinOffset, apertureMaxOffset))
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
                        Text("Focal Length", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = focalFixedValue,
                            onValueChange = { focalFixedValue = it },
                            label = { Text("Fixed value (e.g. 50, 24)") },
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
                            Text("Randomize by offset (mm)")
                        }
                        if (randomizeFocalLength && focalFixedValue.isEmpty()) {
                            Text("Range: ${focalMinOffset.toInt()}mm to ${focalMaxOffset.toInt()}mm")
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

                // Section: Date Time Sequence
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Date/Time (Sequential Batch)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = randomizeTime,
                                onCheckedChange = { randomizeTime = it }
                            )
                            Text("Apply sequential date offset")
                        }
                        if (randomizeTime) {
                            OutlinedTextField(
                                value = baseTimeAddSecs.toString(),
                                onValueChange = { baseTimeAddSecs = it.toLongOrNull() ?: 0L },
                                label = { Text("Base offset addition (seconds)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Incremental delay between photos: ${timeMinSecs}s to ${timeMaxSecs}s")
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
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
