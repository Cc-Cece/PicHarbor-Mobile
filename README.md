# PicHarbor Mobile

Native Android (Compose) and iOS (SwiftUI), maintained together. Shared business logic remains in [gpmc](https://github.com/Cc-Cece/gpmc).

The product and page structure follow the frozen iOS `e0c495c9b23dba5b40eaff7e01b3acd597847096` baseline. See [the page contract](specs/PAGE-CONTRACT.md) and [migration record](specs/REPOSITORY-MIGRATION.md).

Build Android from `android/` with `tools/build-release.ps1`; persistent signing material stays outside this repository. Build iOS with the root unsigned-IPA workflow. Functional tests and phone UI interaction are not executed during this delivery at the user's request.

## Development

- Offline source checks: `python tools/sync-core.py`.
- Update both snapshots from the sibling canonical repository: `python tools/sync-core.py --source ../gpmc` (run from `mobile/`).
- Windows signed APK: run `powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-release.ps1` from `android/`.
- Root GitHub workflows build Android Debug and unsigned iOS IPA independently, without tests or uploaded private keys. Nested historical workflows are retained for provenance and are not active workflows in this repository.
- The existing 30-minute device task is moved with `tools/register-device-preview.ps1`; signing paths and mutex stay unchanged. It installs only, never launches or clears the app.
- `python tools/package-mobile.py` creates the committed source archive with the pinned GPL proxy sources. No local credentials, signing keys or build caches are included.

页面方案已经用户确认，见 [左右对照预览](specs/preview/mobile-page-comparison.html) 和 [确认后的调整](specs/preview/REVIEW-NOTES.md)。预览是设计依据，不是应用截图或功能验证。
