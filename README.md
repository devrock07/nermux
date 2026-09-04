> [!WARNING]
> **This project has been archived.**
>
> Active development, improvements, bug fixes, and feature updates have been discontinued.
>
> The project will remain publicly available, and you are **free to use, fork, and modify it** according to the project's license. However, no further updates or official support are planned.

# Nermux

Nermux is a Termux-powered Android terminal clone/fork based on [Termux](https://github.com/termux/termux-app).

The goal is to keep the power of Termux while making the app easier and cleaner for normal users: a modern blue UI, better session controls, haptics, and a built-in workspace/file editor flow inspired by VS Code.

## Current Status

This fork is in active early development.

- App-facing branding has been changed to **Nermux**.
- The terminal UI has been redesigned with a blue glass-style theme.
- Sessions now have cleaner cards and close controls.
- A native **Workspace** screen has been added for browsing, editing, creating, renaming, deleting, and running files.
- Android's system folder picker can be used to select shared-storage project folders.
- About, Donate, and startup info now use Nermux copy instead of upstream Termux copy.
- The Android package id is still `com.termux`.

The package id is intentionally still `com.termux` for now because Termux bootstrap binaries and packages are compiled for:

```text
/data/data/com.termux/files/usr
```

Changing the package id to `com.nermux` without rebuilding bootstrap packages would break the runtime environment. A full package-id migration requires rebuilding the bootstrap and package ecosystem for the new `$PREFIX`.

## Features

- Android terminal emulator and Linux userspace environment
- Multiple terminal sessions
- Blue Nermux UI theme
- Haptic feedback on common controls
- Session drawer with close buttons
- Pin/unpin session protection from the session long-press menu
- Built-in workspace/file explorer
- Quick text editor for small text files
- Create, rename, and delete files/folders
- Open a terminal in the current folder
- Run selected files in a terminal session
- Android folder picker support for shared-storage projects
- Power Center for dev stacks, SSH profiles, port checks, backups, exports, and wake-lock controls
- Safer paste guard for multi-line or risky clipboard commands
- Nermux about/donate screens
- Custom Nermux startup message for fresh/default bootstrap installs

## Donate

Nermux is an independent fork project. If you want to support the work, Litecoin donations can be sent to:

```text
LcPnFkTa5UTav5Ue3dM6GdLh7LpTm47JZx
```

Keep this address visible in the app and README so GitHub visitors and app users know where to support the fork.

## Project Layout

```text
app/                Android app, activities, services, UI resources
terminal-emulator/  Terminal session, PTY, xterm/vt emulation
terminal-view/      Android terminal rendering and input view
termux-shared/      Shared constants, utilities, shell helpers
docs/               Documentation site content
fastlane/           Android release metadata
gradle/             Gradle wrapper support files
```

## Build Requirements

- Android Studio or Android SDK
- JDK 17 or newer
- Android SDK platform configured locally
- Android NDK installed through the SDK manager

On Windows, this workspace was built with Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
```

## Build

Compile Java:

```bash
./gradlew :app:compileDebugJavaWithJavac
```

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest
```

Build debug APKs:

```bash
./gradlew :app:assembleDebug
```

Debug APKs are written to:

```text
app/build/outputs/apk/debug/
```

The universal debug APK is usually:

```text
app/build/outputs/apk/debug/nermux-app_apt-android-7-debug_universal.apk
```

## Release and Security Checks

Before publishing the repo or APKs, run:

```powershell
.\scripts\nermux-release-check.ps1
```

Linux/macOS/GitHub Actions:

```bash
bash scripts/nermux-release-check.sh
```

The check blocks common release mistakes: tracked APKs, AABs, signing keys, local SDK paths, bootstrap zips, common secret/token patterns, lint failures, test failures, and broken debug builds. It also prints SHA-256 hashes for built APKs.

For a signed release build, keep the keystore outside the repo and set:

```text
NERMUX_RELEASE_STORE_FILE
NERMUX_RELEASE_KEY_ALIAS
NERMUX_RELEASE_STORE_PASSWORD
NERMUX_RELEASE_KEY_PASSWORD
```

Then run:

```powershell
.\scripts\nermux-release-check.ps1 -Release
```

See [docs/SECURITY_RELEASE.md](docs/SECURITY_RELEASE.md) before uploading a public APK.

## GitHub APK Downloads

The workflow [publish_latest_apk.yml](.github/workflows/publish_latest_apk.yml) builds signed release APKs automatically after [release_security.yml](.github/workflows/release_security.yml) passes on `main` or `master`. It can also be started manually from GitHub Actions.

It creates or updates the GitHub Releases page entry tagged:

```text
nermux-latest
```

That release is explicitly marked as the latest release and contains universal, arm64, arm, x86_64, and x86 signed release APKs plus SHA-256 hashes. Most users should download the universal APK.

The repo home page will show no release until the publish workflow finishes successfully. If the release does not appear, check the **Publish Latest Signed APK** workflow run and confirm the release signing secrets and Actions write permissions are configured.

After a successful workflow run, users can download the APK from:

```text
https://github.com/<owner>/<repo>/releases/tag/nermux-latest
```

Repository Actions must allow `GITHUB_TOKEN` write access to contents so the workflow can create the tag, update the release, and upload APK assets.

Add these repository secrets before relying on the workflow:

```text
NERMUX_RELEASE_KEYSTORE_BASE64
NERMUX_RELEASE_KEY_ALIAS
NERMUX_RELEASE_STORE_PASSWORD
NERMUX_RELEASE_KEY_PASSWORD
```

Generate `NERMUX_RELEASE_KEYSTORE_BASE64` from your private keystore without committing the keystore:

```bash
base64 -w 0 nermux-release.jks
```

The public release manifest removes protected/high-risk permissions that normal users do not need, including all-files access, overlay access, log/dump/secure-settings access, package-usage access, and APK-install permission. Debug builds keep the fuller development permission set.

Play Protect may still warn about sideloaded or uncommon terminal apps. Use a consistent private signing key, publish SHA-256 hashes, keep source visible, and appeal incorrect Play Protect classifications after checking Google's Play Protect guidance.

## Install Debug APK

```bash
adb install -r app/build/outputs/apk/debug/nermux-app_apt-android-7-debug_universal.apk
```

If Android reports a signature or shared user incompatibility, uninstall existing Termux/Nermux builds first. Back up important `$HOME` files before uninstalling.

## Development Notes

- Do not commit Gradle build outputs, APKs, local SDK paths, bootstrap zips, screenshots, or signing keys.
- Keep `applicationId` as `com.termux` until the bootstrap/package migration is ready.
- Be careful changing paths under `TermuxConstants`; many runtime paths are tied to the bootstrap.
- The workspace editor is a lightweight native editor, not a full VS Code engine.
- Public APKs should be signed with a private release key and published with SHA-256 hashes.
- Some antivirus scanners may flag terminal/package-manager permissions. Keep source, release notes, permission explanations, and hashes public so users can verify the build.

## Upstream

Nermux is a clone/fork based on Termux. Upstream projects:

- [termux/termux-app](https://github.com/termux/termux-app)
- [termux/termux-packages](https://github.com/termux/termux-packages)

Respect upstream licenses and attribution when publishing this fork.

## License

Nermux follows the upstream Termux app license: **GPLv3-only** for the app/root project, with documented exceptions for bundled/shared libraries.

Original Termux copyright and attribution remain intact. Nermux-specific changes are distributed under the same GPLv3-only terms where they modify GPL-covered app code.

See [LICENSE.md](LICENSE.md) and [termux-shared/LICENSE.md](termux-shared/LICENSE.md).
