# 已确认预览与原生实现对照

用户已确认 `preview/mobile-page-comparison.html`，包括后续三项修改。以下为源码结构核对记录，**不代表实机截图或功能测试通过**。

共同内容分区、顺序、入口与状态操作按 `PAGE-CONTRACT.md` 的 25 组、75 个检查点实现并逐组核对。四个主页面的源码结构与主要入口已对照；实际视觉一致度 ≥95% 的最终判断仍由用户实机验收，不将源码数量换算为体验结论。

| 页面组 | Android 实现 | iOS 对照 |
| --- | --- | --- |
| 照片 | ParityScreens.PhotosScreen；三项筛选、连续图库、工具栏、选择确认 | LibraryView |
| 备份 | ParityScreens.BackupScreen；概览/三项统计/操作/释放/当前设置 | HarborRootView.BackupDashboard |
| 活动 | ParityManagementScreens.ActivityScreen；筛选、缩略图、批次/单项、右上更多 | ActivityView |
| 设置 | ParityManagementScreens.UnifiedSettingsScreen；账号与四组设置，电脑入口在活动记录后 | HarborSettingsView |
| 账号 | AccountsScreen；列表、添加连接页、退出确认、账号隔离 | AccountListView |
| 连接 | ConnectionScreen；网页登录、凭据导入、忙状态、成功返回 | ConnectionView / GoogleLoginView |
| 备份偏好 | SettingsScreen；并发/画质/动态方式/版本/相簿/核验 | BackupPreferencesView |
| 高级配置 | PipelineSettingsScreen；阶段并发/缓存/执行保护与范围校验 | PipelineAdvancedView |
| 高级备份检查 | ConfirmationSettingsScreen；开关与应用等待任务 | AdvancedBackupChecksView |
| 备份范围 | FoldersScreen；本地草稿、全范围或成员、数量/策略/确认 | BackupScopeView |
| 网络后台 | NetworkScreen；Wi-Fi/自动/范围及系统状态 | NetworkPreferencesView |
| 代理 | ProxyScreen；开关/配置入口/策略/测速/节点结果 | ProxyPreferencesView |
| 配置列表 | ProxyProfilesScreen；添加/扫码/文件/已存配置 | ProxyProfilesView |
| 配置导入 | ProxyEditorScreen；名称、链接、文本与二维码选择 | ProxyProfileEditor |
| 配置详情 | ProxyProfileScreen；摘要、使用、更新、移除与节点 | ProxyProfileEditor（已保存配置状态） |
| 任务连接 | TaskProxyScreen；跟随全局或任务策略 | TaskProxyPreferencesView |
| 活动记录设置 | ArchiveSettingsScreen；归档开关、1–90 天、记录入口 | ActivityArchiveSettingsView |
| 归档 | ActivityScreen(archived)；完成记录与取消归档 | ActivityView(archived) |
| 界面 | InterfaceScreen；语言与系统/浅色/深色 | InterfacePreferencesView |
| 相册访问 | PermissionsScreen；实际授权与系统管理 | PermissionView |
| 诊断 | DiagnosticsScreen；实际文件日期、保留策略、范围导出 | MobileDiagnosticsView |
| 关于 | AboutScreen；版本、组件、许可证、统一仓库链接 | AboutView |
| 媒体详情 | DetailScreen / MediaInformationScreen；预览、文件名、详情/分享、备份与关联任务 | AssetDetailView / AssetInformationView |
| 任务与批次 | TaskDetailScreen / JobDetailsScreen；状态、流程、连接策略、操作、高级信息 | JobDetailView / ActivityBatchView |
| 电脑与释放空间 | LiveLinkScreen / FreeSpaceScreen；配对/范围/发送/结果与官方释放指引 | LiveLinkView / CleanupView |

## 导航与兼容核对

- 四个标签分别保存栈；Compose 页面状态按标签和目的地分开；系统重建恢复导航标识与有限的临时选择，不持久化凭据。
- 页面返回优先关闭弹层或退出当前照片选择；媒体详情遮盖底栏，普通二级页面保持所属标签。
- 进入批次继承当前状态及归档条件；批量操作作用于筛选中的逐项记录；取消与移除分别保留/隐藏活动记录，不删除媒体。
- Room 仍为 schema 9；不清空数据库、账号、任务或回执。APK 保持同包名同签名，升级为 2.0.0-alpha.2 / versionCode 2。
- 旧设置原值留在 DataStore；新流程收敛到共同设置。旧任务的画质、实况策略、版本和相册策略保持原快照。

## 系统差异

Android 的 SD 卡/SAF 授权、电池优化、通知、自启动和重启恢复集中在明确的 Android 区域。iOS 使用 Photos、钥匙串和系统后台安排。编辑前版本仅在系统明确提供时可用。原生控件、字号、分享器和授权对话框允许不同，不据此保留旧安卓普通产品结构。

## 用户验收清单（本轮未运行）

逐页比较四个主页面与二级入口；检查标签切换/返回/重建；检查未登录、部分授权、空内容和备份运行/暂停/失败/完成状态；检查筛选后的批次操作、归档、代理配置、电脑传输和媒体分享。测试及真实云端验收由用户执行。
