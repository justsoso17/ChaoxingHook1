---
标题: ChaoxingHook 模块知识库入口
日期: 2026-09-01
版本: 1.3
状态: 与实际代码同步（2026-09-01 移除功能 8/9/10/20/21/23 后核对）
tags: chaoxinghook, Xposed, LSPosed, DexKit, 入口
关联: MODULE_NOTES.md, CHANGELOG.md, ../README.md（免责声明）
---

# 先看这里！ChaoxingHook 模块知识库

基于 Xposed/LSPosed 的学习通（com.chaoxing.mobile）增强模块。本知识库**只描述模块本身**（架构/hook 点/配置/调试），学习通协议级结论另见独立维护的协议知识库（未随本仓库分发）。

## 核心结论（一句话）

模块 = **DexKit 结构匹配抗混淆** + **四层 hook**（原生类/WebView 网络/文件系统/视图注入）+ **配置文件 3 秒热读**，共 18 项功能；UI 净化与学习/刷课类（1-7、11、12、24）为**常开**，考试/签到类（13-19）受 `chaoxing_loc.txt` 开关控制。

## 阅读引导

| 你的目的 | 看哪 |
|---|---|
| 了解全部功能与 hook 点 | MODULE_NOTES.md §3 功能清单 |
| 版本更新后 hook 失效 | MODULE_NOTES.md §4 DexKit 抗混淆策略 + §8 版本适配 |
| 加新功能/新开关 | MODULE_NOTES.md §5 配置链路（三处同步）+ §3 模板 |
| 排查"不生效" | MODULE_NOTES.md §6 调试 + §9 踩坑 |
| 编译 | MODULE_NOTES.md §1 环境（需要 local.properties 高德 Key） |
| **准备提交/推送** | **MODULE_NOTES.md §10 版本控制与推送规范（只推 main + 隐私检查清单）** |

## 文件清单

| 文件 | 内容 |
|---|---|
| MODULE_NOTES.md | 主文档：架构/功能清单/hook 点/配置/调试/版本适配记录/推送规范（§10） |
| VERIFY.md | 全功能验证流程：启动自检/逐项验证步骤/日志关键词对照/配置持久化自检（§0.5） |
| CHANGELOG.md | 版本记录 |
| ../app/src/main/java/com/fredoseep/chaoxinghook/MainHook.java | 全部 hook 逻辑（Java，1204 行） |
| ../app/src/main/java/com/fredoseep/chaoxinghook/ConfigManager.kt | 设置 App 侧配置读写（读写均经 `su`，见 §5） |
| ../app/src/main/java/com/fredoseep/chaoxinghook/SettingsActivity.kt | 设置页入口（申请应用列表权限） |
| ../app/src/main/java/com/fredoseep/chaoxinghook/SettingsRoot.kt | 按 UI 风格偏好分发到三套 Screen |
| ../app/src/main/java/com/fredoseep/chaoxinghook/SettingsCommon.kt | 三套风格共用：状态 / 实时保存 / Root 申请 / 地图选点 |
| ../app/src/main/java/com/fredoseep/chaoxinghook/HookSettingsRows.kt | 与 UI 库无关的设置行模型（文案/显隐/读写只定义一次） |
| ../app/src/main/java/com/fredoseep/chaoxinghook/UiStyle.kt | 风格枚举 + SharedPreferences 偏好 |
| ../app/.../SettingsMiuixScreen.kt、SettingsNukeScreen.kt、SettingsMaterial3Screen.kt | 三套风格的渲染层（见 §7） |
| ../app/src/main/java/com/fredoseep/chaoxinghook/NukeMapPickerScreen.kt | 地图选点（Nuke 风格，窗口内圆形揭示转场） |
| ../app/src/main/java/com/fredoseep/chaoxinghook/MapPickerActivity.java | 地图选点（MIUIX / M3 风格，独立 Activity） |
| ../work 留档 | 协议级逆向知识见独立维护的协议知识库（未随本仓库分发） |

## 免责声明

见项目根 README.md。仅供技术学习与交流，禁止用于学术不端或违法行为。
