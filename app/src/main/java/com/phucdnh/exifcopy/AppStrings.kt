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
    fun tabUpscaleBlend(isVi: Boolean) = if (isVi) "Ghép & Phóng to" else "Upscale & Blend"
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

    // Upscale & Blend Tab Strings
    fun upscaleOriginalTitle(isVi: Boolean) = if (isVi) "1. Ảnh gốc High-Res (A)" else "1. High-Res Original (A)"
    fun upscaleEditedTitle(isVi: Boolean) = if (isVi) "2. Ảnh AI Edit Low-Res (B)" else "2. Low-Res AI Edited (B)"
    fun emptyUpscaleOriginalMsg(isVi: Boolean) = if (isVi) "Chưa chọn ảnh gốc.\nẢnh dùng để lấy độ phân giải và chi tiết nét." else "No original image selected.\nProvides resolution & sharp pixels."
    fun emptyUpscaleEditedMsg(isVi: Boolean) = if (isVi) "Chưa chọn ảnh AI edit.\nẢnh chứa nội dung bạn đã chỉnh sửa." else "No AI edited image selected.\nContains your AI modifications."
    fun upscaleBlendAction(isVi: Boolean) = if (isVi) "Bắt đầu Phóng to & Hòa trộn" else "Start Upscale & Blend"
    fun diffThresholdSlider(isVi: Boolean, value: Int) = if (isVi) "Độ nhạy nhận diện (Threshold: $value)" else "Detection Sensitivity (Threshold: $value)"
    fun featherSigmaSlider(isVi: Boolean, value: Int) = if (isVi) "Độ mềm đường viền (Feather: ${value}px)" else "Edge Softness (Feather: ${value}px)"
    fun upscaleBlendSettingsTitle(isVi: Boolean) = if (isVi) "Tùy chỉnh thuật toán hòa trộn" else "Blending Algorithm Settings"
    fun realtimeMaskPreviewTitle(isVi: Boolean) = if (isVi) "Xem trước mặt nạ (Realtime)" else "Realtime Mask Preview"
    fun selectBothImagesToPreview(isVi: Boolean) = if (isVi) "Chọn cả 2 ảnh phía trên để xem trước mặt nạ trực tiếp" else "Select both images above to see live mask preview"
    fun diffPixelsDetectedShort(isVi: Boolean, count: Int, percent: Float) = if (isVi) "$count pixel vùng đỏ (${String.format(java.util.Locale.US, "%.1f", percent)}%)" else "$count red pixels (${String.format(java.util.Locale.US, "%.1f", percent)}%)"


    // Visual Loading Dialog
    fun visualLoadingTitle(isVi: Boolean) = if (isVi) "Đang xử lý Upscale & Blend" else "Processing Upscale & Blend"
    fun maskPreviewHeader(isVi: Boolean) = if (isVi) "Mặt nạ vùng AI thay đổi (Vùng đỏ):" else "Detected AI Modification Mask (Red Zone):"
    fun diffPixelsDetected(isVi: Boolean, count: Int, percent: Float) = if (isVi) "Phát hiện $count pixel đã sửa (${String.format(java.util.Locale.US, "%.1f", percent)}%)" else "Detected $count modified pixels (${String.format(java.util.Locale.US, "%.1f", percent)}%)"

    // Controls
    fun swap(isVi: Boolean) = if (isVi) "Hoán đổi" else "Swap"
    fun exifSettingsBtn(isVi: Boolean) = if (isVi) "Cài đặt EXIF" else "EXIF settings"
    fun removeWatermarkCheckbox(isVi: Boolean) = if (isVi) "Xóa watermark" else "Remove watermark"
    fun copyExifAction(isVi: Boolean) = if (isVi) "Sao chép EXIF" else "Copy EXIF"
    fun removeAiAction(isVi: Boolean) = if (isVi) "Xóa nhãn AI" else "Remove AI label"
    fun upscaleBlendCheckbox(isVi: Boolean) = if (isVi) "Upscale & Blend" else "Upscale & Blend"
    fun processingUpscaleBlend(isVi: Boolean) = if (isVi) "Đang upscale & blend ảnh..." else "Upscaling & blending images..."

    // Messages & Toasts
    fun selectAtLeastOneSource(isVi: Boolean) = if (isVi) "Chọn ít nhất 1 ảnh nguồn!" else "Select at least 1 source image!"
    fun selectAtLeastOneTarget(isVi: Boolean) = if (isVi) "Chọn ít nhất 1 ảnh đích!" else "Select at least 1 target image!"
    fun selectAiTarget(isVi: Boolean) = if (isVi) "Chọn ảnh ở phần ảnh đích để xóa nhãn AI!" else "Select images in target section to remove AI labels!"
    fun selectBothOriginalAndEdited(isVi: Boolean) = if (isVi) "Vui lòng chọn cả ảnh gốc (A) và ảnh AI edit (B)!" else "Please select both original (A) and edited (B) images!"
    fun processingCopyExif(isVi: Boolean) = if (isVi) "Đang sao chép EXIF..." else "Copying EXIF..."
    fun processingRemoveAiAndWatermark(isVi: Boolean) = if (isVi) "Đang xóa nhãn AI & watermark..." else "Removing AI labels & watermark..."
    fun processingRemoveAiOnly(isVi: Boolean) = if (isVi) "Đang xóa nhãn Google AI..." else "Removing Google AI labels..."
    fun addedToSource(isVi: Boolean) = if (isVi) "Đã thêm vào ảnh nguồn" else "Added to source images"
    fun alreadyInSource(isVi: Boolean) = if (isVi) "Ảnh này đã có trong danh sách nguồn" else "Image already in source list"
    fun copiedToClipboard(isVi: Boolean) = if (isVi) "Đã sao chép vào bộ nhớ tạm!" else "Copied to clipboard!"
    fun logsCleared(isVi: Boolean) = if (isVi) "Đã xóa nhật ký!" else "Logs cleared!"
    fun updatedSettings(isVi: Boolean) = if (isVi) "Đã cập nhật cấu hình random EXIF!" else "Updated random EXIF settings!"
    fun savedResultToast(isVi: Boolean, count: Int, total: Int) = if (isVi) "Đã lưu $count/$total ảnh vào thư mục Pictures/EXIFCopy!" else "Saved $count/$total images to Pictures/EXIFCopy!"
    fun blendSuccessToast(isVi: Boolean) = if (isVi) "Đã phóng to và ghép ảnh thành công vào Pictures/EXIFCopy!" else "Successfully upscaled and blended image to Pictures/EXIFCopy!"

    // Logs Tab
    fun logsTitle(isVi: Boolean) = if (isVi) "Nhật ký hoạt động (Log)" else "Activity logs"
    fun logsLoading(isVi: Boolean) = if (isVi) "Đang tải nhật ký..." else "Loading logs..."
    fun logsEmpty(isVi: Boolean) = if (isVi) "Chưa có nhật ký nào." else "No logs yet."

    // Settings Tab
    fun settingsTabTitle(isVi: Boolean) = if (isVi) "Cài đặt ứng dụng" else "App settings"
    fun languageSectionTitle(isVi: Boolean) = if (isVi) "Ngôn ngữ giao diện" else "Interface language"
    fun languageSectionDesc(isVi: Boolean) = if (isVi) "Chọn ngôn ngữ hiển thị trong ứng dụng" else "Choose the display language for the app"
    fun watermarkModeSectionTitle(isVi: Boolean) = if (isVi) "Phương thức xóa watermark mặc định" else "Default watermark removal mode"
    fun watermarkModeSectionDesc(isVi: Boolean) = if (isVi) "Chọn thuật toán mặc định khi khởi động ứng dụng" else "Select default algorithm when opening the app"
    fun reverseAlphaVersionsTitle(isVi: Boolean) = if (isVi) "Phiên bản Reverse Alpha theo mốc thời gian" else "Reverse Alpha Timeline Versions"
    fun reverseAlphaVersionsDesc(isVi: Boolean) = if (isVi) "Bật/tắt các phiên bản Reverse Alpha và xem trước watermark tương ứng trên nền đen" else "Toggle Reverse Alpha versions and preview corresponding watermarks on black background"
    fun watermarkDropdownVisibilityTitle(isVi: Boolean) = if (isVi) "Tùy chọn hiển thị ở Dropdown bên ngoài" else "Dropdown Visibility Options"
    fun watermarkDropdownVisibilityDesc(isVi: Boolean) = if (isVi) "Đánh dấu chọn (tick) các chế độ/phiên bản m muốn xuất hiện ở Dropdown chọn chế độ ngoài màn hình chính" else "Check the modes/versions you want to appear in the main screen dropdowns"
    fun previewWatermark(isVi: Boolean) = if (isVi) "Xem trước" else "Preview"
    fun previewWatermarkDialogTitle(isVi: Boolean) = if (isVi) "Xem trước Watermark mẫu" else "Sample Watermark Preview"
    fun watermarkPreviewDetail(isVi: Boolean, dateRange: String) = if (isVi) "Mẫu watermark nền đen cho mốc thời gian: $dateRange" else "Black background watermark sample for: $dateRange"
    fun showInDropdownCheckbox(isVi: Boolean) = if (isVi) "Hiện ở Dropdown" else "Show in Dropdown"
    fun autoPrecomputeWatermarkTitle(isVi: Boolean) = if (isVi) "Tự động xử lý trước xem trước Watermark" else "Auto Pre-compute Watermark Previews"
    fun autoPrecomputeWatermarkDesc(isVi: Boolean) = if (isVi) "Tự động chạy ngầm các phương án xóa watermark khi chọn ảnh đích để xem trước tức thì trên Dropdown. Tắt đi nếu muốn tiết kiệm pin và tối ưu hiệu năng." else "Automatically run watermark removal algorithms in background when target image is selected for instant dropdown preview. Turn off to save battery and reduce lag on low-end devices."
    fun watermarkOutputOptionsTitle(isVi: Boolean) = if (isVi) "Tùy chọn file khi xóa Watermark" else "Watermark Output Options"
    fun keepOriginalFileNameTitle(isVi: Boolean) = if (isVi) "Giữ nguyên tên file gốc (Keep name)" else "Keep original filename"
    fun keepOriginalFileNameDesc(isVi: Boolean) = if (isVi) "Lưu ảnh với đúng tên file gốc ban đầu thay vì thêm tiền tố clean_ (mặc định tắt)" else "Save image with original filename without clean_ prefix (default off)"
    fun keepOriginalDateTimeTitle(isVi: Boolean) = if (isVi) "Giữ nguyên ngày giờ chụp gốc (Keep date & time)" else "Keep original date & time"
    fun keepOriginalDateTimeDesc(isVi: Boolean) = if (isVi) "Bảo lưu chính xác thời gian chụp và ngày tạo EXIF ban đầu của ảnh (mặc định tắt)" else "Preserve exact original capture time and EXIF creation date (default off)"
    fun reverseAlphaAutoDetectTitle(isVi: Boolean) = if (isVi) "Tự động nhận diện vị trí cho Reverse Alpha" else "Auto-detect position for Reverse Alpha"
    fun reverseAlphaAutoDetectDesc(isVi: Boolean) = if (isVi) "Mặc định TẮT để Reverse Alpha áp dụng tọa độ chuẩn của Google Gemini siêu tốc tức thì (0ms). Bật nếu ảnh đã bị crop/lệch lề." else "Default OFF for instantaneous (0ms) mathematical removal using Google Gemini standard coordinates. Turn ON if your image has been cropped or re-aligned."
    fun watermarkAutoDetectTitle(isVi: Boolean) = if (isVi) "Tự động nhận diện vị trí Watermark" else "Auto-detect Watermark Position"
    fun watermarkAutoDetectDesc(isVi: Boolean) = if (isVi) "Mặc định BẬT để ứng dụng tự động xác định vị trí watermark và ẩn nút tinh chỉnh ở màn hình chính. Tắt đi nếu muốn hiện nút tinh chỉnh tọa độ thủ công." else "Default ON to auto-detect watermark position and hide the manual adjustment button on the main screen. Turn OFF to show the manual adjustment button."
    fun previewSlideTip(isVi: Boolean) = if (isVi) "Nhấn giữ và trượt ngón tay để so sánh trực tiếp" else "Press and slide finger to compare modes live"
    fun manualAdjustmentTitle(isVi: Boolean) = if (isVi) "Tinh chỉnh vị trí & kích cỡ hộp xóa" else "Watermark Region Manual Adjustment"
    fun manualAdjustmentDesc(isVi: Boolean) = if (isVi) "Tùy chỉnh tọa độ X, Y và kích thước hộp watermark cho các chế độ Inpainting/AI Model" else "Adjust X/Y offsets and watermark box size for Inpainting/AI Model modes"
    fun manualAdjustmentNote(isVi: Boolean) = if (isVi) "⚠️ Tính năng này chỉ áp dụng cho Inpainting/AI Model (không áp dụng cho Reverse Alpha vì RA dùng tọa độ cố định)." else "⚠️ Only applies to Inpainting/AI Model modes (not applicable to Reverse Alpha which uses fixed profiles)."
    fun manualAdjustmentAutoDetect(isVi: Boolean) = if (isVi) "Tự động nhận diện vị trí (Tắt chỉnh thủ công)" else "Auto-detect position (Disable manual adjustment)"
    fun manualAdjustmentPreviewTitle(isVi: Boolean) = if (isVi) "Vùng hộp xóa trên ảnh gốc (Khung đỏ mờ):" else "Watermark box on original image (Red mask):"
    fun offsetXLabel(isVi: Boolean, value: Int) = if (isVi) "Dịch chuyển X (Ngang): ${if (value > 0) "+$value" else "$value"} px" else "Offset X: ${if (value > 0) "+$value" else "$value"} px"
    fun offsetYLabel(isVi: Boolean, value: Int) = if (isVi) "Dịch chuyển Y (Dọc): ${if (value > 0) "+$value" else "$value"} px" else "Offset Y: ${if (value > 0) "+$value" else "$value"} px"
    fun boxSizeLabel(isVi: Boolean, size: Int) = if (isVi) "Kích cỡ hộp xóa: ${size}x${size} px" else "Box Size: ${size}x${size} px"
    fun saveCustomPosBtn(isVi: Boolean) = if (isVi) "Lưu vị trí này" else "Save position"
    fun resetCustomPosBtn(isVi: Boolean) = "Reset"
    fun savedCustomPosToast(isVi: Boolean) = if (isVi) "Đã lưu vị trí và kích thước tùy chỉnh!" else "Saved custom position and size!"
    fun resetCustomPosToast(isVi: Boolean) = if (isVi) "Đã reset về vị trí nhận diện tự động (Manual Mode)!" else "Reset to auto-detect position (Manual Mode)!"
    fun previewComputing(isVi: Boolean) = if (isVi) "Đang tính toán preview..." else "Computing preview..."
    fun previewNotReady(isVi: Boolean) = if (isVi) "Chưa có ảnh preview" else "Preview not available"
    fun exifConfigSectionTitle(isVi: Boolean) = if (isVi) "Cấu hình sao chép & random EXIF" else "EXIF copy & randomization settings"
    fun exifConfigSectionDesc(isVi: Boolean) = if (isVi) "Tùy chỉnh thông số máy ảnh, khẩu độ, tốc độ màn trập, GPS và thời gian" else "Customize camera info, aperture, shutter speed, GPS and time offsets"
    fun openExifConfigBtn(isVi: Boolean) = if (isVi) "Mở cấu hình EXIF" else "EXIF configuration"
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
