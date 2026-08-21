param (
    [string]$Tag = "",
    [string]$Message = "",
    [switch]$SkipCI
)

# Get latest tag and latest commit message
$latestTag = (git describe --tags --abbrev=0 2>$null)
if (-not $latestTag) {
    $latestTag = "1.0.0"
}

$latestCommitMsg = (git log -1 --pretty=%s 2>$null)
if (-not $latestCommitMsg) {
    $latestCommitMsg = "Update project"
} else {
    $latestCommitMsg = $latestCommitMsg.Trim()
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " ExifCopy - Release & Push Helper Script" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Latest Git Tag: $latestTag" -ForegroundColor Yellow
Write-Host "Latest Commit : $latestCommitMsg" -ForegroundColor Gray

# Check uncommitted changes
$status = git status --porcelain
if ($status) {
    Write-Host "`nThere are uncommitted changes in your workspace:" -ForegroundColor Yellow
    git status -s
    
    if (-not $Message) {
        $inputMsg = Read-Host "`nEnter commit message (Press Enter to use: '$latestCommitMsg')"
        if (-not [string]::IsNullOrWhiteSpace($inputMsg)) {
            $Message = $inputMsg.Trim()
        } else {
            $Message = $latestCommitMsg
        }
    }

    if ($SkipCI) {
        $Message = "$Message [skip ci]"
        Write-Host "Appending [skip ci] to commit message..." -ForegroundColor Magenta
    }

    git add .
    git commit -m "$Message"
    git push origin main
    Write-Host "`nChanges pushed to main successfully." -ForegroundColor Green
} elseif ($SkipCI) {
    Write-Host "`nNo changes to commit." -ForegroundColor Gray
}

# If user just wanted to push with [skip ci], exit here
if ($SkipCI) {
    exit 0
}

# Handle Official Tag Release
if (-not $Tag) {
    $Tag = Read-Host "`nEnter version tag to release (or press Enter to skip tag, e.g. v1.1.0)"
}

if ($Tag) {
    # Ensure tag starts with 'v'
    if (-not $Tag.StartsWith("v")) {
        $Tag = "v$Tag"
    }

    Write-Host "`nCreating tag $Tag and pushing to GitHub..." -ForegroundColor Cyan
    git tag $Tag
    git push origin $Tag

    Write-Host "`nOfficial release $Tag triggered successfully on GitHub Actions!" -ForegroundColor Green
    Write-Host "Check progress at: https://github.com/dangphuc2470/exif-copy/actions" -ForegroundColor Cyan
} else {
    Write-Host "`nNo tag created. Normal push completed." -ForegroundColor Gray
}
