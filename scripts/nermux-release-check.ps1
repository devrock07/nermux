param(
    [switch] $SkipGradle,
    [switch] $Release
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string] $Message)
    Write-Host ""
    Write-Host "== $Message =="
}

function Fail-Check {
    param([string] $Message)
    throw $Message
}

Write-Step "Git whitespace check"
git diff --check

Write-Step "Tracked artifact and signing key check"
$forbiddenPatterns = @(
    "*.apk",
    "*.aab",
    "*.ap_",
    "*.aar",
    "*.dex",
    "*.idsig",
    "*.so",
    "*.jks",
    "*.keystore",
    "*.p12",
    "*.pem",
    "*.key",
    "*.mobileprovision",
    "local.properties",
    "app/src/main/cpp/bootstrap-*.zip"
)

$trackedForbidden = @(git ls-files -- $forbiddenPatterns)
if ($trackedForbidden.Count -gt 0) {
    Fail-Check ("Do not publish generated artifacts, SDK paths, or signing material:`n" + ($trackedForbidden -join "`n"))
}

Write-Step "Secret pattern check"
$secretPatterns = @(
    "BEGIN (RSA|DSA|EC|OPENSSH|PRIVATE) KEY",
    "discord(app)?\.com/api/webhooks/",
    "ghp_[A-Za-z0-9_]{30,}",
    "github_pat_[A-Za-z0-9_]{30,}",
    "AKIA[0-9A-Z]{16}",
    "AIza[0-9A-Za-z_-]{30,}",
    "xox[baprs]-[0-9A-Za-z-]{20,}",
    "sk-[A-Za-z0-9]{30,}"
)
$grepExcludes = @(
    ":!scripts/nermux-release-check.ps1",
    ":!scripts/nermux-release-check.sh",
    ":!docs/SECURITY_RELEASE.md",
    ":!SECURITY.md"
)

$secretFindings = @()
foreach ($pattern in $secretPatterns) {
    $matches = @(git grep -n -I -E $pattern -- . $grepExcludes 2>$null)
    if ($LASTEXITCODE -eq 0) {
        $secretFindings += $matches
    } elseif ($LASTEXITCODE -gt 1) {
        Fail-Check "Secret scan failed for pattern: $pattern"
    }
}

if ($secretFindings.Count -gt 0) {
    Fail-Check ("Potential secret material found:`n" + ($secretFindings -join "`n"))
}

if ($Release) {
    Write-Step "Release signing environment check"
    $requiredEnv = @(
        "NERMUX_RELEASE_STORE_FILE",
        "NERMUX_RELEASE_KEY_ALIAS",
        "NERMUX_RELEASE_STORE_PASSWORD",
        "NERMUX_RELEASE_KEY_PASSWORD"
    )
    $missingEnv = @($requiredEnv | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
    if ($missingEnv.Count -gt 0) {
        Fail-Check ("Missing release signing environment variables:`n" + ($missingEnv -join "`n"))
    }

    $storeFile = [Environment]::GetEnvironmentVariable("NERMUX_RELEASE_STORE_FILE")
    if (-not (Test-Path -LiteralPath $storeFile)) {
        Fail-Check "NERMUX_RELEASE_STORE_FILE does not exist: $storeFile"
    }
}

if (-not $SkipGradle) {
    Write-Step "Gradle lint, tests, and debug build"
    $gradleTasks = @(
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":terminal-emulator:testDebugUnitTest",
        ":terminal-view:testDebugUnitTest",
        ":termux-shared:testDebugUnitTest",
        ":app:assembleDebug"
    )

    if ($Release) {
        $gradleTasks += ":app:assembleRelease"
    }

    & .\gradlew.bat @gradleTasks
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

Write-Step "Debug APK SHA-256"
$apkDir = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug"
if (Test-Path -LiteralPath $apkDir) {
    Get-ChildItem -LiteralPath $apkDir -Filter "*.apk" |
        Sort-Object Name |
        ForEach-Object {
            $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName
            "{0}  {1}" -f $hash.Hash.ToLowerInvariant(), $_.FullName
        }
} else {
    Write-Host "No debug APK directory found yet."
}

Write-Step "Release check complete"
