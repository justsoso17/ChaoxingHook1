---
标题: ChaoxingHook 全功能验证流程
日期: 2026-09-12
版本: 1.3
状态: 与 v1.11 代码同步（新增 §0.5 配置持久化自检）
tags: 验证, 测试, debug, 日志
关联: MODULE_NOTES.md, CHANGELOG.md
---

# 全功能验证流程（17 项）

## 0. 通用准备

| 项 | 要求 |
|---|---|
| 模块激活 | LSPosed 勾选模块 + 作用域 `com.chaoxing.mobile` |
| 更新模块后 | **必须强停学习通**（`adb shell am force-stop com.chaoxing.mobile`） |
| 学习通版本 | 7.0.1（适配基线，见 CHANGELOG v1.3） |
| 双账号需求 | 功能 7（撤回）需小号/同学配合 |
| 考试类 13-17 | 需真实考试入口；无考试时只能验证日志通路 |

**三个日志通道**（PC 端实时监听）：

```powershell
# ① 模块调试日志（功能内部状态，最可靠）
adb exec-out su -c "tail -f /data/user/0/com.chaoxing.mobile/files/chaoxinghook_debug.log"

# ② LSPosed 模块日志（hook 安装/触发明细）
adb exec-out su -c "tail -f /data/adb/lspd/log/modules_*.log | grep --line-buffered Chaoxing"

# ③ 界面验证 —— 人眼 + 截图（adb shell screencap -p /sdcard/s.png && adb pull ...）
```

配置开关文件：`/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt`
（推荐用模块设置页改，3 秒热读；改完无需重启）

> ⚠️ **v1.11 起：读写这个文件都必须走 root**（作用域存储禁止模块应用访问学习通的 `Android/data`，
> `File.exists()` 恒 false）。所以下面的 adb 命令一律带 `su -c`，且**必须先给模块 Root 权限**，
> 否则设置页会提示「无法读取配置」并拒绝保存（防止把已保存的配置覆盖成默认值）。

---

## 0.5 配置持久化自检（v1.11 修复项的回归项，**每次改动配置相关代码后必做**）

这是「退出软件后配置丢失」那个 bug 的回归检查，4 步必须全过：

| # | 操作 | 预期 |
|---|---|---|
| 1 | 设置页打开「定位修改」，填入 `116.397,39.908`，看到「配置已保存」 | Toast 为**保存成功**（若提示「未读取到配置，已跳过保存」= Root 没给，先点「申请 Root 权限」） |
| 2 | **完全退出设置页**（最近任务里划掉），重新打开 | 开关仍是**打开**、坐标仍是 `116.397/39.908`（修复前这里会变回默认值） |
| 3 | 核对磁盘文件：<br>`adb exec-out su -c "cat /storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt"` | `是否开启定位修改: true`、`经度: 116.397`、`纬度: 39.908` |
| 4 | 撤销 Root 授权（Magisk → 超级用户 → 拒绝）后重开设置页 | 弹「无法读取配置…当前显示默认值」，且**磁盘文件内容不变**（关键：读不到时绝不写盘） |

---

## 1. 启动自检（每次更新模块/学习通后必做）

冷启动学习通，看 **①+②** 日志，逐行核对：

| 日志行 | 含义 |
|---|---|
| `=== handleLoadPackage enter` | 模块已注入（加固前生效） |
| `loadLibrary dexkit OK` / `DexKitBridge.create OK` | DexKit 正常 |
| `db-query: clazz=y6e matchedMethods=2` | 功能 5 结构匹配命中 |
| `DexKit[chat-filter]: com.chaoxing.mobile.chat.manager.b` | 功能 6 命中 |
| `installWebViewHooks/FileReplaceHook/ExamSnapshotHook OK` | 各层挂载完成 |
| `=== handleLoadPackage done` | 装载结束 |

**任何 `[tag] 结构匹配失败` / `回退类不存在` / `方法未找到` 行 = 对应功能已失效**，按 MODULE_NOTES §4 流程重新定位新类名。

---

## 2. 常开功能（1-7、11、12，无需开关）

### 1. 开屏广告拦截
- 操作：强停后冷启动学习通
- 预期：直接进首页，无开屏广告
- 日志：`DexKit[splash]: ...`（启动时）+ `hooked` 行

### 2. 首页头部广告
- 操作：进首页下拉刷新几次
- 预期：首页顶部无 banner 广告位

### 3+4. 推荐分类卡片隐藏 / 主页记录去推荐位
- 操作：首页"记录/常用"区域上下看全
- 预期：无标题含"推荐"的分类卡片；无推荐占位条目

### 5. 记录分页 3→15
- 操作：首页看"最近使用"条数
- 日志：`db-query.W: pageSize 3 -> 15`（触发即 hook 生效）
- ⚠️ **语义**：只提高 LIMIT 上限不造数据——显示条数 ≤ 库中 `tb_new_resource_log` 表 `top_sign=0` 行数。显示少 ≠ 失效，用 SQL 对照：
  `SELECT COUNT(*) FROM tb_new_resource_log WHERE user_id=<当前uid> AND top_sign=0`

### 6. 聊天列表过滤
- 操作：打开消息页停留 10 秒
- 预期：列表无官方推送/服务号会话（type==20）
- 日志：`chat-filter: 移除 N 个 type==20 会话`（有才打；N=0 不打属正常）

### 7. 撤回消息阻断
- 操作：小号给你发消息后立即撤回
- 预期：消息仍可见，不显示"撤回了一条消息"
- 原理：环信 EMCmdMessageBody.action `REVOKE_FLAG→BLOCK_REVOKE_FLAG`

### 24. 长按「设置」直达模块主页（v1.8 新增）
- 操作：学习通「我」页长按"设置"行（约 0.5 秒）
- 预期：跳转到模块设置页；**原单击仍进学习通设置**，互不影响
- 注意：其他页面恰名为"设置"的可点击行同样生效

### 11. 网课解锁
- 操作：课程视频页直接拖到末尾、开倍速、切后台
- 预期：快进/倍速不被禁，切屏不暂停
- 日志：`[网课解锁]: 成功篡改最底层视频限制参数...`

### 12. 图片长按下载
- 操作：课程页/WebView 内长按图片
- 预期：弹"chaoxingHook"对话框 → 立即下载
- 佐证：`/sdcard/Download/ChaoxingExam/` 出现图片文件

---

## 3. 开关类功能（13-19，先在设置页开对应开关）

### 13. 防切屏绕过（开关：考试风控拦截）
- 操作：进考试页，切到桌面再回来（多次）
- 预期：无切屏警告弹窗
- 日志：`[防切屏]: CLIENT_WEB_LIFECYCLE status 已伪装为前台(11)`

### 14. 随机设备指纹（开关：随机指纹）
- 操作：开开关 → 清后台 → 进考试页
- 预期：考试页加载正常，指纹为随机值
- 佐证：同 URL 的 DEVICE_FLAG 每次清缓存后不同

### 15. 考试风控拦截（开关：考试风控拦截）
- 操作：进考试停留 + 触发切屏
- 日志：WebResource 假响应拦截（receiveExamLogs / exit-count / ac_event / pan-yz upload 四类 URL）
- 极端验证：`process-exit blocked: ...`（App 尝试自杀被拦，出现说明风控触发了退出）

### 16. 复制限制解除（开关：复制限制解除）
- 操作：考试/课程页长按文本复制
- 预期：可选中可复制
- 日志：`AdSkip: 成功拦截 notAllowCopy.css`

### 17. 考试截图替换（开关：考试截图替换 + 截图替换路径）
- 前置：`/sdcard/Download/fake_exam_image.png`（或自定义路径文件）存在
- 操作：有监考拍照的考试中触发截图上传
- 日志：`[绝杀]: 抓到监考截图上传！已成功替换...`
- ⚠️ 文件不存在时日志为 `[警告]: 找不到自定义伪装图片`

### 18. 定位修改（开关：定位修改 + 经纬度/地址/名字）
- 操作：设置坐标（可用地图选点）→ 打开位置签到页手动点签到
- 日志：`stuSignajax` 的 latitude/longitude 为配置值（改写后代发）
- 佐证：签到结果"距 X 米"按配置坐标计算

### 19. 经纬度爆破（开关：经纬度爆破；与 18 互斥）
- 操作：开开关 → 位置签到页连点签到 3 次
- 日志：`Xposed提示: 距靶心 X 米，采集进度(1/3)...(3/3)` → `目标坐标已锁定` → 最终打卡成功
- ⚠️ 三点共线会提示重新采集，属正常

---

## 4. 日志关键词 → 功能 对照表（排查用）

| 关键词 | 功能 |
|---|---|
| `DexKit[tag] 结构匹配失败` / `回退类不存在` | 对应 tag 的功能失效，需重新定位类名 |
| `方法未找到（类名或签名已变化）` | 类活着但方法没了 |
| `pageSize 3 -> 15` | 5 分页 |
| `chat-filter: 移除` | 6 聊天过滤 |
| `网课解锁` | 11 网课解锁 |
| `精准打击`（图片降维） | 12 图片下载 |
| `防切屏` | 13 防切屏 |
| `绝杀` / `找不到自定义伪装图片` | 17 截图替换 |
| `Xposed提示: 距靶心` | 19 经纬度爆破 |
| `process-exit blocked` | 15 风控退出阻断 |
| `Error` | 对应功能运行时异常，需看上下文 |

## 5. 快速冒烟流程（3 分钟版）

1. 强停 → 冷启动，核对启动自检日志（§1）
2. 首页：无广告（1/2）、无推荐位（3/4）、最近使用条数 = min(15, 库内条数)（5）
3. 消息页：无官方推送会话（6）
4. 进任一课程视频：可拖可倍速（11）
5. WebView 长按一张图片弹下载框（12）
