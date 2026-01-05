# Permanently add Node.js and Tesseract to USER PATH (no admin required)

function Add-ToUserPath {
    param (
        [string]$NewPath
    )

    if (-not (Test-Path $NewPath)) {
        Write-Warning "Path not found: $NewPath"
        return
    }

    $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")

    if ($currentPath -like "*$NewPath*") {
        Write-Host "Already in PATH: $NewPath"
    } else {
        $updatedPath = "$currentPath;$NewPath"
        [Environment]::SetEnvironmentVariable("Path", $updatedPath, "User")
        Write-Host "Added to PATH: $NewPath"
    }
}

Write-Host "Configuring USER PATH..."

# --- Tesseract ---
$tesseractPath = "C:\Users\306051\AppData\Local\Programs\Tesseract-OCR"
Add-ToUserPath $tesseractPath

# --- Node.js (default installer path) ---
$nodePath = "C:\Users\306051\nodejs\node-v24.12.0-win-x64"
Add-ToUserPath $nodePath

Write-Host ""
Write-Host "Done."
Write-Host "Restart VS Code and any terminals for changes to take effect."

 