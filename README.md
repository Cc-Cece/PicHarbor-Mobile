# PicHarbor Mobile

Native Android (Compose) and iOS (SwiftUI), maintained together. Shared business logic remains in [gpmc](https://github.com/Cc-Cece/gpmc).

The product and page structure follow the frozen iOS `e0c495c9b23dba5b40eaff7e01b3acd597847096` baseline. See [the page contract](specs/PAGE-CONTRACT.md) and [migration record](specs/REPOSITORY-MIGRATION.md).

本轮交付及构建安装记录见 [DELIVERY-2026-11-18.md](specs/DELIVERY-2026-11-18.md)。代码位于 `feat/mobile-framework-parity`，用户实机验收独立记录。

Android 原生体验优化基线为 `2.0.0-alpha.4`，见 [优化记录](specs/ANDROID-NATIVE-EXPERIENCE-2026-11-18.md)；保留已确认的页面框架。该轮按用户要求只编译，自动安装任务已停用。

Android 代理修复版本 `2.0.0-alpha.5` 解决带空格的代理节点名称测速失败及后续连接不可用，见 [修复和实机验证记录](specs/PROXY-FIX-2026-11-18.md)。用户已单独授权代理测试和安装；自动安装任务继续停用。

Android `2.0.0-alpha.6` 增加活动左滑操作、照片状态标志底色，并将活动菜单改为仅图标，见 [界面调整记录](specs/ANDROID-UI-ADJUSTMENTS-2026-11-18.md)。

最新版本 Android `2.0.0-alpha.7` / iOS `0.2.31` 对齐备份卡片的整体速度、剩余大小和阶段计数，并在两端展示自动备份开启状态，见 [备份卡片调整记录](specs/BACKUP-DASHBOARD-PARITY-2026-11-18.md)。

Build Android from `android/` with `tools/build-release.ps1`; persistent signing material stays outside this repository. Build iOS with the root unsigned-IPA workflow. The original framework delivery omitted functional and phone UI tests at the user's request; later explicitly authorized proxy device checks are documented separately above.

## Development

- Offline source checks: `python tools/sync-core.py`.
- Update both snapshots from the sibling canonical repository: `python tools/sync-core.py --source ../gpmc` (run from `mobile/`).
- Windows signed APK: run `powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-release.ps1` from `android/`.
- Root GitHub workflows build Android Debug and unsigned iOS IPA independently, without tests or uploaded private keys. Nested historical workflows are retained for provenance and are not active workflows in this repository.
- The existing 30-minute device installation task is disabled at the user's request. Do not register, enable or start it without a new installation instruction. Signing paths and mutex remain unchanged.
- `python tools/package-mobile.py` creates the committed source archive with the pinned GPL proxy sources. No local credentials, signing keys or build caches are included.
- Optional CI download tooling requires `python -m pip install -r tools/requirements.txt`; `python tools/download-ipa.py RUN_ID` checks the successful source run and GitHub artifact checksum.

页面方案已经用户确认，见 [左右对照预览](specs/preview/mobile-page-comparison.html) 和 [确认后的调整](specs/preview/REVIEW-NOTES.md)。预览是设计依据，不是应用截图或功能验证。
