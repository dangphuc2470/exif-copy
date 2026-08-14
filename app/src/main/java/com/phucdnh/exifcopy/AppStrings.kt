package com.phucdnh.exifcopy

import java.util.Locale

enum class AppLanguage(val code: String, val displayNameVi: String, val displayNameEn: String) {
    SYSTEM("system", "Mặc định hệ thống", "System default"),
    VIETNAMESE("vi", "Tiếng Việt", "Vietnamese"),
    ENGLISH("en", "English", "English");

    fun getDisplayName(isVi: Boolean): String = if (isVi) displayNameVi else displayNameEn
}

object Strings {
    fun isVietnamese(languageCode: String): Boolean {
        return when (languageCode) {
            "vi" -> true
            "en" -> false
            else -> Locale.getDefault().language.lowercase().startsWith("vi")
        }
    }

    // Tabs
    fun tabCopyExif(isVi: Boolean) = if (isVi) "Sao chép EXIF" else "Copy EXIF"
    fun tabRemoveAi(isVi: Boolean) = if (isVi) "Xóa nhãn AI" else "Remove AI label"
    fun tabLogs(isVi: Boolean) = if (isVi) "Nhật ký" else "Logs"
    fun tabSettings(isVi: Boolean) = if (isVi) "Cài đặt" else "Settings"

    // Container Titles & Buttons
    fun sourceImagesTitle(isVi: Boolean) = if (isVi) "Ảnh nguồn (A)" else "Source images (A)"
    fun targetImagesTitle(isVi: Boolean) = if (isVi) "Ảnh đích (B)" else "Target images (B)"
    fun targetImagesToProcessTitle(isVi: Boolean) = if (isVi) "Ảnh đích" else "Target images"
    fun selectImages(isVi: Boolean) = if (isVi) "Chọn ảnh" else "Select images"
    fun recentImages(isVi: Boolean, count: Int) = if (isVi) "Ảnh gần đây ($count)" else "Recent images ($count)"
    fun tapToAddQuickly(isVi: Boolean) = if (isVi) "Bấm để thêm nhanh" else "Tap to add quickly"
    fun clearRecent(isVi: Boolean) = if (isVi) "Xóa gần đây" else "Clear recent"
    fun emptySourceMsg(isVi: Boolean) = if (isVi) "Chưa chọn ảnh nguồn.\nẢnh dùng để lấy thông tin EXIF." else "No source images selected.\nUsed to extract EXIF metadata."
    fun emptyTargetMsg(isVi: Boolean) = if (isVi) "Chưa chọn ảnh đích.\nẢnh sẽ được gán EXIF mới." else "No target images selected.\nWill receive new EXIF metadata."
    fun emptyAiTargetMsg(isVi: Boolean) = if (isVi) "Chưa chọn ảnh đích.\nChọn ảnh cần xóa nhãn Google AI & watermark." else "No target images selected.\nSelect images to remove Google AI labels & watermarks."
    fun resultImagesTitle(isVi: Boolean, count: Int) = if (isVi) "Ảnh kết quả ($count)" else "Result images ($count)"
    fun clearResults(isVi: Boolean) = if (isVi) "Xóa kết quả" else "Clear results"

    // Controls
    fun swap(isVi: Boolean) = if (isVi) "Hoán đổi" else "Swap"
    fun exifSettingsBtn(isVi: Boolean) = if (isVi) "Cài đặt EXIF" else "EXIF settings"
    fun removeWatermarkCheckbox(isVi: Boolean) = if (isVi) "Xóa watermark" else "Remove watermark"
    fun copyExifAction(isVi: Boolean) = if (isVi) "Sao chép EXIF" else "Copy EXIF"
    fun removeAiAction(isVi: Boolean) = if (isVi) "Xóa nhãn AI" else "Remove AI label"

    // Messages & Toasts
    fun selectAtLeastOneSource(isVi: Boolean) = if (isVi) "Chọn ít nhất 1 ảnh nguồn!" else "Select at least 1 source image!"
    fun selectAtLeastOneTarget(isVi: Boolean) = if (isVi) "Chọn ít nhất 1 ảnh đích!" else "Select at least 1 target image!"
    fun selectAiTarget(isVi: Boolean) = if (isVi) "Chọn ảnh ở phần ảnh đích để xóa nhãn AI!" else "Select images in target section to remove AI labels!"
    fun processingCopyExif(isVi: Boolean) = if (isVi) "Đang sao chép EXIF..." else "Copying EXIF..."
    fun processingRemoveAiAndWatermark(isVi: Boolean) = if (isVi) "Đang xóa nhãn AI & watermark..." else "Removing AI labels & watermark..."
    fun processingRemoveAiOnly(isVi: Boolean) = if (isVi) "Đang xóa nhãn Google AI..." else "Removing Google AI labels..."
    fun addedToSource(isVi: Boolean) = if (isVi) "Đã thêm vào ảnh nguồn" else "Added to source images"
    fun alreadyInSource(isVi: Boolean) = if (isVi) "Ảnh này đã có trong danh sách nguồn" else "Image already in source list"
    fun copiedToClipboard(isVi: Boolean) = if (isVi) "Đã sao chép vào bộ nhớ tạm!" else "Copied to clipboard!"
    fun logsCleared(isVi: Boolean) = if (isVi) "Đã xóa nhật ký!" else "Logs cleared!"
    fun updatedSettings(isVi: Boolean) = if (isVi) "Đã cập nhật cấu hình random EXIF!" else "Updated random EXIF settings!"
    fun savedResultToast(isVi: Boolean, count: Int, total: Int) = if (isVi) "Đã lưu $count/$total ảnh vào thư mục Pictures/EXIFCopy!" else "Saved $count/$total images to Pictures/EXIFCopy!"

    // Logs Tab
    fun logsTitle(isVi: Boolean) = if (isVi) "Nhật ký hoạt động (Log)" else "Activity logs"
    fun logsLoading(isVi: Boolean) = if (isVi) "Đang tải nhật ký..." else "Loading logs..."
    fun logsEmpty(isVi: Boolean) = if (isVi) "Chưa có nhật ký nào." else "No logs yet."

    // Settings Tab
    fun settingsTabTitle(isVi: Boolean) = if (isVi) "Cài đặt ứng dụng" else "App settings"
    fun languageSectionTitle(isVi: Boolean) = if (isVi) "Ngôn ngữ giao diện" else "Interface language"
    fun languageSectionDesc(isVi: Boolean) = if (isVi) "Chọn ngôn ngữ hiển thị trong ứng dụng" else "Choose the display language for the app"
    fun watermarkModeSectionTitle(isVi: Boolean) = if (isVi) "Phương thức xóa watermark" else "Watermark removal mode"
    fun watermarkModeSectionDesc(isVi: Boolean) = if (isVi) "Chọn thuật toán mặc định khi xóa watermark Google Gemini" else "Select default algorithm for Google Gemini watermark removal"
    fun exifConfigSectionTitle(isVi: Boolean) = if (isVi) "Cấu hình sao chép & random EXIF" else "EXIF copy & randomization settings"
    fun exifConfigSectionDesc(isVi: Boolean) = if (isVi) "Tùy chỉnh thông số máy ảnh, khẩu độ, tốc độ màn trập, GPS và thời gian" else "Customize camera info, aperture, shutter speed, GPS and time offsets"
    fun openExifConfigBtn(isVi: Boolean) = if (isVi) "Mở cấu hình EXIF" else "Open EXIF configuration"
    fun aboutAppTitle(isVi: Boolean) = if (isVi) "Thông tin ứng dụng" else "About app"
    fun appVersion(isVi: Boolean) = if (isVi) "Phiên bản: 1.0.0" else "Version: 1.0.0"

    // Dialogs
    fun completionDialogTitle(isVi: Boolean) = if (isVi) "Xử lý hoàn tất!" else "Process completed!"
    fun completionDialogMsg(isVi: Boolean) = if (isVi) "Đã lưu ảnh mới vào thư mục Pictures/EXIFCopy!" else "Saved new images to Pictures/EXIFCopy!"
    fun tapToZoom(isVi: Boolean) = if (isVi) "Chạm để phóng to" else "Tap to zoom"
    fun close(isVi: Boolean) = if (isVi) "Đóng" else "Close"
    fun externalShareTitle(isVi: Boolean) = if (isVi) "Nhập ảnh từ bên ngoài" else "Import external images"
    fun externalShareMsg(isVi: Boolean, count: Int) = if (isVi) "Bạn muốn thêm $count ảnh đã chia sẻ vào danh sách nào?" else "Which list do you want to add $count shared images to?"
    fun skip(isVi: Boolean) = if (isVi) "Bỏ qua" else "Skip"
}
