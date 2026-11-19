<div align="center">

<img src="assets/brand/PicHarbor.png" width="96" height="96" alt="PicHarbor logo" />

# PicHarbor Mobile

Back up photos, videos, and Live Photos to Google Photos with a native mobile experience.

[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](android/)
[![iOS](https://img.shields.io/badge/iOS-17%2B-000000?logo=apple&logoColor=white)](ios/)
[![Android build](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/workflows/android-build.yml/badge.svg)](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/workflows/android-build.yml)
[![iOS build](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/workflows/ios-build.yml/badge.svg)](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/workflows/ios-build.yml)

<p><b>English</b> | <a href="README.zh-CN.md">简体中文</a></p>

<p><a href="https://github.com/Cc-Cece/PicHarbor-Mobile">GitHub Repository</a> · <a href="https://github.com/Cc-Cece/PicHarbor-Mobile/actions">Download Builds</a> · <a href="https://github.com/Cc-Cece/SelfRefreshKit">iOS Auto-Renewal</a></p>

[Features](#features) · [Quick Start](#quick-start) · [iOS Auto-Renewal](#ios-auto-renewal) · [Build](#build) · [Project Structure](#project-structure)

</div>

## Features

| Capability | Description |
| --- | --- |
| **Photo & Live Photo Backup** | Browse photos, videos, and Live Photos, select a backup scope, and match Live Photo handling to the available resources. |
| **Backup Queue** | Track progress and processing stages; pause, resume, retry, and manage persisted task records. |
| **Album Organization** | Organize uploads by local album and reuse existing cloud album mappings. |
| **In-App Proxy** | Import subscriptions or configurations, select nodes, and check connection status. |
| **Transfer to Computer** | Scan a computer's QR code and send selected media over the same local network, without a Google account. |
| **Native Settings & Diagnostics** | Manage accounts, appearance, and languages, and export local diagnostic logs when needed. |
| **iOS Auto-Renewal** | Built-in SelfRefreshKit enables automatic on-device renewal through Shortcuts after a one-time computer-assisted setup. |

The app has four main tabs: **Photos, Backup, Activity, and Settings**. Background execution, photo permissions, and installation follow each platform's capabilities and restrictions.

> [!NOTE]
> This project is under development and is not an official Google client. Verify upload results, quality, and quota usage in Google Photos with a small batch before starting a large backup.

## Quick Start

The **More** page contains publisher-signed tools, initially the official community and feedback. Diagnostics are in **Settings → About**. The service receives a random installation ID and coarse usage counts, without photo names, Google credentials or full Google email addresses. Feedback may include a locally masked account hint and attachments you select; screenshots are re-encoded and diagnostic archives are sanitized before upload. Remote configuration can apply a preference once or keep it locked until withdrawn.

| Platform | Requirements | Package |
| --- | --- | --- |
| Android | Android 10+, ARM64 | APK; CI produces a Debug preview with a separate application ID |
| iOS | iOS 17+, iPhone | Unsigned IPA; use a computer for the initial signing, installation, and SelfRefreshKit setup |

Download published APKs and unsigned IPAs from [Releases](https://github.com/Cc-Cece/PicHarbor-Mobile/releases), or preview artifacts from a successful [GitHub Actions](https://github.com/Cc-Cece/PicHarbor-Mobile/actions) run. CI artifacts expire after their retention period. Android releases use the existing production signing key.

1. Install the app and grant the photo access you need.
2. Connect your Google account and choose the backup scope and upload method.
3. Back up a few photos, verify them in Google Photos, then proceed with larger batches.

## iOS Auto-Renewal

[Read the full setup and troubleshooting guide](guides/ios-auto-renewal.md) · [简体中文](guides/ios-auto-renewal.zh-CN.md)

PicHarbor integrates [SelfRefreshKit](https://github.com/Cc-Cece/SelfRefreshKit). **Connect to a computer once for installation and pairing; subsequent routine renewals can run automatically on your iPhone without weekly computer connections.**

1. Use [SelfRefreshSetup](https://github.com/Cc-Cece/SelfRefreshKit/tree/master/tools/SelfRefreshSetup) to sign and install the IPA and write the pairing records. Download the tool from [SelfRefreshKit Releases](https://github.com/Cc-Cece/SelfRefreshKit/releases).
2. Open the renewal entry in PicHarbor Settings and complete Required Setup: device pairing, Apple account authentication, and local renewal setup.
3. Install and connect LocalDevVPN, then run one manual renewal and confirm that the expiration date advances.
4. Create a Shortcuts automation: connect LocalDevVPN → run PicHarbor's Refresh App Provisioning Profile action → disconnect LocalDevVPN.

> [!TIP]
> Renew every 5 days with Run Immediately enabled. Do not exceed 7 days since the last successful renewal; otherwise, reconnect to a computer to restore signing and installation. Renewal requires network access, a valid Apple session, and the local loopback VPN. Check the latest renewal result periodically.

## Build

### Shared Core

Both clients include a pinned core snapshot; ordinary builds do not require a separate gpmc checkout. Check provenance and snapshot consistency from the repository root:

```shell
python tools/sync-core.py
```

To update the core, make changes in the separate gpmc repository, then explicitly synchronize both clients instead of editing their vendored snapshots:

```shell
python tools/sync-core.py --source ../gpmc
```

### Android

Requires JDK 17, Python 3.13, Android SDK 36, and Go. The repository includes the Gradle Wrapper. The proxy core is built from pinned source; see the CI toolchain in [setup-build](android/.github/actions/setup-build/action.yml). The first build needs network access to fetch dependencies.

Windows PowerShell:

```powershell
cd android
.\gradlew.bat :app:assembleDebug "-PbuildPython=$((Get-Command python).Source)"
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`.

Maintainers with the existing release signing environment can run this from `android/`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-release.ps1 -BuildPython (Get-Command python).Source
```

The release script uses the existing key stored outside the workspace and checks its pinned certificate. Private keys and signing passwords are not included in the repository. Use Debug builds for ordinary development.

### iOS

Use the root [iOS Build (Unsigned IPA)](.github/workflows/ios-build.yml) workflow to prepare pinned Python and proxy runtimes, generate the project with XcodeGen, and build an unsigned IPA. Personal signing certificates are not required in CI.

Local macOS builds require Xcode, XcodeGen, and Python 3.13. The workflow defines the Xcode version currently used by CI.

```shell
cd ios
python3 tools/prepare_python.py
python3 tools/prepare_proxy.py
xcodegen generate
xcodebuild -project PicHarbor.xcodeproj -scheme PicHarbor \
  -configuration Release -sdk iphoneos \
  -destination 'generic/platform=iOS' -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
```

This produces an `.app`; see the same workflow for IPA packaging.

### Tag-Based Releases

Push a version tag on a commit already included in `main` to build and publish both platforms:

```shell
git switch main
git pull --ff-only
git tag v1.0.0
git push origin v1.0.0
```

Use a new version for each release. Tags such as `v1.0.0-beta.1` produce prereleases. The combined release tag is independent of the native Android and iOS version numbers, which are recorded in the Release notes and manifest.

The [Mobile Release](.github/workflows/release.yml) workflow checks main ancestry, builds and verifies a signed ARM64 APK and an unsigned IPA, then publishes both together with matching source, `MANIFEST.json`, and `SHA256SUMS.txt`. Existing releases are not overwritten. The source package excludes local project documents and includes the pinned GPL proxy source.

Android signing requires repository Secrets `PICHARBOR_MOBILE_KEYSTORE_BASE64`, `PICHARBOR_MOBILE_STORE_PASSWORD`, and `PICHARBOR_MOBILE_KEY_PASSWORD`, using the existing keystore alias `picharbor` and pinned certificate. iOS does not require a signing certificate in CI.

For a build-only check, run **Mobile Release** manually with **publish** disabled. It uploads verified artifacts without creating a tag or Release; release signing Secrets are still required.

## Project Structure

```text
mobile/
├─ android/       Compose UI, Android integration, and build tools
├─ ios/           SwiftUI, PhotoKit, native transfers, and embedded Python
├─ assets/brand/  Shared artwork and provenance
├─ tools/         Core synchronization, icons, CI downloads, and source packaging
└─ .github/       Active workflows for both platforms
```

Related projects: [PicHarbor Desktop](https://github.com/Cc-Cece/PicHarbor) · [gpmc Shared Core](https://github.com/Cc-Cece/gpmc)
