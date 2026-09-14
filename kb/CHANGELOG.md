---
标题: ChaoxingHook 变更记录
日期: 2026-09-14
版本: 1.8
状态: 持续追加
tags: changelog
---

## 2026-09-14 v1.13 Nuke 改为本地 AAR（让仓库开箱可构建）

- **问题**：Nuke（自研组件库）此前走 `settings.gradle` 的 `includeBuild('../nuke')` +
  `dependencySubstitution`，这要求 Nuke 源码目录与本仓库**同级存在** ——
  别人 clone 下来 Gradle 在 settings 阶段就会失败，**仓库无法构建**。
- **改法**：预编译的 `nuke-release.aar`（336 KB）放进 `app/libs/` 本地集成，
  与高德 SDK 的做法一致：
  - `settings.gradle` 删除 `includeBuild('../nuke')` 整块
  - `app/build.gradle` 的 `fileTree(include: ['*.jar'], ...)` → `['*.jar', '*.aar']`，
    并移除 `libs.nuke.ui` 依赖声明
  - `gradle/libs.versions.toml` 删除 `nuke` 版本与 `nuke-ui` 库条目
- **验证**：把整个项目复制到 `../nuke` 不存在的孤立目录后 `assembleDebug` **构建成功** ——
  证明不再依赖仓库外的任何目录。
- **代价**：改 Nuke 源码后需重新出 AAR（步骤见 MODULE_NOTES §10.5），
  换来的是任何人都能 clone 即编译。
- 同步文档：MODULE_NOTES §1（关键依赖/构建）、§7（Nuke 行）、§10.1（改写为仓库无关表述）、§10.5。

## 2026-09-14 v1.12 学习通 7.0.3 实测通过 + 补回功能 24（分支分叉导致被降级）

- **环境**：Redmi K60 / **Android 16 (SDK 36)** / KernelSU v3.3.0 / LSPosed；学习通 **7.0.3**（versionCode 10994，2026-09-14 更新，此前设备上是 7.0.1）
- **7.0.3 兼容性：DexKit 抗混淆策略验证成功，未改一行代码**
  - `db-query` 分页类 **`y6e` → `ace`**（R8 重新混淆），`findClassByMethodsEverywhere` 全包结构匹配自动跟上；
    `I`/`W` 两个同签名方法均 hook 成功，`db-query.W: pageSize 3 -> 15` 实测触发
  - `category-holder` 的 hook 目标方法名由 `o(...)` → **`l(...)`**：代码按**签名**（`void(ResourceLog)`）匹配，不受影响
  - 其余 tag 类名未变；启动自检 **6/6 `installXxx OK`，0 条失败/回退日志**
- **补回功能 24（长按「设置」直达模块主页）**：`git cherry-pick -n d362664`
  - 起因：仓库有两个**分叉**分支 —— `feat/root-app-list`(a4cd33e，Root 权限申请) 与
    `feat/longpress-entry`(d362664，功能 24)，二者在 `9aff514` 分叉、**互不包含**。
    设备上原跑的是后者，按当前工作区（前者）构建安装会**静默丢掉功能 24**
  - 两者改动文件不重叠（a4cd33e 只动 build.gradle / AndroidManifest / SettingsActivity / SettingsScreen；
    d362664 只动 MainHook.java），故 cherry-pick **无冲突**（161+/64−，MainHook.java 1107 → 1204 行）
  - 采用 `-n`（只改工作区不提交），MainHook.java 处于 **staged** 状态，由用户自行决定何时 commit
  - 装机验证：`installLongPressModuleEntry OK` + `longpress-entry: 已注入设置行长按 (row=FrameLayout / CardView)` 双路命中
- **构建前检查清单（新增）**：改代码后构建安装前，先确认当前分支/工作区包含全部预期功能，
  并对比设备上已装版本的 debug 日志（缺 `installLongPressModuleEntry OK` = 该功能不在当前构建里）

## 2026-09-12 v1.11 修复「退出设置页后配置丢失」（作用域存储导致读路径失效 + 反向覆盖）

- **bug 现象**：设置页改完 → 退出 → 再进又变回默认值，看起来"配置没保存"（实际学习通侧短暂读到过正确值，所以一直没被发现）
- **根因**：`ConfigManager.load()` 用 `File(CONFIG_PATH).exists()` 判断存在性。配置位于**学习通自己的**私有外部目录
  `/storage/emulated/0/Android/data/com.chaoxing.mobile/files/`，而模块应用 targetSdk 35、**未声明任何存储权限**，
  Android 11+ 的作用域存储禁止访问其他应用的 `Android/data`（`MANAGE_EXTERNAL_STORAGE` 也绕不过）→
  - `exists()` 真机恒为 **false** → 每次都走进 "文件不存在 → `save(默认)`" 分支，**把磁盘上用户已保存的配置覆盖成默认值**
  - 紧随其后的 `bufferedReader()` 抛 EACCES，被空 `catch` 吞掉 → 返回的仍是默认值
- **修复**（`ConfigManager.kt` 重写）：
  - **读也走 root**：`su -c 'if [ -f ... ]; then cat ...; else echo <哨兵>; fi'`，
    与写入路径对称；不再使用任何 `File` API 读写配置
  - **区分三态**：`Ok`（读到内容）/ `Absent`（root 明确回答文件不存在，或被清空）/ `Failed`（无 root、su 被拒、退出码非 0）。
    **只有 `Absent` 允许写默认模板**，`Failed` 置 `loadFailed` 并完全不碰磁盘
  - **`loadFailed` 守卫**：读取失败期间 `save()` 直接返回 false —— 内存里只是默认值，落盘等于清空用户配置
  - `reset()` 改为返回 `Boolean`（重置是确认框里显式点过的破坏性操作，**不受** `loadFailed` 保护），
    `HookSettingsState.reset()` 按真实结果显示「配置已重置 / 重置失败，可能需要 Root」
  - 设置页读取失败时弹长 Toast 说明「当前显示默认值，获得 Root 前不会覆盖已保存的配置」
  - 「申请 Root 权限」成功后自动 `settings.reload()` 补读一次 —— 否则 `loadFailed` 会一直挡住保存，
    表现为"授权了但开关点了没反应"；`reload()` 只在 `loadFailed` 时生效，避免顶掉刚输入未落盘的文本
- **顺带修掉写入路径的两个隐患**：
  - 不再用 `File.createTempFile` 落本应用私有目录的临时文件（`java.io.tmpdir` 在不同 ROM 上指向不一致，
    且要求 root 能读本应用私有目录）→ 改为内容经 **stdin** 送进 root 侧，
    `cat > *.new && mv -f` **原子替换**（学习通不会读到写了一半的配置）
  - 补 `@Synchronized` 串行化，并**排空子进程 stdout/stderr**（管道写满会让 su 阻塞在 `waitFor()` 上，表现为"保存卡死"）
- **对齐两边默认值**（此前 ConfigManager 与 MainHook 模板不一致，谁先创建 `chaoxing_loc.txt` 谁说了算）：
  - `randomizeDeviceFlag`：`false` → **`true`**（与 MainHook 模板一致）
  - `fakeImagePath`：`""` → **`/storage/emulated/0/Download/fake_exam_image.png`**（`ConfigManager.DEFAULT_FAKE_IMAGE_PATH`）

## 2026-09-11 v1.10 Nuke 卡片分组对齐 WeKit + 地图选点改窗口内圆形揭示转场

- **三套风格卡片分组统一**（按 WeKit 设置页观感）：一个分组 = 一张四角圆角卡片 + 卡外小标题 + 组内分割线，组间 12dp
  - Nuke 从 `nukeGroupedCardItem`（每行一片、无分割线）改为 `NukeSettingGroup` + `NukeDivider`；顺带拿回该组件自带的 0.96→1 入场缩放
  - M3 组头从卡内透明 `ListItem` 挪到卡外（原来标题与内容糊在同一块底色上，分组边界看不出来），底色改 `surface`，组间距 8dp
  - 纠正上一版的一个错误判断：`NukeSettingGroup` 内部用 `CompositingStrategy.ModulateAlpha` + `RoundedCornerShape` 正是为规避 8192px 纹理/命中测试问题，本页最长一组约 1000px，原先"长列表不能用它"的顾虑不成立
- **Nuke 补回缺失的按压/动效**（之前只接了 Action/MapPick 两类行）：
  - 开关行、输入行都补 `onClick` —— `NukePreferenceRow` 内部是 `if (onClick == null) Modifier else nukeJellyClickable(...)`，漏传就整行没有果冻按压（缩放到 0.92 + 3D 倾斜 + 原点跟随手指 + 触感）
  - 输入行改用 `NukeTextField`，拿回聚焦动效（圆角 11→13dp、2dp 边框浮现、0.996/1.012 拉伸、内容下移 1dp、placeholder 淡化）
  - 「重置所有配置」接 `NukeSimpleDialog` 确认框（行模型加 `confirm` 字段），弹窗的三段 keyframe 缩放生效
- **地图选点（仅 Nuke 风格）改为窗口内 Compose 页面** `NukeMapPickerScreen.kt`，配 `NukeRevealStackNavigator` 圆形揭示转场：
  - 入场圆心 = 点「地图选点」那一行的触点；**退场圆心同样是该触点**（三处 `optimizeExitOrigin = false`：顶栏返回键 / 确认选取 / 返回手势。传 `true` 会改用返回键触点和手势边缘）
  - 版式与 MIUIX 的 XML 对齐：地图铺满整屏，顶栏/搜索框/建议/底部面板浮在图上；`NukeTextField` 无底色，浮层里自行补 `.background(surface)`
  - `MapView` 是 `FrameLayout` 而非 `SurfaceView`，能被揭示层 `graphicsLayer { clip = true }` 一起裁切；生命周期用 `DisposableEffect` 手工转发
  - MIUIX / Material 3 仍走 `MapPickerActivity` + `ActivityResultContracts`（用户指定：只有 Nuke 风格有这套转场）

## 2026-09-11 v1.9 设置页 UI 风格切换（MIUIX / Nuke / Material 3）

- **新增三套 UI 风格**：设置页顶部「界面风格」分组可切换 MIUIX（HyperOS，默认）/ Nuke / Material 3，选择存 `SharedPreferences`（`chaoxinghook_ui`），切换时重建 Activity 后立即生效。真机（Android 17 / MIUI）三套逐一验证通过，无崩溃
- **Nuke 接入**：`../nuke` 以 `includeBuild('../nuke')` + `dependencySubstitution` 组合构建组入（改 Nuke 源码无需重新出 AAR）；AGP 对齐到 9.3.2
- **Material 3 接入**：`compose-bom 2026.08.00` + `material3 1.4.0`。Miuix 经 Compose Multiplatform 把 material3 拉到 `1.5.0-alpha22`，已用 `resolutionStrategy.force` 钉回稳定版 1.4.0
- **minSdk 24 → 28**：Nuke 的 minSdk 就是 28（它只用 `SDK_INT == P` 判断，无 28+ API 调用）。受众为 Magisk + LSPosed + 学习通 7.0.1，28 不损失覆盖面
- **重构（业务逻辑与渲染分离）**：原 `SettingsScreen.kt`（361 行 Miuix 单实现）拆为
  - `UiStyle.kt` / `SettingStore`：风格枚举 + 偏好持久化
  - `SettingsCommon.kt`：`HookSettingsState`（配置 + 实时保存）、`SettingsScaffold`（Root/应用列表权限、地图选点）、`rememberUiStyleSwitcher`
  - `HookSettingsRows.kt`：与 UI 库无关的行模型（`Toggle/Text/Action/MapPick`），文案/显隐/读写**只定义一次**
  - `SettingsRoot.kt`：风格分发；三个 `Settings*Screen.kt` 只负责渲染
- `AndroidManifest` 开 `enableOnBackInvokedCallback="true"`（Nuke 浮层预测性返回 + MIUIX Scaffold 返回处理都需要）
- 顺带修掉一个老问题：`rememberSaveable` 会把重建前的配置快照恢复回来，与「实时保存到文件」互相打架 —— 改用 `remember` + 重建后重读文件，风格切换不会回滚未落盘的输入
- > 本版还曾擅自把实时保存的成功 Toast 去掉，**已回退**（用户未要求该改动）：恢复为成功「配置已保存」/ 失败「保存失败，可能需要 Root」

## 2026-09-07 v1.8 长按直达模块主页 + Root/应用列表权限申请 + arm64 精简

- **新增长按入口（常开）**：「我」页 = `com.chaoxing.study.mine.MineFragment2`（jadx 7.0.1 确认），设置行 = CardView(cv_settings) 可点击、内层 TextView 不可点击。双路注入：① 精准 hook MineFragment2.onViewCreated 根布局递归找"设置"TextView；② 兜底 hook `View.dispatchAttachedToWindow`。首版 bug：误加 `tv.isClickable()` 前置过滤导致 CardView 结构下全部跳过无反应，已修
- **设置页新增"申请 Root 权限"**：`su -c id` 实际输出（uid=0）判定授权；结合 Magisk 安装检测（Manifest queries 声明包名）分级提示（未装 Magisk / 已装被拒 → 引导超级用户列表）；未授权应用列表权限时先弹应用列表申请，授权后自动续接 Root
- **应用列表权限适配**（工信部 TTAF 108-2022）：运行时申请 `com.android.permission.GET_INSTALLED_APPS`（dangerous），ColorOS 16 / MIUI 13+（com.lbe.security.miui）弹窗；SettingsActivity 启动延迟 600ms 申请（MIUI 对立即请求可能静默拒绝）；授权结果以实际状态复核，不信任 ROM 回调
- **体积优化**：`abiFilters arm64-v8a` 仅打包 64 位原生库，APK 67.3 → 48.1MB
- 随机指纹开关说明文案改为「用于单设备多账号签到」
- 功能计数 17 → 18 项

## 2026-09-01 v1.7 移除功能 23（加密响应解密，应用户要求）

- **完全移除**：installDecryptHook（Cipher.doFinal hook）及其辅助函数 extractPrintable/printable、handleLoadPackage 调用点
- **配置键数 14→13**：MainHook SignConfig/解析/默认模板与 ConfigManager 字段/解析/序列化同步删除"是否开启响应解密"
- **SettingsScreen**：删除"加密响应解密"开关（"章节与调试"分组随之清空，整组移除）
- 旧配置文件中残留的"是否开启响应解密"键会被解析器自动忽略
- 文档同步：MODULE_NOTES/VERIFY/kb-README 功能计数 18→17

## 2026-09-01 v1.6 移除功能 8/9/10/20/21（应用户要求）

- **完全移除五个功能**：播放器行为上报拦截（8）、进度回退无视（9）、视频心跳伪造（10）、手势签到自动完成（20）、位置签到自动完成（21）
- **MainHook.java**：installCoreHooks 播放器 Pa/Wa 与 CourseDotRes 回退块删除；shouldInterceptRequest 的 `/multimedia/log/a/` 篡改块删除；onPageFinished 自动签到注入与 sign-diag 诊断删除；`buildAutoSignJs()` 整体删除；SignConfig 删除 gestureAutoSign/locationAutoSign 字段；默认配置模板删除对应两键（键数 16→14）
- **ObfuscationMap 清理**：CLASS_PLAYER_FRAGMENT / METHOD_PLAYER_PA / METHOD_PLAYER_WA / CLASS_DOT_RES / METHOD_DOT_RES_ROLLBACK 移除
- **ConfigManager.kt / SettingsScreen.kt**：gestureAutoSign / locationAutoSign 字段、解析、序列化与两个 SwitchPreference 同步删除；配置键数 16→14
- **保留不受影响**：定位修改（18）/ 经纬度爆破（19）——stuSignajax 拦截层独立工作；网课解锁（11）不依赖心跳伪造
- 旧配置文件中残留的"是否开启手势自动签到 / 是否开启位置自动签到"键会被解析器自动忽略，无需手动清理
- 文档同步：MODULE_NOTES §3 功能清单 23→18 项、VERIFY 逐项步骤与关键词表、kb/README 核心结论、根 README 功能列表

## 2026-09-01 v1.5 MODULE_NOTES 文档核对（代码同步，无代码变更）

- **修正 §3 功能 20/21 描述过时**：旧文"fetch stuSignajax 直接提交/用活动下发靶心坐标"与 v1.4 后代码不符——实际已改为**模拟点击页面自身签到按钮**方案（7.0.1 直发 stuSignajax 被 90002+滑块拒绝；手势页无按钮提示手动绘制；位置坐标改写仍由功能 18 拦截层承担）
- **删除 §8 矛盾残留行**："Pa(int) 旧签名已修正为无参 Pa()"与同表"Pa/Wa 7.0.1 带参"冲突且与反射枚举实测不符（7.0.1 实为 `Pa(int,int)`/`Wa(int,int)`，[player-enum] 确认）
- **§6 补日志纪律**：XposedBridge.log 仅限关键事件——LSPosed modules.log 有轮转上限，高频写入会挤掉自身与其他模块的历史日志
- frontmatter 版本 1.0 → 1.1（状态标注 7.0.1 适配后核对）

## 2026-09-01 v1.4 移除功能 24（一键完成章节）+ 功能 16 换方案

- **移除 功能 24**：7.0.1 心跳改由原生层发送（wap 播放器 `proxy_completed` JSBridge → 原生 HttpURLConnection，UA=Dalvik），WebView 的 shouldInterceptRequest 永远拦不到；且 enc 逐条动态计算（与参数绑定）无法伪造 → 功能失去根基，经用户确认整体移除（代码/ConfigManager/SettingsScreen/配置模板四处同步，键数 17→16）
- 心跳实测结论（cxanalysis 原生 HTTP 钩子抓取）：端点升级为 `mooc1-api.chaoxing.com/mooc-ans/multimedia/log/a/{cpi}/{签名}`，参数 `playingTime=214`（定时）/`241-238`（拖拽区间）/`609+isdrag=4`（完播），`reportTimeInterval=60`
- 视频完成现状：依赖真实播放；`fastforward:true` 的课跳尾可触发真实 `isdrag=4` 完播心跳（已实测服务端接受）
- **重写 功能 16（复制解除）**：7.0.1 中 `notAllowCopy.css` 已不存在 → 改为通用"强制可选中/可复制"注入（user-select 覆盖 + 内联阻止清除 + 捕获阶段 stopPropagation，所有页面幂等注入），实测生效
- 新增诊断：功能 20/21 的 activeId 页面若不在 mobilelearn 域会记录 URL（sign-diag）

## 2026-09-01 v1.3 学习通 7.0.1 适配

- **新增 kb/VERIFY.md**：全功能验证流程（启动自检/24 项逐项步骤/日志关键词对照/3 分钟冒烟流程）

- **修复 功能 5（分页 3→15）失效**：7.0.1 中旧类 `zo.b0` 已消失（`zo` 包整体不存新版本中），新类为顶级混淆包 **`y6e`**，方法名 `I`/`W` 与签名 `(Context,int,int)->LiveData` 不变
- **根因**：DexKit 结构匹配写死 `searchPackages("com.chaoxing.mobile")`，而 `y6e` 在顶级包永远搜不到；回退类名 `zo.b0` 也已失效 → 双路全断
- **修复方式**：`findClassByMethods` 重构出 `findClassByMethodsEverywhere`（全包搜索版）；`ObfuscationMap.CLASS_DB_QUERY` → `y6e`
- **可观测性**：DexKit 结构匹配失败/回退失败/方法未找到现在均写 debugLog（原为静默）；player-frag 增加 Pa/Wa 存活上报
- **7.0.1 全量核对**（脱壳 dex 静态验证）：1/2/3/4/7/8/9 号 hook 点全部存活；`Pa/Wa` 7.0.1 变为带参 `Pa(int,int)`/`Wa(int,int)`，`hookAllMethods` 按名 hook 不受影响；**功能 6（q1 聊天过滤）确认死亡**（multiple matches + 类不存在）
- **验证**：装机实测 `db-query: clazz=y6e matchedMethods=2`，`pageSize 3 -> 15` 触发
- **修复 功能 6（聊天过滤）**：7.0.1 重写 hook——`chat.manager.b.N2/r0/n1(List,boolean)` 前置过滤 type==20（LSPosed 日志确认 5 处挂载成功）
- **澄清 功能 5 语义**：分页 3→15 只提高 LIMIT 上限不造数据；实测当前账号 `tb_new_resource_log` 仅 4 条 top_sign=0，故界面显示 4 条属正常（脱壳 jadx 链路核对 W→V→U→xne.w 全动态无硬编码）

## 2026-09-01 v1.2 kb 文档审计（代码逐项比对，无代码变更）

- 对照 MainHook.java（1394 行）逐项核查 §3 功能清单：**24 项功能全部与代码一致**，拦截 URL 全集（multimedia/log、keeper/exam、knowledge/cards 等 5 处）无文档遗漏
- 修正：§5 配置键数量 16 → **17**（列表本身 17 项，计数笔误）
- 修正：README.md 核心结论"全部受开关控制"→ 实际 1-12 号（UI 净化/学习刷课）为**常开**，13-24 受开关控制
- 修正：根 README.md 三处过时——分页"3 up to 10"→ 15、"无 UI 需手改配置"→ 已有完整设置页（补 UI 说明与 kb 目录指引）

## 2026-08-31 v1.1 文档审查同步

- 协议库严格审查发现 knowledge/cards 须按 num=0..6 循环拉卡（一个知识点多张卡片）——MODULE_NOTES §8 补充功能 24 的"可能漏任务"限制与改进方向（详见协议库 OSS_API_REFERENCE §9）

## 2026-08-31 v1.0 功能扩展（本次会话）

- **新增 手势签到自动完成**（开关：是否开启手势自动签到）：onPageFinished 注入 JS，签到页打开即查 getPPTActiveInfo 并提交 stuSignajax；**严格模式**——位置签到（ifopenAddress==1 或带坐标）跳过；每 activeId 去重
- **新增 位置签到自动完成**（开关：是否开启位置自动签到）：独立开关，用活动下发的靶心坐标（locationLatitude_gd/locationLongitude_gd 高德系优先）直接提交 → 距离 0 米；与手势开关互不冲突（防重按"模式+activeId"隔离）
- **新增 加密响应解密**（开关：是否开启响应解密）：installDecryptHook 拦 Cipher.doFinal，AES 系解密输出落 chaoxinghook_debug.log（AC ED 序列化提取可读串/文本直出）
- **新增 一键完成章节**（开关：是否开启一键完成章节）：knowledge/cards 注入"⚡一键完成本章"悬浮按钮——iframe 视频 静音+16x+跳尾（触发已有心跳伪造）、mArg 文档 type==3 补发 job/document、12s 自动刷新
- **UI 重组**：设置页分组"定位"→"签到"（定位修改/经纬度爆破/手势签到/位置签到），新增"章节与调试"组（加密响应解密/一键完成章节）
- 配置文件新增 4 键；功能总数 24 项；编译验证通过并已装机

## 2026-08-31 v1.0 之前（历史，见 git）

- 开屏/首页广告清理、聊天过滤、撤回阻断、分页扩展
- 播放器上报拦截、进度回退无视、视频心跳伪造、网课解锁
- 防切屏绕过、随机指纹、考试风控拦截、复制限制解除、考试截图替换
- 定位修改/经纬度爆破（三角定位）、地图选点（高德）、图片长按下载
- DexKit 抗混淆框架（结构匹配+ObfuscationMap 回退链）、MIUIX 设置页、配置 3 秒热读
