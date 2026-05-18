#!/usr/bin/env pwsh
#Requires -Version 7.0

# ============================================================================
# CRITICAL: using module MUST be at the very top (parse-time, not runtime)
# ============================================================================
using module "./scripts/BuildHelper.psm1"

<#
.SYNOPSIS
    Run tests for Otter Android application
.DESCRIPTION
    Executes unit tests, instrumented tests, lint checks, and generates coverage reports.
    Supports both local Gradle and Docker execution.
.PARAMETER TestType
    Type of tests to run: Unit, Instrumented, All, Lint
.PARAMETER Filter
    Filter tests by class or method (e.g., "*ViewModelTest*")
.PARAMETER Coverage
    Generate coverage report (unit tests only)
.PARAMETER Lint
    Run lint checks before tests
.PARAMETER SkipBuild
    Skip assembleDebug before tests (faster if already built)
.PARAMETER Docker
    Run tests inside Docker container
.EXAMPLE
    .\test.ps1
    .\test.ps1 -TestType Unit -Coverage
    .\test.ps1 -TestType Instrumented
    .\test.ps1 -Docker
#>

param(
    [ValidateSet("Unit", "Instrumented", "All", "Lint")]
    [string]$TestType = "Unit",

    [string]$Filter = "",

    [switch]$Coverage,

    [switch]$Lint,

    [switch]$SkipBuild,

    [switch]$Docker
)

# ============================================================================
# Test Functions
# ============================================================================

function Invoke-UnitTests {
    param(
        [GradleRunner]$Runner,
        [string]$Filter,
        [bool]$WithCoverage
    )

    Write-Header "Running Unit Tests"

    $task = if ($WithCoverage) { "koverXmlReportDebug" } else { "testDebugUnitTest" }
    $extraArgs = @()

    if ($Filter) {
        Write-Info "Filter: $Filter"
        $extraArgs += "--tests=$Filter"
    }

    Write-Step "Running $task..."
    $success = $Runner.ExecuteTask($task, $extraArgs)

    if ($success) {
        Write-Success "Unit tests passed"

        if ($WithCoverage) {
            $reportPath = [System.IO.Path]::Combine($PSScriptRoot, "app", "build", "reports", "kover", "reportDebug.xml")
            if ([System.IO.File]::Exists($reportPath)) {
                Write-Info "Coverage report: app\build\reports\kover\reportDebug.xml"
            }
        }

        $testReportPath = [System.IO.Path]::Combine($PSScriptRoot, "app", "build", "reports", "tests", "testDebugUnitTest", "index.html")
        if ([System.IO.File]::Exists($testReportPath)) {
            Write-Info "Test report: app\build\reports\tests\testDebugUnitTest\index.html"
        }
    }
    else {
        Write-Failure "Unit tests failed"
    }

    return $success
}

function Invoke-InstrumentedTests {
    param(
        [GradleRunner]$Runner,
        [string]$Filter
    )

    Write-Header "Running Instrumented Tests"

    # Check for connected device
    Write-Step "Checking for connected Android devices..."
    try {
        $devices = adb devices 2>&1 | Select-String -Pattern "device$"
        if ($devices.Count -eq 0) {
            Write-Failure "No Android devices or emulators connected"
            Write-Info "Start an emulator or connect a device, then run: adb devices"
            return $false
        }
        Write-Success "Found $($devices.Count) connected device(s)"
    }
    catch {
        Write-Failure "adb not found. Android SDK must be installed."
        return $false
    }

    $extraArgs = @()
    if ($Filter) {
        Write-Info "Filter: $Filter"
        $extraArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$Filter"
    }

    Write-Step "Running connectedDebugAndroidTest..."
    $success = $Runner.ExecuteTask("connectedDebugAndroidTest", $extraArgs)

    if ($success) {
        Write-Success "Instrumented tests passed"
        $reportPath = [System.IO.Path]::Combine($PSScriptRoot, "app", "build", "reports", "androidTests", "connected", "debug", "index.html")
        if ([System.IO.File]::Exists($reportPath)) {
            Write-Info "Test report: app\build\reports\androidTests\connected\debug\index.html"
        }
    }
    else {
        Write-Failure "Instrumented tests failed"
    }

    return $success
}

function Invoke-LintChecks {
    param([GradleRunner]$Runner)

    Write-Header "Running Lint Checks"

    Write-Step "Running lintDebug..."
    $success = $Runner.ExecuteTask("lintDebug", @())

    if ($success) {
        Write-Success "Lint checks passed"
        $reportPath = [System.IO.Path]::Combine($PSScriptRoot, "app", "build", "reports", "lint-results-debug.html")
        if ([System.IO.File]::Exists($reportPath)) {
            Write-Info "Lint report: app\build\reports\lint-results-debug.html"
        }
    }
    else {
        Write-Failure "Lint checks failed"
    }

    return $success
}

function Invoke-BuildDebug {
    param([GradleRunner]$Runner)

    Write-Header "Building Debug APK"

    Write-Step "Running assembleDebug..."
    $success = $Runner.ExecuteTask("assembleDebug", @())

    if ($success) {
        Write-Success "Build succeeded"
    }
    else {
        Write-Failure "Build failed"
    }

    return $success
}

# ============================================================================
# Main Script
# ============================================================================

Write-Host "🧪 Otter Test Runner" -ForegroundColor Cyan
Write-Host ""

# Initialize Gradle runner
$runner = [GradleRunner]::new($PSScriptRoot, $Docker)
$runner.StopDaemons()

# Build if not skipped
if (-not $SkipBuild) {
    if (-not (Invoke-BuildDebug $runner)) {
        exit 1
    }
}

# Run lint if requested
if ($Lint) {
    if (-not (Invoke-LintChecks $runner)) {
        exit 1
    }
}

# Run tests based on type
$testSuccess = $false

switch ($TestType) {
    "Unit" {
        $testSuccess = Invoke-UnitTests -Runner $runner -Filter $Filter -WithCoverage $Coverage
    }
    "Instrumented" {
        $testSuccess = Invoke-InstrumentedTests -Runner $runner -Filter $Filter
    }
    "All" {
        $unitSuccess = Invoke-UnitTests -Runner $runner -Filter $Filter -WithCoverage $Coverage
        if ($unitSuccess) {
            $testSuccess = Invoke-InstrumentedTests -Runner $runner -Filter $Filter
        }
        else {
            $testSuccess = $false
        }
    }
    "Lint" {
        $testSuccess = $true  # Lint already ran above
    }
}

# Summary
Write-Header "Test Summary"

if ($testSuccess) {
    Write-Success "All tests passed ✨"
    exit 0
}
else {
    Write-Failure "Tests failed"
    exit 1
}
