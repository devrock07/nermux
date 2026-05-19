#!/usr/bin/env bash
set -euo pipefail

skip_gradle=0
release=0

for arg in "$@"; do
  case "$arg" in
    --skip-gradle)
      skip_gradle=1
      ;;
    --release)
      release=1
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 2
      ;;
  esac
done

step() {
  printf '\n== %s ==\n' "$1"
}

step "Git whitespace check"
git diff --check

step "Tracked artifact and signing key check"
forbidden_patterns=(
  '*.apk'
  '*.aab'
  '*.ap_'
  '*.aar'
  '*.dex'
  '*.idsig'
  '*.so'
  '*.jks'
  '*.keystore'
  '*.p12'
  '*.pem'
  '*.key'
  '*.mobileprovision'
  'local.properties'
  'app/src/main/cpp/bootstrap-*.zip'
)

tracked_forbidden="$(git ls-files -- "${forbidden_patterns[@]}")"
if [[ -n "$tracked_forbidden" ]]; then
  echo "Do not publish generated artifacts, SDK paths, or signing material:" >&2
  echo "$tracked_forbidden" >&2
  exit 1
fi

step "Secret pattern check"
secret_patterns=(
  'BEGIN (RSA|DSA|EC|OPENSSH|PRIVATE) KEY'
  'discord(app)?\.com/api/webhooks/'
  'ghp_[A-Za-z0-9_]{30,}'
  'github_pat_[A-Za-z0-9_]{30,}'
  'AKIA[0-9A-Z]{16}'
  'AIza[0-9A-Za-z_-]{30,}'
  'xox[baprs]-[0-9A-Za-z-]{20,}'
  'sk-[A-Za-z0-9]{30,}'
)

secret_findings=""
for pattern in "${secret_patterns[@]}"; do
  set +e
  matches="$(git grep -n -I -E "$pattern" -- . \
    ':!scripts/nermux-release-check.ps1' \
    ':!scripts/nermux-release-check.sh' \
    ':!docs/SECURITY_RELEASE.md' \
    ':!SECURITY.md')"
  status=$?
  set -e

  if [[ "$status" -eq 0 ]]; then
    secret_findings+="${matches}"$'\n'
  elif [[ "$status" -gt 1 ]]; then
    echo "Secret scan failed for pattern: $pattern" >&2
    exit "$status"
  fi
done

if [[ -n "$secret_findings" ]]; then
  echo "Potential secret material found:" >&2
  echo "$secret_findings" >&2
  exit 1
fi

if [[ "$release" -eq 1 ]]; then
  step "Release signing environment check"
  required_env=(
    NERMUX_RELEASE_STORE_FILE
    NERMUX_RELEASE_KEY_ALIAS
    NERMUX_RELEASE_STORE_PASSWORD
    NERMUX_RELEASE_KEY_PASSWORD
  )

  for name in "${required_env[@]}"; do
    if [[ -z "${!name:-}" ]]; then
      echo "Missing release signing environment variable: $name" >&2
      exit 1
    fi
  done

  if [[ ! -f "$NERMUX_RELEASE_STORE_FILE" ]]; then
    echo "NERMUX_RELEASE_STORE_FILE does not exist: $NERMUX_RELEASE_STORE_FILE" >&2
    exit 1
  fi
fi

if [[ "$skip_gradle" -eq 0 ]]; then
  step "Gradle lint, tests, and debug build"
  gradle_tasks=(
    ':app:lintDebug'
    ':app:testDebugUnitTest'
    ':terminal-emulator:testDebugUnitTest'
    ':terminal-view:testDebugUnitTest'
    ':termux-shared:testDebugUnitTest'
    ':app:assembleDebug'
  )

  if [[ "$release" -eq 1 ]]; then
    gradle_tasks+=(':app:assembleRelease')
  fi

  ./gradlew "${gradle_tasks[@]}"
fi

step "Debug APK SHA-256"
apk_dir="app/build/outputs/apk/debug"
if [[ -d "$apk_dir" ]]; then
  find "$apk_dir" -maxdepth 1 -name '*.apk' -print0 | sort -z | xargs -0 sha256sum
else
  echo "No debug APK directory found yet."
fi

step "Release check complete"
