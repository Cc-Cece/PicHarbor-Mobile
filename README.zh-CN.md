<div align="center">

<img src="assets/brand/PicHarbor.png" width="96" height="96" alt="PicHarbor 图标" />

# PicHarbor Mobile

使用原生界面，将手机里的照片、视频和实况照片备份到 Google 相册。

[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](android/)
[![iOS](https://img.shields.io/badge/iOS-17%2B-000000?logo=apple&logoColor=white)](ios/)
[![Android build](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/workflows/android-build.yml/badge.svg)](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/workflows/android-build.yml)
[![iOS build](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/workflows/ios-build.yml/badge.svg)](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/workflows/ios-build.yml)

<p><a href="README.md">English</a> | <b>简体中文</b></p>

<p><a href="https://github.com/Cc-Cece/PicHarbor-Mobile">GitHub 仓库</a> · <a href="https://github.com/Cc-Cece/PicHarbor-Mobile/actions">下载构建</a> · <a href="https://github.com/Cc-Cece/SelfRefreshKit">iOS 自动续签</a></p>

[主要功能](#主要功能) · [开始使用](#开始使用) · [iOS 自动续签](#ios-自动续签) · [构建](#构建) · [项目结构](#项目结构)

</div>

## 主要功能

| 功能 | 说明 |
| --- | --- |
| **照片与实况备份** | 浏览照片、视频和 Live Photo，选择备份范围，按媒体资源匹配实况处理方式。 |
| **备份队列** | 查看进度与阶段，暂停、继续、重试和管理记录；任务状态持久保存。 |
| **相册归类** | 根据本机相册组织备份内容，复用已建立的云端相册映射。 |
| **应用内代理** | 导入订阅或配置，选择节点并查看连接状态。 |
| **传输到电脑** | 扫描电脑二维码，在同一局域网内发送所选照片与视频，无需 Google 账号。 |
| **原生设置与诊断** | 账号管理、外观和多语言设置，以及由用户主动导出的本地诊断日志。 |
| **iOS 自动续签** | 内置 SelfRefreshKit，完成一次电脑辅助配置后，可通过手机快捷指令自动续签。 |

主界面包含 **照片、备份、活动、设置** 四个页面。后台执行、照片权限和安装方式遵循各平台的能力与限制。

> [!NOTE]
> 项目仍处于开发阶段，并非 Google 官方客户端。上传结果、画质和配额占用以 Google 相册实际显示为准，建议先用少量照片验证。

## 开始使用

| 平台 | 设备要求 | 安装包 |
| --- | --- | --- |
| Android | Android 10+，ARM64 | APK；CI 生成带独立包名的 Debug 预览版 |
| iOS | iOS 17+，iPhone | 未签名 IPA；首次通过电脑签名安装并配置 SelfRefreshKit |

从 [Releases](https://github.com/Cc-Cece/PicHarbor-Mobile/releases) 下载已发布的 APK 和未签名 IPA，或从 [GitHub Actions](https://github.com/Cc-Cece/PicHarbor-Mobile/actions) 的成功运行中下载预览产物。CI 产物有保留期限；Android 正式发布使用既有发布密钥。

1. 安装后授予需要的照片访问权限。
2. 连接 Google 账号，确认备份范围和上传方式。
3. 先备份少量照片，在 Google 相册核对结果，再开始批量备份。

## iOS 自动续签

[查看完整配置与故障处理教程](guides/ios-auto-renewal.zh-CN.md) · [English](guides/ios-auto-renewal.md)

PicHarbor 内置 [SelfRefreshKit](https://github.com/Cc-Cece/SelfRefreshKit)。**首次连接电脑完成安装和配对后，日常续签可在 iPhone 上自动完成，无需每周连接电脑。**

1. 使用 [SelfRefreshSetup](https://github.com/Cc-Cece/SelfRefreshKit/tree/master/tools/SelfRefreshSetup) 在电脑上签名安装 IPA，并写入配对记录；工具可从 [SelfRefreshKit Releases](https://github.com/Cc-Cece/SelfRefreshKit/releases) 下载。
2. 在 PicHarbor 设置中的续签入口完成「必需操作」，确认设备配对、Apple 账号和本机续签设置。
3. 安装并连接 LocalDevVPN，先手动续签一次，检查有效期是否更新。
4. 在系统快捷指令中配置自动化：连接 LocalDevVPN → 运行 PicHarbor 的「刷新应用签名描述文件」→ 断开 LocalDevVPN。

> [!TIP]
> 建议每 5 天自动续签，并选择「立即运行」。从上次成功续签起，间隔不能超过 7 天，否则需要重新连接电脑恢复签名安装。续签需要网络、可用的 Apple 会话和本地回环 VPN，请定期检查最近续签结果。

## 构建

### 共享核心

两端已包含固定版本的核心快照，普通构建无需另外克隆 gpmc。先在仓库根目录检查来源和内容一致性：

```shell
python tools/sync-core.py
```

需要升级核心时，先在独立 gpmc 仓库完成修改，再显式同步两端；不要直接修改客户端中的第三方快照。

```shell
python tools/sync-core.py --source ../gpmc
```

### Android

需要 JDK 17、Python 3.13、Android SDK 36 和 Go。仓库包含 Gradle Wrapper；代理核心在构建时从固定源码编译，CI 的工具链配置位于 [setup-build](android/.github/actions/setup-build/action.yml)。首次构建需要联网取得依赖。

Windows PowerShell：

```powershell
cd android
.\gradlew.bat :app:assembleDebug "-PbuildPython=$((Get-Command python).Source)"
```

输出：`android/app/build/outputs/apk/debug/app-debug.apk`。

已有发布签名环境的维护者，可在 `android/` 下运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-release.ps1 -BuildPython (Get-Command python).Source
```

发布脚本使用工作区外保存的既有密钥，并核对固定签名证书。源码仓库不包含私钥或签名密码；普通开发使用 Debug 构建。

### iOS

可使用根目录的 [iOS Build (Unsigned IPA)](.github/workflows/ios-build.yml) 工作流。它准备固定的 Python 和代理运行时，使用 XcodeGen 生成工程，再构建未签名 IPA；无需将个人签名证书上传到 CI。

在 macOS 本地构建时，需要 Xcode、XcodeGen 和 Python 3.13。CI 当前使用的 Xcode 版本以工作流配置为准。

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

该命令输出 `.app`；IPA 打包步骤见同一工作流。

### 标签自动发布

在已包含于 `main` 的提交上推送版本标签，即可构建并发布两端：

```shell
git switch main
git pull --ff-only
git tag v1.0.0
git push origin v1.0.0
```

每次发布使用新版本号。`v1.0.0-beta.1` 这类标签会发布为预发布版。合并发布标签独立于 Android 和 iOS 各自的应用版本，原生版本会写入 Release 说明及清单。

[Mobile Release](.github/workflows/release.yml) 工作流验证提交属于 main，构建并检查签名 ARM64 APK 和未签名 IPA，全部成功后统一发布，并附上对应源码、`MANIFEST.json` 和 `SHA256SUMS.txt`。已有 Release 不覆盖。源码包排除本地项目文档，包含固定版本的 GPL 代理源码。

Android 发布需要仓库 Secrets：`PICHARBOR_MOBILE_KEYSTORE_BASE64`、`PICHARBOR_MOBILE_STORE_PASSWORD`、`PICHARBOR_MOBILE_KEY_PASSWORD`；使用既有密钥别名 `picharbor` 和固定证书。iOS CI 不需要上传签名证书。

验证构建时，手动运行 **Mobile Release** 并保持 **publish** 关闭；仅上传校验后的产物，不创建标签或 Release，仍需已有的发布签名 Secrets。

## 项目结构

```text
mobile/
├─ android/       Compose 界面、Android 系统适配与构建工具
├─ ios/           SwiftUI 界面、PhotoKit、原生传输与嵌入式 Python
├─ assets/brand/  两端共用图标及来源记录
├─ tools/         核心同步、图标生成、CI 下载和源码打包
└─ .github/       两端的有效 CI 工作流
```

相关项目：[PicHarbor 桌面版](https://github.com/Cc-Cece/PicHarbor) · [gpmc 共享核心](https://github.com/Cc-Cece/gpmc)
