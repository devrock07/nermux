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

## Upstream

Nermux is a clone/fork based on Termux. Upstream projects:

- [termux/termux-app](https://github.com/termux/termux-app)
- [termux/termux-packages](https://github.com/termux/termux-packages)

Respect upstream licenses and attribution when publishing this fork.

## License

This project follows the upstream Termux app license. See [LICENSE.md](LICENSE.md).
