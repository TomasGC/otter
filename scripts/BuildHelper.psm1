#!/usr/bin/env pwsh
#Requires -Version 7.0

<#
.SYNOPSIS
    Build helper module for Otter Android application
.DESCRIPTION
    Provides reusable classes and functions for building and testing.
    Supports both local Gradle and Docker execution.
#>

using namespace System.IO
using namespace System.Collections.Generic

# ============================================================================
# Classes
# ============================================================================

<#
.SYNOPSIS
    Gradle task executor (local or Docker)
#>
class GradleRunner {
    [bool]$UseDocker
    [string]$ProjectRoot
    hidden [string]$GradlewPath

    GradleRunner([string]$projectRoot, [bool]$useDocker) {
        $this.ProjectRoot = $projectRoot
        $this.UseDocker = $useDocker
        $this.GradlewPath = $this.ResolveGradlewPath()
    }

    <#
    .SYNOPSIS
        Execute a Gradle task
    .PARAMETER Task
        Gradle task name (e.g., "assembleDebug", "testDebugUnitTest")
    .PARAMETER ExtraArgs
        Additional arguments to pass to Gradle
    .OUTPUTS
        Boolean - true if task succeeded
    #>
    [bool] ExecuteTask([string]$Task, [string[]]$ExtraArgs) {
        if ($this.UseDocker) {
            return $this.ExecuteInDocker($Task, $ExtraArgs)
        }
        else {
            return $this.ExecuteLocal($Task, $ExtraArgs)
        }
    }

    <#
    .SYNOPSIS
        Stop Gradle daemons (local only)
    #>
    [void] StopDaemons() {
        if ($this.UseDocker) {
            # Docker containers are ephemeral, no daemons to stop
            return
        }

        Write-Verbose "Stopping Gradle daemons..."
        $null = & $this.GradlewPath --stop 2>&1
    }

    hidden [string] ResolveGradlewPath() {
        # Detect Windows by checking for gradlew.bat existence
        $batPath = [Path]::Combine($this.ProjectRoot, "gradlew.bat")
        if ([File]::Exists($batPath)) {
            return $batPath
        }

        # Fallback to Unix gradlew
        return [Path]::Combine($this.ProjectRoot, "gradlew")
    }

    hidden [bool] ExecuteLocal([string]$Task, [string[]]$ExtraArgs) {
        if (-not [File]::Exists($this.GradlewPath)) {
            Write-Error "gradlew not found at $($this.GradlewPath)"
            return $false
        }

        $allArgs = @($Task) + $ExtraArgs
        & $this.GradlewPath @allArgs

        return ($LASTEXITCODE -eq 0)
    }

    hidden [bool] ExecuteInDocker([string]$Task, [string[]]$ExtraArgs) {
        # Ensure Docker image exists
        if (-not [DockerManager]::EnsureImageExists()) {
            return $false
        }

        $allArgs = @($Task) + $ExtraArgs
        $argsString = $allArgs -join ' '

        Write-Verbose "Running: docker-compose run --rm android-build ./gradlew $argsString"
        docker-compose run --rm android-build ./gradlew @allArgs

        return ($LASTEXITCODE -eq 0)
    }
}

<#
.SYNOPSIS
    Docker image manager
#>
class DockerManager {
    hidden static [string]$ImageName = "otter-android-build:latest"

    <#
    .SYNOPSIS
        Ensure Docker image exists, build if necessary
    .OUTPUTS
        Boolean - true if image exists or was built successfully
    #>
    static [bool] EnsureImageExists() {
        # Check if image exists
        $null = docker image inspect ([DockerManager]::ImageName) 2>&1
        if ($LASTEXITCODE -eq 0) {
            return $true
        }

        Write-Host "🐳 Building Docker image (first time only)..." -ForegroundColor Cyan
        docker-compose build

        return ($LASTEXITCODE -eq 0)
    }
}

<#
.SYNOPSIS
    Version manager for build.gradle.kts
#>
class VersionManager {
    <#
    .SYNOPSIS
        Increment version (versionCode and versionName)
    .PARAMETER BuildFile
        Path to build.gradle.kts file
    .OUTPUTS
        Boolean - true if version was incremented successfully
    #>
    static [bool] IncrementVersion([string]$BuildFile) {
        if (-not [File]::Exists($BuildFile)) {
            Write-Error "build.gradle.kts not found at $BuildFile"
            return $false
        }

        $content = [File]::ReadAllText($BuildFile)

        # Increment versionCode
        if ($content -match 'versionCode = (\d+)') {
            $currentVersionCode = [int]$matches[1]
            $newVersionCode = $currentVersionCode + 1
            Write-Host "Incrementing versionCode: $currentVersionCode → $newVersionCode" -ForegroundColor Cyan
            $content = $content -replace 'versionCode = \d+', "versionCode = $newVersionCode"
        }
        else {
            Write-Error "Could not find versionCode in build.gradle.kts"
            return $false
        }

        # Increment versionName (patch)
        if ($content -match 'versionName = "(\d+)\.(\d+)\.(\d+)"') {
            $major = $matches[1]
            $minor = $matches[2]
            $patch = [int]$matches[3]
            $newPatch = $patch + 1
            $newVersionName = "$major.$minor.$newPatch"
            Write-Host "Incrementing versionName: $major.$minor.$patch → $newVersionName" -ForegroundColor Cyan
            $content = $content -replace 'versionName = "\d+\.\d+\.\d+"', "versionName = `"$newVersionName`""
        }
        else {
            Write-Error "Could not find versionName in build.gradle.kts"
            return $false
        }

        # Write back
        [File]::WriteAllText($BuildFile, $content)

        Write-Host "✅ Version incremented successfully!" -ForegroundColor Green
        Write-Host "   versionCode: $newVersionCode" -ForegroundColor Gray
        Write-Host "   versionName: $newVersionName" -ForegroundColor Gray

        return $true
    }
}

# ============================================================================
# Utility Functions
# ============================================================================

function Write-Header {
    param([string]$Message)
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host " $Message" -ForegroundColor Cyan
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host ""
}

function Write-Step {
    param([string]$Message)
    Write-Host "▶ $Message" -ForegroundColor Yellow
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Failure {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Write-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor Cyan
}

# ============================================================================
# Exports
# ============================================================================

Export-ModuleMember -Function @(
    'Write-Header',
    'Write-Step',
    'Write-Success',
    'Write-Failure',
    'Write-Info'
)
