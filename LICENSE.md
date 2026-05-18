# Nermux License

SPDX-License-Identifier: GPL-3.0-only

Nermux is a fork of [`termux/termux-app`](https://github.com/termux/termux-app).

The app/root project is distributed under the **GNU General Public License
version 3 only (GPLv3-only)**, matching the upstream Termux app license.
Nermux-specific changes to GPL-covered app code are distributed under the same
GPLv3-only terms.

Original upstream Termux code remains copyright of the original Termux authors
and contributors. Nermux-specific modifications remain copyright of their
respective Nermux contributors.

## Fork Notice

Nermux is not the upstream Termux project. It is a Termux-powered fork focused
on a cleaner blue app UI, improved session controls, haptics, workspace/file
editing flows, and local-development helper features.

The Android package id may still be `com.termux` while the fork depends on the
existing Termux bootstrap/package ecosystem. This runtime compatibility detail
does not change the license or upstream attribution.

## Exceptions

- The `terminal-view` and `terminal-emulator` libraries include code from
  [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator),
  which is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
- The `termux-shared` library has its own license notes and exceptions. See
  [`termux-shared/LICENSE.md`](termux-shared/LICENSE.md).

## Upstream

- Upstream app source: [`termux/termux-app`](https://github.com/termux/termux-app)
- Upstream package source: [`termux/termux-packages`](https://github.com/termux/termux-packages)

When publishing or redistributing Nermux, keep this license file, upstream
credit, and all applicable notices intact.
