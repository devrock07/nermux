# Nermux Release and Security Checklist

Use this before uploading public APKs.

## Required Checks

Run the local release check:

```bash
bash scripts/nermux-release-check.sh
```

Windows:

```powershell
.\scripts\nermux-release-check.ps1
```

This checks:

- Git whitespace problems
- Accidentally tracked APKs, AABs, bootstrap zips, local SDK paths, or signing keys
- Common secret patterns such as private keys, GitHub tokens, Discord webhooks, Slack tokens, AWS keys, Google API keys, and OpenAI-style keys
- Android lint
- Unit tests
- Debug APK build
- SHA-256 hashes for built debug APKs

For release signing, set:

```text
NERMUX_RELEASE_STORE_FILE
NERMUX_RELEASE_KEY_ALIAS
NERMUX_RELEASE_STORE_PASSWORD
NERMUX_RELEASE_KEY_PASSWORD
```

Then run:

```bash
bash scripts/nermux-release-check.sh --release
```

## Public APK Trust

For every public release:

- Publish APK SHA-256 hashes next to the APKs.
- Sign release APKs with the same private release key every time.
- Do not publish debug APKs as official stable releases.
- Keep release notes clear about the package id currently remaining `com.termux`.
- Keep source code for the exact release tag public.

## Permission Notes

Nermux inherits powerful terminal-app permissions from Termux. They should stay documented because they can look scary in scanner reports:

- `INTERNET` and `ACCESS_NETWORK_STATE` are required for package downloads and terminal networking.
- Storage permissions and all-files access support editing and running projects from shared storage.
- `WAKE_LOCK` and battery optimization access support long-running shells, servers, bots, SSH sessions, and local dev tasks.
- `SYSTEM_ALERT_WINDOW` supports terminal overlay/display workflows inherited from Termux shell helpers.
- `REQUEST_INSTALL_PACKAGES` supports package/APK install flows launched from the terminal.
- `READ_LOGS`, `DUMP`, `WRITE_SECURE_SETTINGS`, and `PACKAGE_USAGE_STATS` are protected Android permissions. Normal installs do not silently grant them; advanced users may grant them through ADB/root for diagnostic workflows.

## Network and Backup Hardening

The Android app layer is configured to:

- Block cleartext HTTP traffic for app-owned networking.
- Trust system certificate authorities only for app-owned TLS.
- Disable Android backup and data extraction for app data.

This does not restrict user commands inside the terminal. Users can still run their own network tools and servers from the shell.
