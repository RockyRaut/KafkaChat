# Force cleanup script - attempts multiple methods to clean locked build directories
# Run this when files are locked by Android Studio or other processes

Write-Host "=== Force Cleanup Script ===" -ForegroundColor Cyan
Write-Host ""

# Method 1: Stop Gradle daemons
Write-Host "Method 1: Stopping Gradle daemons..." -ForegroundColor Yellow
& .\gradlew.bat --stop 2>&1 | Out-Null
Start-Sleep -Seconds 3

# Method 2: Check for Android Studio/Java processes
Write-Host "`nMethod 2: Checking for locking processes..." -ForegroundColor Yellow
$lockingProcesses = Get-Process | Where-Object {
    $_.ProcessName -match "studio|java|gradle" -and
    $_.Path -notlike "*\.cursor\*"
} -ErrorAction SilentlyContinue

if ($lockingProcesses) {
    Write-Host "Found processes that may be locking files:" -ForegroundColor Yellow
    $lockingProcesses | ForEach-Object {
        Write-Host "  - $($_.ProcessName) (PID: $($_.Id), Path: $($_.Path))" -ForegroundColor Yellow
    }
    Write-Host "`n⚠ Please close Android Studio before continuing." -ForegroundColor Red
    Write-Host "Press any key after closing Android Studio to continue..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

# Method 3: Try to delete the specific problematic directory with retries
Write-Host "`nMethod 3: Attempting to delete locked directories..." -ForegroundColor Yellow
$problemPath = "app\build\intermediates\merged_res_blame_folder\debug\mergeDebugResources\out"
$buildPath = "app\build"

if (Test-Path $problemPath) {
    Write-Host "Attempting to remove: $problemPath" -ForegroundColor Yellow
    $maxRetries = 3
    $retryCount = 0
    $success = $false
    
    while ($retryCount -lt $maxRetries -and -not $success) {
        $retryCount++
        Write-Host "  Attempt $retryCount of $maxRetries..." -ForegroundColor Gray
        try {
            # Try removing with -Force and -ErrorAction Stop to catch errors
            Remove-Item -Path $problemPath -Recurse -Force -ErrorAction Stop
            $success = $true
            Write-Host "  ✓ Successfully removed directory" -ForegroundColor Green
        } catch {
            Write-Host "  ✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
            if ($retryCount -lt $maxRetries) {
                Write-Host "  Waiting 2 seconds before retry..." -ForegroundColor Gray
                Start-Sleep -Seconds 2
            }
        }
    }
    
    if (-not $success) {
        Write-Host "`n⚠ Could not delete directory directly." -ForegroundColor Yellow
        Write-Host "Trying alternative: Delete entire build directory..." -ForegroundColor Yellow
        try {
            Remove-Item -Path $buildPath -Recurse -Force -ErrorAction Stop
            Write-Host "✓ Successfully removed entire build directory" -ForegroundColor Green
        } catch {
            Write-Host "✗ Still locked. Please:" -ForegroundColor Red
            Write-Host "  1. Close Android Studio completely" -ForegroundColor Yellow
            Write-Host "  2. Close any other IDEs or file explorers with this folder open" -ForegroundColor Yellow
            Write-Host "  3. Run this script again" -ForegroundColor Yellow
            exit 1
        }
    }
} else {
    Write-Host "Directory not found (may already be deleted)" -ForegroundColor Green
}

# Method 4: Run Gradle clean
Write-Host "`nMethod 4: Running Gradle clean..." -ForegroundColor Yellow
try {
    & .\gradlew.bat clean 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Gradle clean completed successfully" -ForegroundColor Green
    } else {
        Write-Host "⚠ Gradle clean completed with warnings" -ForegroundColor Yellow
    }
} catch {
    Write-Host "✗ Gradle clean failed, but directories may still be cleaned" -ForegroundColor Yellow
}

Write-Host "`n=== Cleanup Complete ===" -ForegroundColor Cyan
Write-Host "You can now build your project normally." -ForegroundColor Green


