$PackageName = "com.phucdnh.exifcopy"
$MainActivity = "com.phucdnh.exifcopy/.MainActivity"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "BUILDING AND INSTALLING ANDROID APP..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

.\gradlew.bat installDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed" -ForegroundColor Red
    exit $LASTEXITCODE
}

adb logcat -c
Write-Host "Launching app..." -ForegroundColor Green
adb shell am start -n $MainActivity

Start-Sleep -Milliseconds 800

$appPid = (adb shell pidof -s $PackageName).ToString().Trim()
if ($appPid.Length -gt 0) {
    Write-Host "PID: $appPid" -ForegroundColor Yellow
    adb.exe logcat --pid $appPid -v color
}
