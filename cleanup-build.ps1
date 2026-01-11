# Cleanup script for locked build directories
# Run this AFTER closing Android Studio

Write-Host "=== Build Directory Cleanup Script ===" -ForegroundColor Cyan
Write-Host ""

# Stop Gradle daemons first
Write-Host "1. Stopping Gradle daemons..." -ForegroundColor Yellow
try {
    & .\gradlew.bat --stop 2>&1 | Out-Null
    Write-Host "   ✓ Gradle daemons stopped" -ForegroundColor Green
} catch {
    Write-Host "   ⚠ Could not stop Gradle daemons (may already be stopped)" -ForegroundColor Yellow
}

# Wait a moment for file handles to release
Start-Sleep -Seconds 2

# Check if Android Studio is still running
Write-Host "`n2. Checking for Android Studio processes..." -ForegroundColor Yellow
$studioProcesses = Get-Process | Where-Object {
    $_.Path -like "*AndroidStudio*" -or 
    $_.Path -like "*studio64*" -or 
    $_.ProcessName -eq "studio64"
} -ErrorAction SilentlyContinue

if ($studioProcesses) {
    Write-Host "   ✗ Android Studio is still running!" -ForegroundColor Red
    Write-Host "   Please close Android Studio completely and run this script again." -ForegroundColor Yellow
    Write-Host "   Found processes:" -ForegroundColor Yellow
    $studioProcesses | ForEach-Object {
        Write-Host "     - $($_.ProcessName) (PID: $($_.Id))" -ForegroundColor Yellow
    }
    exit 1
} else {
    Write-Host "   ✓ No Android Studio processes found" -ForegroundColor Green
}

# Try to clean the build
Write-Host "`n3. Cleaning build directories..." -ForegroundColor Yellow
try {
    & .\gradlew.bat clean
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✓ Build cleaned successfully!" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ Clean completed with warnings" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   ✗ Clean failed" -ForegroundColor Red
}

Write-Host "`n=== Cleanup Complete ===" -ForegroundColor Cyan

