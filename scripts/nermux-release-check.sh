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
  chmod +x ./gradlew

  gradle_tasks=(
    ':app:lintDebug'
    ':app:processDebugMainManifest'
    ':app:processReleaseMainManifest'
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

if [[ "$skip_gradle" -eq 0 ]]; then
  step "Merged manifest hardening check"

  check_merged_manifest() {
    local variant="$1"
    local variant_title
    case "$variant" in
      debug) variant_title="Debug" ;;
      release) variant_title="Release" ;;
      *) echo "Unknown manifest variant: $variant" >&2; exit 2 ;;
    esac

    local manifest="app/build/intermediates/merged_manifest/$variant/process${variant_title}MainManifest/AndroidManifest.xml"
    if [[ ! -f "$manifest" ]]; then
      echo "Missing merged manifest: $manifest" >&2
      exit 1
    fi

    python3 - "$manifest" "$variant" <<'PY'
import sys
import xml.etree.ElementTree as ET

manifest_path, variant = sys.argv[1], sys.argv[2]
android = "{http://schemas.android.com/apk/res/android}"

def fail(message):
    print(f"{manifest_path}: {message}", file=sys.stderr)
    sys.exit(1)

root = ET.parse(manifest_path).getroot()
attr = lambda node, name: node.get(android + name) if node is not None else None

forbidden_permissions = {
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.READ_LOGS",
    "android.permission.DUMP",
    "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.PACKAGE_USAGE_STATS",
    "com.android.alarm.permission.SET_ALARM",
}

permissions = {attr(node, "name") for node in root.findall("uses-permission")}
found_permissions = sorted(forbidden_permissions & permissions)
if found_permissions:
    fail(f"forbidden permissions present in {variant}: {', '.join(found_permissions)}")

if attr(root, "sharedUserId"):
    fail(f"sharedUserId must not be present in {variant}")

application = root.find("application")
if application is None:
    fail(f"application node missing in {variant}")

for receiver_name in {
    "androidx.profileinstaller.ProfileInstallReceiver",
    "com.termux.app.event.SystemEventReceiver",
}:
    if any(attr(receiver, "name") == receiver_name for receiver in application.findall("receiver")):
        fail(f"{receiver_name} must not be present in {variant}")

for provider in application.findall("provider"):
    provider_name = attr(provider, "name")
    if provider_name == "com.termux.app.TermuxOpenReceiver$ContentProvider" and attr(provider, "exported") != "false":
        fail(f"{provider_name} must not be exported in {variant}")
for service in application.findall("service"):
    if attr(service, "name") == "com.termux.app.RunCommandService" and attr(service, "exported") != "false":
        fail(f"RunCommandService must not be exported in {variant}")
PY
  }

  check_merged_manifest debug
  check_merged_manifest release
fi

step "Debug APK SHA-256"
apk_dir="app/build/outputs/apk/debug"
if [[ -d "$apk_dir" ]]; then
  find "$apk_dir" -maxdepth 1 -name '*.apk' -print0 | sort -z | xargs -0 sha256sum
else
  echo "No debug APK directory found yet."
fi

step "Release check complete"
