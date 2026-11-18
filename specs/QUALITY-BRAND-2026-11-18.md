# 上传方式名称与移动端图标统一

版本：Android `2.0.0-alpha.13`（13）/ iOS `0.2.36`（40）。应用代码 `77f2b69bd418c1f1c6a7a13d3c6c63fcb1d4b91a`。

两端按以下顺序展示上传方式：

1. 原始画质（不占用配额）
2. 节省空间（不占用配额）
3. 原始画质（占用配额）

备份偏好、确认摘要、详情以及保留的旧版入口使用相同名称。持久化的 original/saver/quota 标识和实际上传参数不变，不修改现有账号设置或已排队任务。

备注采用：

> 元信息会随文件上传，云端处理可能调整部分信息。节省空间由设备或 Google 相册处理，不使用第三方图片处理服务。此处仅选择上传方式，实际配额占用以 Google 相册显示为准。大量备份前，建议先少量测试。

未承诺云端处理后每项元信息都完整不变；未承诺网络不经过代理/CDN，明确的是不使用第三方图片处理服务。配额名称表示所选上传方式，实际计费结果以 Google 相册为准。

两端启动器图标与关于页复用桌面版 `src/PicHarbor.Gui/Assets/PicHarbor.png`，共享原图位于 `assets/brand/PicHarbor.png`，逐字节一致，来源与哈希在 `SOURCE.json` 中。Android 包含原生自适应图标及传统多密度资源；iOS 的 1024×1024 AppIcon 按系统要求不含透明通道，只将原图透明边角补为同色渐变，系统自行裁切圆角。通知栏的小图标仍使用平台要求的任务状态图形。

重新生成图标可运行 `python tools/generate-mobile-icons.py`，仅生成时需要 Pillow，构建直接使用已提交的资源，不依赖桌面仓库。已核对不透明图案像素与桌面原图一致，APK 包含同一源图。

Android 中文源码字符串 672 项无翻译遗漏；iOS 本地化检查 3 项通过。两端共享核心与网络协议未修改。用户要求只编译、不安装，本轮继续遵循，未进行手机桌面图标或系统裁切的实机验收。

Android release、lintVital、固定发布证书与 APK 内容检查通过。iOS [44319594151](https://github.com/Cc-Cece/PicHarbor-Mobile/actions/runs/44319594151) 编译、打包及内容检查通过，IPA 未签名。交付目录 `delivery/quality-brand-2026-11-18/`，包括 APK、IPA、源码 ZIP、`MANIFEST.json` 与 `SHA256SUMS.txt`。
