# iOS 冷启动登录顺序修复

iOS 0.2.42（46）。0.2.41 的用户设备复测仍未自动登录；先前账号选择持久化和临时失败重试未解决启动依赖问题。

确定的代码路径：HarborModel.boot 先调用 Engine.boot；Engine.boot 自动恢复账号，通过 Engine.invoke(connect) 请求 ProxyManager.route；合并版 ProxyManager.sharedDecision 要求 HarborModel.coreAdapter.available。这个 available 直到 Engine.boot 返回之后，HarborModel 才调用 coreAdapter.probe 设置。开启代理时，首次登录必然先于代理选路所依赖的共享核心初始化。手动选账号发生在页面初始化完成后，可以正常连接，与用户现象一致。没有设备日志，不能排除设备上还存在其他触发因素。

改为 Engine.boot 仅初始化本地 Python 运行时。HarborModel 加载本地存储后，通过 SessionStartup 依次准备共享核心和代理验证、恢复账号，然后载入该账号的设置并恢复 URLSession 任务。保留已有代理策略，不通过直连绕过选路。原有账号选择 Keychain 持久化、主动退出标记、限频恢复继续生效。

诊断日志增加 session.startup/routing_ready 及 session.restore 的成功或错误码，不记录账号或凭据，便于核对设备冷启动实际走到哪一步。

验证：真实生产 SessionStartup 在 macOS 隔离 Keychain 上运行；模拟冷的选路依赖、异步核心准备、连续两次冷启动、不就绪不认证、真实认证失败不伪装为成功。合计 31 项 Swift 检查；另有实际 Engine/HarborModel 调用顺序回归检查及既有 Python 离线套件、完整未签名 IPA 编译。未安装到真实设备或使用真实账号联网验证。
