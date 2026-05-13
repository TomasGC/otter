#!/usr/bin/env pwsh
#Requires -Version 7.0

<#
.SYNOPSIS
    Run tests for Otter Android application
.DESCRIPTION
    Executes unit tests, integration tests, instrumented tests, and generates coverage reports.
    Supports filtering by test class or method.
.PARAMETER TestType
    Type of tests to run: Unit, Instrumented, All, Lint
.PARAMETER Filter
    Filter tests by class or method (e.g., "*ViewModelTest*", "ExtractionServiceTest.shouldExtractZip")
.PARAMETER Coverage
    Generate coverage report (unit tests only)
.PARAMETER Lint
    Run lint checks before tests
.PARAMETER SkipBuild
    Skip assembleDebug before tests (faster if already built)
.EXAMPLE
    .\test.ps1
    .\test.ps1 -TestType Unit
    .\test.ps1 -TestType Unit -Filter "*ViewModelTest*"
    .\test.ps1 -TestType Instrumented
    .\test.ps1 -TestType All -Coverage
    .\test.ps1 -Lint
#>

param(
    [ValidateSet("Unit", "Instrumented", "All", "Lint")]
    [string]$TestType = "Unit",

    [string]$Filter = "",

    [switch]$Coverage,

    [switch]$Lint,

    [switch]$SkipBuild
)

# ============================================================================
# Helper Functions
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

function Get-GradlewCommand {
    <#
    .SYNOPSIS
        Get the correct Gradle wrapper command for the platform
    #>
    if ($IsWindows -or ($PSVersionTable.PSVersion.Major -le 5)) {
        $gradlewPath = [System.IO.Path]::Combine($PSScriptRoot, "gradlew.bat")
        if ([System.IO.File]::Exists($gradlewPath)) {
            return $gradlewPath
        }
    }

    # Fallback to Unix gradlew
    return [System.IO.Path]::Combine($PSScriptRoot, "gradlew")
}

function Test-GradlewExists {
    $gradlewPath = Get-GradlewCommand
    return [System.IO.File]::Exists($gradlewPath)
}

function Stop-GradleDaemons {
    Write-Step "Stopping Gradle daemons..."
    $gradlewPath = Get-GradlewCommand
    & $gradlewPath --stop | Out-Null
}

function Test-EmulatorOrDeviceConnected {
    <#
    .SYNOPSIS
        Check if Android emulator or physical device is connected
    #>
    Write-Step "Checking for connected Android devices..."

    try {
        $devices = adb devices 2>&1 | Select-String -Pattern "device$"

        if ($devices.Count -gt 0) {
            Write-Success "Found $($devices.Count) connected device(s)"
            return $true
        }
        else {
            Write-Failure "No Android devices or emulators connected"
            Write-Info "Start an emulator or connect a device, then run:"
            Write-Info "  adb devices"
            return $false
        }
    }
    catch {
        Write-Failure "adb not found. Android SDK must be installed."
        return $false
    }
}

function Invoke-UnitTests {
    <#
    .SYNOPSIS
        Run unit tests
    #>
    param(
        [string]$Filter,
        [bool]$WithCoverage
    )

    Write-Header "Running Unit Tests"

    $gradlewPath = Get-GradlewCommand

    if ($WithCoverage) {
        Write-Step "Running unit tests with coverage..."
        $task = "testDebugUnitTestCoverage"
    }
    else {
        Write-Step "Running unit tests..."
        $task = "testDebugUnitTest"
    }

    if ($Filter) {
        Write-Info "Filter: $Filter"
        & $gradlewPath $task --tests=$Filter
    }
    else {
        & $gradlewPath $task
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Unit tests passed"

        if ($WithCoverage) {
            $reportPath = [System.IO.Path]::Combine($PSScriptRoot, "app", "build", "reports", "coverage", "test", "debug", "index.html")
            if ([System.IO.File]::Exists($reportPath)) {
                Write-Info "Coverage report: app\build\reports\coverage\test\debug\index.html"
            }
        }

        $reportPath = [System.IO.Path]::Combine($PSScriptRoot, "app", "build", "reports", "tests", "testDebugUnitTest", "index.html")
        if ([System.IO.File]::Exists($reportPath)) {
            Write-Info "Test report: app\build\reports\tests\testDebugUnitTest\index.html"
        }

        return $true
    }
    else {
        Write-Failure "Unit tests failed"
        return $false
    }
}

function Invoke-InstrumentedTests {
    <#
    .SYNOPSIS
        Run instrumented tests (requires emulator or device)
    #>
    param([string]$Filter)

    Write-Header "Running Instrumented Tests"

    # Check for connected device
    if (-not (Test-EmulatorOrDeviceConnected)) {
        return $false
    }

    $gradlewPath = Get-GradlewCommand

    Write-Step "Running instrumented tests..."

    if ($Filter) {
        Write-Info "Filter: $Filter"
        & $gradlewPath connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=$Filter
    }
    else {
        & $gradlewPath connectedDebugAndroidTest
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Instrumented tests passed"

        $reportPath = [System.IO.Path]::Combine($PSScriptRoot, "app", "build", "reports", "androidTests", "connected", "debug", "index.html")
        if ([System.IO.File]::Exists($reportPath)) {
            Write-Info "Test report: app\build\reports\androidTests\connected\debug\index.html"
        }

        return $true
    }
    else {
        Write-Failure "Instrumented tests failed"
        return $false
    }
}

function Invoke-LintChecks {
    <#
    .SYNOPSIS
        Run lint checks
    #>

    Write-Header "Running Lint Checks"

    $gradlewPath = Get-GradlewCommand

    Write-Step "Running lint..."
    & $gradlewPath lintDebug

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Lint checks passed"

        $reportPath = [System.IO.Path]::Combine($PSScriptRoot, "app", "build", "reports", "lint-results-debug.html")
        if ([System.IO.File]::Exists($reportPath)) {
            Write-Info "Lint report: app\build\reports\lint-results-debug.html"
        }

        return $true
    }
    else {
        Write-Failure "Lint checks failed"
        return $false
    }
}

function Invoke-BuildDebug {
    <#
    .SYNOPSIS
        Build debug APK before tests
    #>

    Write-Header "Building Debug APK"

    $gradlewPath = Get-GradlewCommand

    Write-Step "Running assembleDebug..."
    & $gradlewPath assembleDebug

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Build succeeded"
        return $true
    }
    else {
        Write-Failure "Build failed"
        return $false
    }
}

# ============================================================================
# Main Script
# ============================================================================

Write-Host "🧪 Otter Test Runner" -ForegroundColor Cyan
Write-Host ""

# Validate Gradle wrapper exists
if (-not (Test-GradlewExists)) {
    Write-Failure "gradlew not found in project root"
    exit 1
}

# Stop Gradle daemons
Stop-GradleDaemons

# Build if not skipped
if (-not $SkipBuild) {
    if (-not (Invoke-BuildDebug)) {
        exit 1
    }
}

# Run lint if requested
if ($Lint) {
    if (-not (Invoke-LintChecks)) {
        exit 1
    }
}

# Run tests based on type
$testSuccess = $false

switch ($TestType) {
    "Unit" {
        $testSuccess = Invoke-UnitTests -Filter $Filter -WithCoverage $Coverage
    }
    "Instrumented" {
        $testSuccess = Invoke-InstrumentedTests -Filter $Filter
    }
    "All" {
        $unitSuccess = Invoke-UnitTests -Filter $Filter -WithCoverage $Coverage

        if ($unitSuccess) {
            $instrumentedSuccess = Invoke-InstrumentedTests -Filter $Filter
            $testSuccess = $instrumentedSuccess
        }
        else {
            $testSuccess = $false
        }
    }
    "Lint" {
        # Lint already ran above
        $testSuccess = $true
    }
}

# Summary
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host " Test Summary" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

if ($testSuccess) {
    Write-Success "All tests passed ✨"
    exit 0
}
else {
    Write-Failure "Tests failed"
    exit 1
}
