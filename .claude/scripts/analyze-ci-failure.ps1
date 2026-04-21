#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Analyzes GitHub Actions CI/CD failures and extracts actionable errors.

.DESCRIPTION
    Downloads failed logs from GitHub Actions, categorizes errors, and provides
    a concise summary with actionable recommendations.

.PARAMETER RunId
    GitHub Actions run ID to analyze.

.PARAMETER Repo
    Repository in format "owner/repo" (default: TomasGC/otter).

.EXAMPLE
    .\analyze-ci-failure.ps1 -RunId 24734772369
    .\analyze-ci-failure.ps1 -RunId 24734772369 -Repo TomasGC/otter

.NOTES
    Requires: gh CLI (GitHub CLI)
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$RunId,

    [Parameter(Mandatory = $false)]
    [string]$Repo = "TomasGC/otter"
)

$ErrorActionPreference = "Stop"

# Colors
$Red = "`e[31m"
$Green = "`e[32m"
$Yellow = "`e[33m"
$Blue = "`e[34m"
$Magenta = "`e[35m"
$Cyan = "`e[36m"
$Reset = "`e[0m"
$Bold = "`e[1m"

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host "${Bold}${Cyan}═══════════════════════════════════════════════════════════════${Reset}"
    Write-Host "${Bold}${Cyan}  $Text${Reset}"
    Write-Host "${Bold}${Cyan}═══════════════════════════════════════════════════════════════${Reset}"
    Write-Host ""
}

function Write-Category {
    param([string]$Text, [int]$Count)
    if ($Count -gt 0) {
        Write-Host ""
        Write-Host "${Bold}${Yellow}▶ $Text ($Count found)${Reset}"
        Write-Host "${Yellow}$("-" * 60)${Reset}"
    }
}

function Write-ErrorItem {
    param([string]$Text)
    Write-Host "  ${Red}✗${Reset} $Text"
}

function Write-WarningItem {
    param([string]$Text)
    Write-Host "  ${Yellow}⚠${Reset} $Text"
}

function Write-InfoItem {
    param([string]$Text)
    Write-Host "  ${Blue}ℹ${Reset} $Text"
}

function Write-Success {
    param([string]$Text)
    Write-Host "${Green}✓${Reset} $Text"
}

Write-Header "CI Failure Analysis: Run #$RunId"

# Check gh CLI
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "${Red}Error: gh CLI not found. Install from: https://cli.github.com/${Reset}"
    exit 1
}

Write-Host "${Cyan}Fetching logs from GitHub...${Reset}"
$tempFile = New-TemporaryFile
try {
    gh run view $RunId --repo $Repo --log-failed | Out-File -FilePath $tempFile.FullName -Encoding UTF8
    $logs = Get-Content $tempFile.FullName -Raw
} catch {
    Write-Host "${Red}Failed to fetch logs: $_${Reset}"
    exit 1
} finally {
    Remove-Item $tempFile.FullName -ErrorAction SilentlyContinue
}

if ([string]::IsNullOrWhiteSpace($logs)) {
    Write-Host "${Yellow}No failed logs found. Run may have passed or is still in progress.${Reset}"
    exit 0
}

# Parse logs into lines
$lines = $logs -split "`n"

# Error categories
$compilationErrors = @()
$testFailures = @()
$lintErrors = @()
$infrastructureErrors = @()
$buildErrors = @()
$unknownErrors = @()

# Pattern matching
foreach ($line in $lines) {
    $line = $line.Trim()

    # Skip empty lines and noise
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    if ($line -match "^\[command\]|^INFO |^WARNING.*Node\.js|^##\[|daemon started|daemon not running") { continue }

    # Infrastructure errors (emulator, timeout, network)
    if ($line -match "device offline|Connection refused|timeout|Unable to connect|emulator.*failed|INSTALL_FAILED") {
        $infrastructureErrors += $line
    }
    # Compilation errors (Kotlin, Java)
    elseif ($line -match "^e: |Unresolved reference|Compilation error|BUILD FAILED in") {
        $compilationErrors += $line
    }
    # Test failures
    elseif ($line -match "FAILED|AssertionError|Test.*failed|expected.*but was|java\.lang\.|kotlin\.|org\.junit\.") {
        if ($line -match "BUILD FAILED") { continue } # Skip generic build failed
        $testFailures += $line
    }
    # Lint errors
    elseif ($line -match "Lint found|UnspecifiedRegisterReceiverFlag|UnsafeImplicitIntentLaunch|SecurityException") {
        $lintErrors += $line
    }
    # Build errors (Gradle, dependency issues)
    elseif ($line -match "Could not resolve|Dependency|artifact not found|A problem occurred|Execution failed for task") {
        $buildErrors += $line
    }
    # Generic errors
    elseif ($line -match "ERROR |Error:|error:|FAILURE:|Failed to|Exception|fatal:") {
        $unknownErrors += $line
    }
}

# Remove duplicates
$compilationErrors = $compilationErrors | Select-Object -Unique
$testFailures = $testFailures | Select-Object -Unique
$lintErrors = $lintErrors | Select-Object -Unique
$infrastructureErrors = $infrastructureErrors | Select-Object -Unique
$buildErrors = $buildErrors | Select-Object -Unique
$unknownErrors = $unknownErrors | Select-Object -Unique

# Display results
Write-Header "Error Summary"

$totalErrors = $compilationErrors.Count + $testFailures.Count + $lintErrors.Count + $infrastructureErrors.Count + $buildErrors.Count + $unknownErrors.Count

if ($totalErrors -eq 0) {
    Write-Success "No critical errors found in logs."
    Write-Host "${Yellow}The failure may be a transient infrastructure issue.${Reset}"
    exit 0
}

# Infrastructure Errors (highest priority for actionability)
Write-Category "Infrastructure Errors" $infrastructureErrors.Count
foreach ($err in $infrastructureErrors | Select-Object -First 10) {
    Write-ErrorItem $err
}
if ($infrastructureErrors.Count -gt 10) {
    Write-InfoItem "... and $($infrastructureErrors.Count - 10) more infrastructure errors"
}

# Compilation Errors
Write-Category "Compilation Errors" $compilationErrors.Count
foreach ($err in $compilationErrors | Select-Object -First 10) {
    Write-ErrorItem $err
}
if ($compilationErrors.Count -gt 10) {
    Write-InfoItem "... and $($compilationErrors.Count - 10) more compilation errors"
}

# Build Errors
Write-Category "Build Errors" $buildErrors.Count
foreach ($err in $buildErrors | Select-Object -First 10) {
    Write-ErrorItem $err
}
if ($buildErrors.Count -gt 10) {
    Write-InfoItem "... and $($buildErrors.Count - 10) more build errors"
}

# Test Failures
Write-Category "Test Failures" $testFailures.Count
foreach ($err in $testFailures | Select-Object -First 15) {
    Write-ErrorItem $err
}
if ($testFailures.Count -gt 15) {
    Write-InfoItem "... and $($testFailures.Count - 15) more test failures"
}

# Lint Errors
Write-Category "Lint Errors" $lintErrors.Count
foreach ($err in $lintErrors | Select-Object -First 10) {
    Write-WarningItem $err
}
if ($lintErrors.Count -gt 10) {
    Write-InfoItem "... and $($lintErrors.Count - 10) more lint errors"
}

# Unknown Errors
if ($unknownErrors.Count -gt 0) {
    Write-Category "Other Errors" $unknownErrors.Count
    foreach ($err in $unknownErrors | Select-Object -First 5) {
        Write-ErrorItem $err
    }
    if ($unknownErrors.Count -gt 5) {
        Write-InfoItem "... and $($unknownErrors.Count - 5) more errors"
    }
}

# Recommendations
Write-Header "Recommendations"

if ($infrastructureErrors.Count -gt 0) {
    Write-Host "${Bold}Infrastructure Issues Detected:${Reset}"
    if ($logs -match "device offline|adb.*offline") {
        Write-ErrorItem "Android Emulator failed to start or crashed"
        Write-Host "    ${Cyan}→ Retry the workflow (likely transient emulator issue)${Reset}"
        Write-Host "    ${Cyan}→ Check if emulator configuration is correct${Reset}"
    }
    if ($logs -match "timeout|timed out") {
        Write-ErrorItem "Timeout occurred during execution"
        Write-Host "    ${Cyan}→ Increase timeout values in workflow${Reset}"
        Write-Host "    ${Cyan}→ Optimize slow operations${Reset}"
    }
    Write-Host ""
}

if ($compilationErrors.Count -gt 0) {
    Write-Host "${Bold}Compilation Issues Detected:${Reset}"
    Write-ErrorItem "Fix compilation errors before re-running"
    Write-Host "    ${Cyan}→ Check for unresolved references${Reset}"
    Write-Host "    ${Cyan}→ Verify imports and dependencies${Reset}"
    Write-Host "    ${Cyan}→ Run './gradlew build' locally${Reset}"
    Write-Host ""
}

if ($buildErrors.Count -gt 0) {
    Write-Host "${Bold}Build Issues Detected:${Reset}"
    Write-ErrorItem "Build configuration or dependency problems"
    Write-Host "    ${Cyan}→ Check build.gradle.kts for errors${Reset}"
    Write-Host "    ${Cyan}→ Verify all dependencies are available${Reset}"
    Write-Host "    ${Cyan}→ Clear Gradle cache and rebuild${Reset}"
    Write-Host ""
}

if ($testFailures.Count -gt 0) {
    Write-Host "${Bold}Test Failures Detected:${Reset}"
    Write-ErrorItem "$($testFailures.Count) tests failed"
    Write-Host "    ${Cyan}→ Run tests locally: ./gradlew test connectedAndroidTest${Reset}"
    Write-Host "    ${Cyan}→ Check test logs for assertion failures${Reset}"
    Write-Host "    ${Cyan}→ Fix failing tests or update assertions${Reset}"
    Write-Host ""
}

if ($lintErrors.Count -gt 0) {
    Write-Host "${Bold}Lint Issues Detected:${Reset}"
    Write-WarningItem "$($lintErrors.Count) lint errors found"
    Write-Host "    ${Cyan}→ Run './gradlew lint' locally${Reset}"
    Write-Host "    ${Cyan}→ Fix security and code quality issues${Reset}"
    Write-Host ""
}

# Priority action
Write-Header "Priority Action"

if ($infrastructureErrors.Count -gt 0 -and $compilationErrors.Count -eq 0) {
    Write-Host "${Green}→ Retry the workflow${Reset} (infrastructure failure, likely transient)"
    Write-Host "  ${Cyan}gh run rerun $RunId --repo $Repo${Reset}"
} elseif ($compilationErrors.Count -gt 0) {
    Write-Host "${Red}→ Fix compilation errors first${Reset} (code changes needed)"
} elseif ($buildErrors.Count -gt 0) {
    Write-Host "${Red}→ Fix build configuration${Reset} (build.gradle.kts or dependencies)"
} elseif ($testFailures.Count -gt 0) {
    Write-Host "${Yellow}→ Fix failing tests${Reset} (test code or assertions need updates)"
} else {
    Write-Host "${Yellow}→ Review logs manually${Reset}"
    Write-Host "  ${Cyan}gh run view $RunId --repo $Repo --log-failed${Reset}"
}

Write-Host ""
Write-Host "${Cyan}View full run: ${Reset}https://github.com/$Repo/actions/runs/$RunId"
Write-Host ""
