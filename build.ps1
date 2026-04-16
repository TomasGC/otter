#!/usr/bin/env pwsh
#Requires -Version 7.0

<#
.SYNOPSIS
    Build Otter APK with automatic version increment
.DESCRIPTION
    Increments version (versionCode and versionName) and builds APK via Docker.
.PARAMETER IncrementOnly
    Only increment version without building
.EXAMPLE
    .\build.ps1
    .\build.ps1 -IncrementOnly
#>

param(
    [switch]$IncrementOnly
)

function Update-AppVersion {
    <#
    .SYNOPSIS
        Increment version in build.gradle.kts
    #>

    $buildFile = [System.IO.Path]::Combine($PSScriptRoot, "app", "build.gradle.kts")

    if (-not [System.IO.File]::Exists($buildFile)) {
        Write-Error "build.gradle.kts not found at $buildFile"
        return $false
    }

    $content = [System.IO.File]::ReadAllText($buildFile)

    # Extract and increment versionCode
    if ($content -match 'versionCode = (\d+)') {
        $currentVersionCode = [int]$matches[1]
        $newVersionCode = $currentVersionCode + 1
        Write-Host "Incrementing versionCode: $currentVersionCode -> $newVersionCode"
        $content = $content -replace 'versionCode = \d+', "versionCode = $newVersionCode"
    }
    else {
        Write-Error "Could not find versionCode in build.gradle.kts"
        return $false
    }

    # Extract and increment versionName (patch)
    if ($content -match 'versionName = "(\d+)\.(\d+)\.(\d+)"') {
        $major = $matches[1]
        $minor = $matches[2]
        $patch = [int]$matches[3]
        $newPatch = $patch + 1
        $newVersionName = "$major.$minor.$newPatch"
        Write-Host "Incrementing versionName: $major.$minor.$patch -> $newVersionName"
        $content = $content -replace 'versionName = "\d+\.\d+\.\d+"', "versionName = `"$newVersionName`""
    }
    else {
        Write-Error "Could not find versionName in build.gradle.kts"
        return $false
    }

    # Write back to file
    [System.IO.File]::WriteAllText($buildFile, $content)

    Write-Host "✅ Version incremented successfully!" -ForegroundColor Green
    Write-Host "   versionCode: $newVersionCode" -ForegroundColor Cyan
    Write-Host "   versionName: $newVersionName" -ForegroundColor Cyan

    return $true
}

function Invoke-ApkBuild {
    <#
    .SYNOPSIS
        Build APK via Docker
    #>

    Write-Host "🐳 Building APK via Docker..." -ForegroundColor Yellow

    $dockerBuildScript = [System.IO.Path]::Combine($PSScriptRoot, "docker-build.ps1")

    if (-not [System.IO.File]::Exists($dockerBuildScript)) {
        Write-Error "docker-build.ps1 not found at $dockerBuildScript"
        return $false
    }

    & $dockerBuildScript assembleDebug

    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✅ APK built successfully!" -ForegroundColor Green
        Write-Host "📦 Location: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Cyan
        return $true
    }
    else {
        Write-Error "Build failed with exit code $LASTEXITCODE"
        return $false
    }
}

# ============================================================================
# Main Script
# ============================================================================

Write-Host "🚀 Otter Build Script" -ForegroundColor Cyan
Write-Host ""

# Step 1: Increment version
Write-Host "Step 1: Incrementing version..." -ForegroundColor Yellow
$versionSuccess = Update-AppVersion

if (-not $versionSuccess) {
    Write-Error "Failed to increment version"
    exit 1
}

Write-Host ""

# Step 2: Build APK (unless -IncrementOnly)
if ($IncrementOnly) {
    Write-Host "✅ Version incremented (build skipped)" -ForegroundColor Green
    exit 0
}

Write-Host "Step 2: Building APK..." -ForegroundColor Yellow
$buildSuccess = Invoke-ApkBuild

if (-not $buildSuccess) {
    exit 1
}

exit 0
