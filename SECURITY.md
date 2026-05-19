# Security Policy

Report security issues privately through the repository security advisory flow after this fork is published on GitHub. If advisories are not available yet, open a minimal issue asking for a private contact channel and do not include exploit details publicly.

## Supported Builds

Security fixes are intended for the current public Nermux release and the active development branch.

Nermux is based on Termux, so inherited security behavior and upstream package issues may still be relevant:

- https://termux.dev/security
- https://github.com/termux/termux-app
- https://github.com/termux/termux-packages

## Release Safety

Before publishing an APK, run the release/security checks:

```bash
bash scripts/nermux-release-check.sh
```

On Windows:

```powershell
.\scripts\nermux-release-check.ps1
```

For signed release builds, set these environment variables before running with release mode:

```text
NERMUX_RELEASE_STORE_FILE
NERMUX_RELEASE_KEY_ALIAS
NERMUX_RELEASE_STORE_PASSWORD
NERMUX_RELEASE_KEY_PASSWORD
```

Never commit release keystores, passwords, APKs, AABs, SDK paths, bootstrap zips, or private tokens.

## Antivirus Notes

Nermux is a terminal app with a Linux userspace. Some scanners may treat terminals, package managers, all-files access, or app-install permissions as higher risk. That does not mean every warning is valid, but each public release should provide source code, reproducible build notes, and SHA-256 hashes so users can verify what they installed.
