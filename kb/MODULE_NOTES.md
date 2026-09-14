---
标题: ChaoxingHook 模块主文档
日期: 2026-09-14
版本: 1.5
状态: 与代码同步（2026-09-14 学习通 7.0.3 实测通过；新增 §10 推送规范）
tags: hook 点, DexKit, 配置, 踩坑, 推送规范
关联: README.md, CHANGELOG.md, ../../app/src/main/java/com/fredoseep/chaoxinghook/MainHook.java
---

# ChaoxingHook 模块主文档

## 1. 环境与构建

| 项 | 值 |
|---|---|
| 目标 | com.chaoxing.mobile（学习通 **7.0.3** 实测 2026-09-14 通过；7.0.1 适配基线 2026-09-01；6.7.8 历史） |
| 框架 | Xposed API 82（compileOnly），LSPosed 激活 |
| 语言 | Java（hook）+ Kotlin（配置/设置 UI，Compose，三套风格见 §7） |
| 关键依赖 | DexKit（运行时反混淆定位，加固场景用 ClassLoader 方式创建）、高德 3D 地图 SDK V11.2.100（本地 `libs/*.jar` + `jniLibs`，16KB 对齐适配版）、MIUIX 0.9.4-rc01 / **Nuke**（自研，本地 `libs/nuke-release.aar`）/ Material 3 1.4.0 |
| SDK | compileSdk 37 / **targetSdk 35**（高德旧版 so 未 16KB 对齐，勿随意升 targetSdk）/ **minSdk 28**（Nuke 的 minSdk 就是 28） |
| 构建 | `gradlew assembleDebug`；**只需 local.properties 含 AMAP_MAP_KEY / AMAP_WEB_KEY**（地图选点用）。本地集成的 SDK 都在 `app/libs/`，**clone 下来开箱即可编译**，不依赖仓库外的任何目录 |
| 生效条件 | LSPosed 勾选模块 + 作用域 com.chaoxing.mobile；**更新 APK 后必须强停学习通**（代码不热更） |

## 2. 架构

```
handleLoadPackage（仅 com.chaoxing.mobile）
├── installProcessExitHook        System.exit / Runtime.exit 阻断（受考试风控开关）
├── DexKitBridge.create(classLoader, useMemoryDexFile=true)   ← 梆梆加固必须 ClassLoader 方式
│   └── installCoreHooks          原生类 hook（§3 功能 1-7，DexKit 结构匹配+回退）
├── installWebViewHooks           WebView 层（§3 功能 11-16、18-19）
│   ├── setWebViewClient → 每个客户端类: shouldInterceptRequest + onPageFinished（复制解除注入）
│   └── evaluateJavascript/loadUrl → JS 注入拦截（防切屏/指纹）
├── installFileReplaceHook        FileInputStream 构造 → 路径替换（截图）
└── installExamSnapshotHook       f1.q0 遗留回退（6.7.8 已失效，静默）
```

配置读取：`getSignConfig()` 读 `/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt`，**3 秒缓存**热更新；`READING_CONFIG` ThreadLocal 防递归（hook 内读文件可能再触发 hook）。

## 3. 功能清单（18 项，2026-09-01 移除功能 8/9/10/20/21/23/24，2026-09-07 v1.8 新增长按直达）

> 开关键 = chaoxing_loc.txt 的键名；"DexKit" = 结构匹配定位，"回退" = ObfuscationMap 硬编码类名。

### UI 净化

| # | 功能 | 开关 | hook 点 | 原理 |
|---|---|---|---|---|
| 1 | 开屏广告拦截 | 常开 | DexKit: SplashViewModel.a(Activity)→null | 拦广告数据 |
| 2 | 首页头部广告 | 常开 | HomePageHeader.g(List)→args=null | 清空广告数据 |
| 3 | 推荐分类卡片隐藏 | 常开 | MainRecordCategoryHolder.o + 反射找 TextView | 标题含"推荐"→ GONE |
| 4 | 主页记录去推荐位 | 常开 | MainPageRecordAdapter.getItemCount→真实列表 size | 反射 List 字段 |
| 5 | 记录分页 3→15 | 常开 | y6e 同签名方法全部（I/W）；6.7.8 为 zo.b0 | args[2] 3→15。**注意语义**：只提高查询上限（`xne.w` 的 `LIMIT 3→15`），不造数据——"最近使用"实际显示条数 ≤ 表内 `top_sign=0` 的行数（表 `tb_new_resource_log`，库 chaoxing.db） |
| 6 | 聊天列表过滤 | 常开 | 7.0.1: chat.manager.b 的 N2/r0/n1(List,boolean) 前置过滤；6.7.8 为 q1.c1()（已消失） | 移除 type==20 会话（元素类型白名单 ConversationInfo，误挂无副作用） |
| 7 | 撤回消息阻断 | 常开 | EMCmdMessageBody.action()（环信 SDK 不混淆） | REVOKE_FLAG→BLOCK_REVOKE_FLAG |
| 24 | 长按「设置」直达模块主页 | 常开 | 双路：① 精准 hook `MineFragment2.onViewCreated`（我页 = com.chaoxing.study.mine.MineFragment2，设置行 = CardView cv_settings）根布局递归找"设置"TextView；② 兜底 hook `View.dispatchAttachedToWindow` | TextView 不可点击（CardView 才 clickable），向上找 ≤6 层可点击祖先注入 OnLongClickListener → 启动模块设置页；弱引用表去重；单击不变（v1.8，7.0.1 jadx 确认） |

### 学习/刷课

| # | 功能 | 开关 | hook 点 | 原理 |
|---|---|---|---|---|
| 11 | 网课解锁 | 常开 | 拦 knowledge/cards / richvideo/initdatawithviewer / studentstudy 响应 | HTML 参数替换: fastforward→false、doublespeed→1、switchwindow→false（含 &quot; 实体转义兼容） |
| 12 | 图片长按下载 | 常开 | setWebViewClient + OnLongClickListener | HitTestResult 图片 → AlertDialog → 下载到 /Download/ChaoxingExam |

### 考试

| # | 功能 | 开关 | hook 点 | 原理 |
|---|---|---|---|---|
| 13 | 防切屏绕过 | 是否开启考试风控拦截 | evaluateJavascript 拦 CLIENT_WEB_LIFECYCLE | status 全部→11（前台），正则兼容 JSON 转义 |
| 14 | 随机设备指纹 | 是否开启随机指纹 | 拦 CLIENT_DEVICE_FLAG | 注入随机指纹 + 清 localStorage/sessionStorage |
| 15 | 考试风控拦截 | 是否开启考试风控拦截 | 拦 receiveExamLogs / exam/phone/exit-count / ac_event / pan-yz upload → 假 JSON；System.exit/Runtime.exit 阻断 | 双通道 |
| 16 | 复制限制解除 | 是否开启复制限制解除 | 拦 notAllowCopy.css → 空响应 | — |
| 17 | 考试截图替换 | 是否开启考试截图替换 | FileInputStream 构造：路径含 cache/image 且 .png/.jpg → 替换为 fakeImagePath；f1.q0 回退已失效 | 前置路径过滤防递归 |

### 签到

| # | 功能 | 开关 | hook 点 | 原理 |
|---|---|---|---|---|
| 18 | 定位修改 | 是否开启定位修改 | 拦 stuSignajax → 改写 latitude/longitude（配置值）+ 地址名/名字改写 | HttpURLConnection 代发 |
| 19 | 经纬度爆破 | 是否开启经纬度爆破 | 同上 + "距...X米"响应解析 | 三点反馈 historyPoints → calculateTriangulation 三角定位锁靶心（互斥于 18） |

### 调试/工具

> 功能 23（加密响应解密）已于 2026-09-01 移除（见 CHANGELOG v1.7），该分类已无功能。

> 功能 24（一键完成章节）已于 2026-09-01 移除：7.0.1 心跳改由原生层发送（WebView 拦不到），且 enc 逐条动态计算无法伪造，视频完成依赖真实播放（详见 CHANGELOG v1.4）
> 功能 8/9/10（播放器上报拦截/进度回退无视/视频心跳伪造）与功能 20/21（手势/位置签到自动完成）已于 2026-09-01 应用户要求整体移除，代码四处同步（详见 CHANGELOG v1.6）；定位修改（18）/经纬度爆破（19）保留不受影响

### 独立于开关

| 功能 | 位置 | 说明 |
|---|---|---|
| 图片长按下载 | #12 | 常驻 |

## 4. DexKit 抗混淆策略（核心设计）

学习通 R8 混淆 + 梆梆加固，**类名/方法名随版本变化**。策略：

1. **优先结构匹配**：`findClassByMethods(bridge, loader, tag, className可空, returnType, paramTypes...)`——按"返回类型+参数类型"在 `com.chaoxing.mobile` 包内找唯一匹配类，不依赖方法名
   - ⚠️ **顶级混淆包陷阱**：部分关键类在顶级包（7.0.1 分页类 `y6e`、旧版 `zo.b0`），不在 `com.chaoxing.mobile` 下，`searchPackages` 永远搜不到 → 用 `findClassByMethodsEverywhere` 全包搜索版（db-query 已用）
2. **回退链**：DexKit 失败 → ObfuscationMap 硬编码类名 findClassIfExists
3. **同签名多方法全 hook**：如 zo.b0 的 I/W 签名相同，`findMethodsBySignature` 返回列表逐一 hook
4. **第三方 SDK 不混淆**（环信 EMCmdMessageBody、高德）→ 直接硬编码
5. **字段访问双保险**：`findTextViewField`/`findListField` 先按名取，失败则按类型遍历声明字段
6. **失败静默**：全部 hook 包 try-catch + 日志，单点失效不影响其他功能

**版本适配流程**：新版本 → 看 LSPosed 日志哪个 tag 的 DexKit 匹配失败 → 用 jadx 在新 dex 里按"调用特征"（如 URL 常量、方法签名）定位新类名 → 更新 ObfuscationMap（DexKit 结构匹配通常自动适配，无需改）。

## 5. 配置链路（三处同步，加新开关必改）

配置文件：`/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt`（`键: 值` 纯文本）

```
设置页 SwitchPreference（SettingsMiuixScreen / SettingsNukeScreen / SettingsMaterial3Screen）
  → HookSettingsState.update{}（SettingsCommon.kt）→ ConfigManager.save()（Kotlin，su -c + stdin + mv 原子替换，需 root）
  → 学习通进程内 MainHook.getSignConfig()（Java）每 3 秒重读同一文件
  → hook 触发时实时检查标志位
```

> ⚠️ **读也必须走 root**（v1.11 修复的 bug）：配置落在**学习通自己的**私有外部目录，
> 而模块应用（targetSdk 35、未声明任何存储权限）受作用域存储约束 ——
> Android 11+ 禁止访问其他应用的 `Android/data`，`File.exists()` 恒为 false、
> `bufferedReader()` 抛 EACCES，连 `MANAGE_EXTERNAL_STORAGE` 都绕不过。
> 因此 `ConfigManager` 的**读和写都经 `su -c`**，不要图省事改回 `File` API。
>
> `load()` 必须区分「root 确认文件不存在」与「读不到（无 root）」：
> **只有前者**才允许写默认模板；后者要置 `ConfigManager.loadFailed` 并**完全不碰磁盘** ——
> 否则每次打开设置页都会把用户已保存的配置覆盖成默认值。

**加新开关三处同步**：
1. `ConfigManager.kt`：HookConfig 字段 + load 解析 + save 序列化（**默认值必须与 MainHook 模板逐项一致**，否则谁先创建文件谁说了算）
2. `MainHook.java`：SignConfig 字段 + getSignConfig 解析 + **默认文件模板**（`getSignConfig()` 里那一条 write）
3. `HookSettingsRows.kt`：`buildHookSettingGroups()` 里加一行（三套 UI 风格自动生效，**不需要**改三个 Screen 文件）

**当前全部键**（13 个）：是否开启定位修改/经度/纬度/是否开启地址名修改/地址名/是否开启名字修改/名字/是否开启随机指纹/是否开启经纬度爆破/是否开启考试风控拦截/是否开启复制限制解除/是否开启考试截图替换/截图替换路径

## 6. 调试

- `debugLog()` → `/data/user/0/com.chaoxing.mobile/files/chaoxinghook_debug.log`（logcat 不可靠时用；拉取：`adb exec-out su -c "cat ..."`）
- `XposedBridge.log()` → LSPosed 日志（tag `Chaoxing`/`Chaoxing DexKit[tag]`）
- DexKit 匹配结果逐项打日志（`DexKit[tag]: 类名`），启动即知全部 hook 点存活状态
- **日志纪律**：`XposedBridge.log` 仅限关键事件（DexKit 匹配结果/hook 失败/功能触发），禁止高频调用——LSPosed 的 modules.log 有轮转上限，高频写入会挤掉自身与其他模块的历史日志（高频观察类日志一律走 debugLog 文件）

## 7. 设置 UI

### 7.1 三套 UI 风格（可切换）

设置页底部「界面风格」分组里切换，选择存 `SharedPreferences`（`chaoxinghook_ui`），
**业务逻辑完全共用，差别只在渲染层**：

| 风格 | 实现文件 | 组件库 | 选择控件 |
|---|---|---|---|
| MIUIX（默认） | `SettingsMiuixScreen.kt` | `top.yukonga.miuix.kmp` 0.9.4-rc01（HyperOS） | `SwitchPreference` 开关式单选 |
| Nuke | `SettingsNukeScreen.kt` | `dev.nuke.ui`（自研，本地 `libs/nuke-release.aar`；果冻按压 + 方圆角卡片 + 圆形揭示转场） | `NukeSelectPreference` 浮层 |
| Material 3 | `SettingsMaterial3Screen.kt` | `androidx.compose.material3` 1.4.0 | `RadioButton` |

```
SettingsActivity
 └── SettingsRoot                    ← 按 SettingStore 里的偏好分发（SettingsRoot.kt）
      ├── SettingsMiuixScreen        ← 只渲染
      ├── SettingsNukeScreen         ← 只渲染 + 持有圆形揭示转场栈（含地图选点页）
      └── SettingsMaterial3Screen    ← 只渲染
           ↑ 三者共用
      HookSettingsState（SettingsCommon.kt）    配置 + 实时保存
      SettingsScaffold（SettingsCommon.kt）     Root/应用列表权限、地图选点（Activity Result）
      buildHookSettingGroups（HookSettingsRows.kt）  行模型：文案/显隐/读写只写一次
      UiStyle / SettingStore（UiStyle.kt）      风格枚举 + SharedPreferences
```

**卡片分组三套一致**：一个分组 = 一张四角圆角卡片 + 卡外小标题，组内分割线，组间 12dp。
- MIUIX：`SmallTitle` + `Card`（行自带分割线）
- Nuke：`NukeSettingGroup`（内部 0.96→1 入场缩放）+ `NukeDivider`
- M3：卡外 `Text` 小标题 + `Card` + `HorizontalDivider`

> Nuke 不用 `nukeGroupedCardItem`（每行独立 Lazy item 拼卡片）——那条路没有分割线、
> 观感与 WeKit 不一致，是给几千行超大列表用的。`NukeSettingGroup` 的高度上限（约 8192px 纹理）
> 对本页无威胁：最长一组约 1000px，且它内部用 `CompositingStrategy.ModulateAlpha` +
> `RoundedCornerShape`（而非 `NukeSquircleShape` 的通用 Path）正是为规避该问题。

- **加一个开关**：只在 `HookSettingsRows.kt` 加一行（[ConfigManager.kt](ConfigManager.kt) 与 MainHook.java 照旧同步），三套风格自动都有
- 切换风格靠 `activity.recreate()`（Compose 主题无法就地热替换）；配置实时落盘，重建不丢数据
- 风格偏好**不复用** `chaoxing_loc.txt`：那是「学习通进程读的行为开关」且写入需要 root，UI 偏好没 root 也得能存

### 7.2 其余

- `SettingsActivity`（LAUNCHER）→ `SettingsRoot()`（默认 MIUIX）
- 分组：签到（定位修改/经纬度爆破）、信息修改（地址/名字）、风控与考试（随机指纹/考试风控拦截/复制限制解除/考试截图替换）、其他（申请 Root/重置）、界面风格
- 实时保存（每次变更即 save + Toast）；`config.copy().apply{}` 触发重组
- 清单里 `android:enableOnBackInvokedCallback="true"`：Nuke 浮层预测性返回与 MIUIX Scaffold 返回处理都用它

### 7.3 地图选点的两套实现（**仅 Nuke 风格有圆形揭示转场**）

| 风格 | 实现 | 转场 |
|---|---|---|
| Nuke | `NukeMapPickerScreen.kt` —— `SettingsActivity` **窗口内**的 Compose 页面 | **圆形揭示**：从「地图选点」那一行的触点扩散入场，返回时收缩回同一个触点 |
| MIUIX / Material 3 | `MapPickerActivity.java` + `activity_map_picker.xml`（独立 Activity） | 系统 Activity 转场 |

Nuke 那套的关键点：

- 用 `NukeRevealStackNavigator`（`NukeRevealStackState<SettingsRoute>`）承载；
  `NukePreferenceRow.onClick` 给的 `Offset` 直接当 `push(from = origin)` 的圆心
- **退场圆心固定为进入时的触点**：三处（顶栏返回键 / 确认选取 / 返回手势）都传
  `optimizeExitOrigin = false` —— 该参数为 `true` 时 `pop()` 里 `from.takeIf{...}` 会改用
  返回键触点或手势边缘，`false` 才保持原圆心
- 版式与 MIUIX 的 XML 一致：**地图铺满整屏**，顶栏 / 搜索框 / 建议列表 / 底部坐标+确认 全部浮在图上
  （`NukeTextField` 自身无底色，浮层里要自己补 `.background(colors.surface)`，否则地图 POI 文字会透出来）
- `MapView` 是 **`FrameLayout` 而非 `SurfaceView`**，所以会被揭示层的 `graphicsLayer { clip = true }`
  一起裁切 —— 这是"能裁"的前提
- 生命周期手工接力：`remember { MapView(context) }` + `DisposableEffect` 转发
  onCreate/onResume/onPause/onDestroy
- 触屏选点双路（与 MapPickerActivity 同思路）：`AMap.setOnMapClickListener`
- 高德 `MapView` 只能在 `SettingsActivity` 里存活，切回 MIUIX / M3 后走的仍是原 Activity

## 8. 已知限制与版本适配记录

| 项 | 状态 |
|---|---|
| f1.q0（截图上传类名 hook） | 6.7.8 已失效，仅作静默回退；功能由 pan-yz URL 层承担 |
| q1.c1 聊天过滤 | **已修复**（7.0.1 重写为 hook `chat.manager.b` 的 N2/r0/n1，见 §3 功能 6） |
| 脱壳定位流程 | 7.0.1 实战：Layout Inspect 悬浮窗 Dump Dex（脱出 14 个 dex）→ Python 解析 dex proto/method_ids 精确搜签名 → jadx 定点反编译核对 |
| 拍照签到自动完成 | 未实现（需 pan-yz 上传换 objectId） |
| 视频完成（7.0.1） | 心跳走原生层（proxy_completed JSBridge→HttpURLConnection），WebView 拦不到；enc 逐条动态计算无法伪造。完成依赖真实播放；fastforward:true 的课可跳尾触发 isdrag=4 完播心跳 |
| targetSdk | 锁 35（高德 so 16KB 对齐问题） |
| LSPatch/沙盒 | 均失败，必须 root + LSPosed |
| **学习通 7.0.3（2026-09-14 实测）** | **DexKit 结构匹配全自动适配，无需改代码**。分页类 `y6e` → **`ace`**（`I`/`W` 两个同签名方法都 hook 上，`pageSize 3 -> 15` 实测触发）；其余 5 个 tag 类名未变（`SplashViewModel`/`HomePageHeader`/`MainRecordCategoryHolder`/`MainPageRecordAdapter`/`chat.manager.b`）。启动自检 6/6 `installXxx OK`，无失败项 |
| 7.0.3 下 `category-holder` 方法名漂移 | `MainRecordCategoryHolder` 的 hook 目标由 `o(...)` 变为 **`l(...)`** —— 但代码按**签名** hook（`void(ResourceLog)`），不受方法名影响，属设计内行为 |
| **分支陷阱（务必注意）** | `feat/root-app-list`（Root 权限申请）与 `feat/longpress-entry`（功能 24 长按直达）**在 9aff514 分叉、互不包含**。构建前必须先确认当前分支/工作区是否含你要的功能，否则会把设备上的模块**静默降级**（排查方法：看 debug 日志有没有 `installLongPressModuleEntry OK`）。两分支对同一文件的改动不重叠（前者只动 build.gradle/Manifest/Settings*，后者只动 MainHook.java），`git cherry-pick -n d362664` 可干净并入 |

## 9. 踩坑

1. **配置读取递归死循环**：hook 内读配置文件可能再触发自身 hook → `READING_CONFIG` ThreadLocal 防重入 + 前置路径过滤
2. **Xposed API 82 无 `param.returnValue`** → 用 `getResult()`/`setResult()`
3. **抽象方法/接口方法不能 hook** → 捕获实现类后 hookAllMethods
4. **varargs 陷阱**：参数数组后直接跟回调不会展开 → 手动拼 Object[]
5. **LSPosed 更新模块后**：强停学习通；若仍未加载查 `modules_config.db` 的 modules_state
6. **AnimatedVisibility 内多组件必须包 Column**，否则重叠堆叠
7. **mutableStateOf 按引用比较**：配置修改须 `copy().apply{}` 不能原地 apply
8. **`rememberSaveable` 与「实时保存到文件」互相打架**：它恢复的是重建前那一刻的快照，会把刚输入还没落盘（或保存失败）的文本又覆盖回来 → 设置页状态一律用 `remember`，重建后从文件重读（UI 风格切换正好会重建 Activity，踩过）
9. **Miuix 的 SwitchPreference 开关可拖拽**：自动化测试里 `input swipe` 起点落在开关上会被判成拖拽开关而不是滚动列表（真机验证时的坑）
10. **Nuke 的 `NukeSettingGroup` 会 clip 整张卡**：卡片高过约 8192px 后命中测试失效（行看得见点不动）→ 长列表一律用 `Modifier.nukeGroupedCardItem(index, count)` 每行独立成 Lazy item

## 10. 版本控制与推送规范（**推送前必读**）

### 10.1 铁律：只保留一个对外分支

**本仓库对外只有一个主分支。所有改动一律提交到主分支后推送，不要新建长期存在的 feature 分支。**

| 场景 | 用法 |
|---|---|
| 上游仓库 | 主分支是 `master`，直接在其上提交 / 合并 |
| Fork 自用 | 主分支建议统一叫 `main`；`origin` 指向上游（**只读，不要推**），推送走自己的 `fork` 远端 |

> 需要试验性改动时，用本地临时分支或 `git stash`，**不要 push**；确认无误后合并回主分支再推。
> Fork 自用时推送别忘带远端名：`git push fork main:main` —— 只写 `git push main` 会因为
> 默认远端是上游而推错地方。

### 10.2 为什么定这条规矩（两次真实事故）

1. **分支分叉 → 功能被静默降级**：曾经同时存在 `feat/root-app-list`（Root 权限申请）与
   `feat/longpress-entry`（功能 24），二者在 `9aff514` 分叉、**互不包含**。
   按其中一个分支构建安装，会把设备上已装的模块**降级**，而且**没有任何报错** ——
   用户只会发现"某个功能不见了"。收敛为单一 `main` 后，这类问题从根上消失。

2. **历史里的隐私擦不掉**：本机绝对路径曾随提交进入历史，后来用新提交从工作区清掉了，
   但 `git log -p` 仍能看到旧版本 —— **事后清理必须重写历史 + 强推**。
   所以隐私一定要在**提交前**拦下。

### 10.3 推送前检查清单（逐条过，别跳）

| # | 检查项 | 通过判据 |
|---|---|---|
| 1 | 只含预期改动 | `git status --short` 的每一行都是你打算提交的 |
| 2 | **无本机绝对路径** | 扫描无命中 |
| 3 | 无密钥 / token | 只允许一处预期命中：`com.amap.api.v2.apikey`（高德 SDK 的 **meta-data 名称**，不是密钥值） |
| 4 | 无身份信息 | 扫描无命中 |
| 5 | 作者是自己 | 输出为 `justsoso17 <justsoso17@users.noreply.github.com>` |
| 6 | 能编译 | `BUILD SUCCESSFUL` |
| 7 | 文件清单无敏感项 | 不含 `local.properties` / `.claude/` / `work/` / `build/` |

命令（**整段复制到 Git Bash 跑**，已在 Windows Git Bash 实测通过）：

```bash
cd /e/chaoxinghook
# 排除本规范自身 —— 否则文档里的示例文本会自己匹配自己
EX=":(exclude)kb/MODULE_NOTES.md"

# 1) 改动清单
git status --short

# 2) 本机绝对路径（预期：无命中）
git grep -nIE '[A-Za-z]:[\\/]' -- . "$EX" \
  | grep -vE 'https?://|schemas\.android\.com|xmlns|apache\.org|gradle\.org|github\.com|developer\.android\.com|aliyun\.com|xposed\.info|amap\.com|chaoxing\.com|aichoxing\.com'

# 3) 密钥 / token（预期：只剩 AndroidManifest 里的 com.amap.api.v2.apikey）
git grep -nIE 'api[_-]?key|apikey|secret|password|passwd|token|bearer|[0-9a-f]{32}' -- . "$EX"

# 4) 邮箱 / 手机号（预期：无命中）
git grep -nIE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.(com|cn|net)|1[3-9][0-9]{9}' -- . "$EX"

# 5) 作者身份
git log -1 --pretty='%an <%ae>' main

# 6) 编译
./gradlew assembleDebug

# 7) 公开文件清单（人工过一眼）
git ls-tree -r --name-only main
```

> **三个坑（都真实踩过）**：
> 1. 不加 `-I`：`.so` / `.jar` 会回 `Binary file ... matches`，把真问题淹掉；
> 2. 不排除 `kb/MODULE_NOTES.md`：本节里的示例串（示例路径、`api...key`、作者邮箱）
>    会自匹配，导致"永远有命中"，扫了等于没扫；
> 3. `git grep` 用的是 POSIX 正则，**反斜杠在模式里要写两个**才表示一个字面反斜杠。
>    因此查「盘符 + 反斜杠」这类路径时一律用 -F（原样路径，不做正则转义）；
>    用 -E 时反斜杠要翻倍，**多翻一倍会静默漏检**（曾因此漏掉整段历史扫描）。
>
> **要连历史一起查**（当前文件干净 ≠ 历史干净，隐私一旦提交就很难擦）：
>
> ```bash
> git grep -nI -F '要查的串' $(git rev-list --all)
> ```

### 10.4 禁止入库的文件（`.gitignore` 已覆盖，**不要用 `git add -f` 绕过**）

| 路径 | 原因 |
|---|---|
| `local.properties` | 含高德 `AMAP_MAP_KEY` / `AMAP_WEB_KEY` |
| `work/` | 脱壳 dex、jadx 反编译、261MB 原始 APK、截图 —— 体积大且敏感 |
| `.claude/` | 本机 AI 工具配置（含命令历史、URL、临时凭据） |
| `.idea/` `.kotlin/` | 本机 IDE / Kotlin 配置 |
| `build/` `.gradle/` | 构建产物 |

### 10.5 体积基线

| 项 | 值 |
|---|---|
| 仓库体积 | 约 **35.9 MB** / 57 个文件 |
| 最大文件 | `app/src/main/jniLibs/arm64-v8a/libAMapSDK_MAP_v11_2_100.so`（24.6 MB） |
| 本地集成 SDK | `app/libs/amap3dmap-11.2.100.jar`（10.9 MB）、`app/libs/nuke-release.aar`（336 KB） |
| 已清理 | `app/src/main/jniLibs/armeabi-v7a/`（约 **16.9 MB**）已于 2026-09-14 删除 |

`abiFilters` 只留 `arm64-v8a`，v7a 那两个 `.so` **从不进入 APK**（已对比 app-debug.apk
内的 `lib/` 核实：只有 arm64-v8a 四个 so）。删除后仓库从 52.7 MB 降到 35.9 MB。

> **若要重新支持 32 位**：高德 SDK 是本地集成的 **16KB 对齐适配版**，需另取同版本 v7a so
> 放回 `jniLibs/armeabi-v7a/`，同时改 `build.gradle` 的 `abiFilters`。

> **Nuke 是本地 AAR，不是 includeBuild**：早期用 `includeBuild('../nuke')` + `dependencySubstitution`，
> 那要求 Nuke 源码与仓库同级，别人 clone 后**无法构建**。现改为 `app/libs/nuke-release.aar`。
> 改 Nuke 源码后需要重新出包：
>
> ```bash
> cd <nuke 源码目录> && ./gradlew :nuke:assembleRelease
> cp nuke/build/outputs/aar/nuke-release.aar <本仓库>/app/libs/
> ```
