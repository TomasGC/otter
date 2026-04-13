# Helper script to run Gradle commands inside Docker (PowerShell)

param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$GradleArgs
)

# Build Docker image if needed
$imageExists = docker image inspect otter-android-build:latest 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "🐳 Building Docker image (first time only)..." -ForegroundColor Cyan
    docker-compose build
}

# Run Gradle command
$argsString = $GradleArgs -join ' '
Write-Host "🚀 Running: ./gradlew $argsString" -ForegroundColor Green
docker-compose run --rm android-build ./gradlew @GradleArgs
