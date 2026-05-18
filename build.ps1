#!/usr/bin/env pwsh
#Requires -Version 7.0

# ============================================================================
# CRITICAL: using module MUST be at the very top (parse-time, not runtime)
# ============================================================================
using module "./scripts/BuildHelper.psm1"

<#
.SYNOPSIS
    Build Otter APK with automatic version increment
.DESCRIPTION
    Increments version (versionCode and versionName) and builds APK.
    Supports both local Gradle and Docker execution.
.PARAMETER IncrementOnly
    Only increment version without building
.PARAMETER Docker
    Run build inside Docker container
.EXAMPLE
    .\build.ps1
    .\build.ps1 -IncrementOnly
    .\build.ps1 -Docker
#>

param(
    [switch]$IncrementOnly,
    [switch]$Docker
)

# ============================================================================
# Main Script
# ============================================================================

Write-Host "🚀 Otter Build Script" -ForegroundColor Cyan
Write-Host ""

# Step 1: Increment version
Write-Header "Step 1: Incrementing version"
$buildFile = [System.IO.Path]::Combine($PSScriptRoot, "app", "build.gradle.kts")
$versionSuccess = [VersionManager]::IncrementVersion($buildFile)

if (-not $versionSuccess) {
    Write-Failure "Failed to increment version"
    exit 1
}

# Step 2: Build APK (unless -IncrementOnly)
if ($IncrementOnly) {
    Write-Success "Version incremented (build skipped)"
    exit 0
}

Write-Header "Step 2: Building APK"
$runner = [GradleRunner]::new($PSScriptRoot, $Docker)
$runner.StopDaemons()

Write-Step "Running assembleDebug..."
$buildSuccess = $runner.ExecuteTask("assembleDebug", @())

if ($buildSuccess) {
    Write-Host ""
    Write-Success "APK built successfully!"
    Write-Info "Location: app\build\outputs\apk\debug\app-debug.apk"
    exit 0
}
else {
    Write-Failure "Build failed"
    exit 1
}
