package com.fredoseep.chaoxinghook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * 三套 UI 风格（MIUIX / Nuke / Material 3）共用的设置页外壳。
 *
 * 业务逻辑只写一份：这里持有 [ConfigManager.HookConfig] 与「实时保存」流程，
 * 各风格实现只负责把状态渲染成自己的组件语言（行模型见 [HookSettingsRows]）。
 */

/**
 * 设置页数据源。所有变更都立刻落盘（写 `chaoxing_loc.txt`，需 root 时用 su cp）。
 *
 * 用 [MutableState] 持有配置：hook 配置是 data class，修改必须走 `copy()` ——
 * `mutableStateOf` 按引用比较，原地 apply 不会触发重组。
 */
class HookSettingsState internal constructor(
    private val context: Context,
    initial: ConfigManager.HookConfig,
) {
    internal val configState: MutableState<ConfigManager.HookConfig> = mutableStateOf(initial)

    val config: ConfigManager.HookConfig get() = configState.value

    /** 改一项就存一次；保存失败（通常是没 root）会明确提示，不再静默 */
    fun update(block: ConfigManager.HookConfig.() -> Unit) {
        val next = configState.value.copy().apply(block)
        configState.value = next
        val ok = ConfigManager.save(next)
        Toast.makeText(
            context,
            if (ok) "配置已保存" else "保存失败，可能需要 Root",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun reset() {
        configState.value = ConfigManager.reset()
        Toast.makeText(context, "配置已重置", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 记住一份设置页状态。
 *
 * 注意：**不要**把 `rememberSaveable` 用在这里 —— 配置里同时存在开关与文本，
 * 而 `rememberSaveable` 恢复的是「重建前那一刻的快照」，会把切换 UI 风格前
 * 刚输入还没落盘的文本又覆盖回来。重建后从文件重读才是唯一事实来源。
 */
@Composable
fun rememberHookSettingsState(): HookSettingsState {
    val context = LocalContext.current
    return remember { HookSettingsState(context, ConfigManager.load()) }
}

/**
 * 设置页的公共依赖：应用列表权限 + Root 申请 + 地图选点回填。
 *
 * 权限申请走 Activity Result API，必须挂在 `@Composable` 上，
 * 所以三套风格各调用一次本函数即可，逻辑仍然只有一份。
 */
class SettingsScaffold internal constructor(
    /** 申请 Root：未授予应用列表权限时先弹系统权限弹窗 */
    val requestRoot: () -> Unit,
    /** 打开高德地图选点；返回后自动回填经纬度 */
    val launchMapPicker: () -> Unit,
)

/**
 * 创建 [SettingsScaffold]。
 *
 * @param settings 状态对象；地图选点回填会直接写进它
 */
@Composable
fun rememberSettingsScaffold(settings: HookSettingsState): SettingsScaffold {
    val context = LocalContext.current

    // ==================== Root / 应用列表权限 ====================
    // 工信部规范权限（ColorOS/MIUI 等国产 ROM 定义），未授权时申请 Root 前需先弹窗申请
    val appListPermission = "com.android.permission.GET_INSTALLED_APPS"

    /**
     * Magisk 是否安装（Manifest 的 queries 已声明其包名保证可见性；
     * Magisk 隐藏包名时检测不到，走「未检测到 Magisk」兜底提示）
     */
    fun isMagiskInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("com.topjohnwu.magisk", 0)
        true
    } catch (_: Exception) {
        false
    }

    fun requestRoot() {
        // su 请求会阻塞等待用户在 Magisk 弹窗中确认，必须放后台线程
        Thread {
            val output = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                out
            } catch (_: Exception) {
                ""
            }
            Handler(Looper.getMainLooper()).post {
                when {
                    // su -c id 成功且确为 uid=0(root) 才算授权
                    output.contains("uid=0") ->
                        Toast.makeText(context, "已获得 Root 授权", Toast.LENGTH_SHORT).show()
                    // Magisk 在但被拒：多半是之前勾过"记住拒绝"，或超级用户列表里策略为拒绝
                    isMagiskInstalled() ->
                        Toast.makeText(
                            context,
                            "Root 申请未通过：请打开 Magisk → 超级用户，允许本应用（若勾选过\"记住拒绝\"需先删除该记录）",
                            Toast.LENGTH_LONG,
                        ).show()
                    else ->
                        Toast.makeText(context, "未检测到 Magisk，无法申请 Root", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    val appListPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // ColorOS 16 等 ROM 的授权结果可能不反映在标准回调中，以实际权限状态为准
        val nowGranted = try {
            context.checkSelfPermission(appListPermission) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        if (nowGranted) {
            // 应用列表权限到手，继续申请 Root
            requestRoot()
        } else {
            Toast.makeText(
                context,
                "未获得应用列表权限，请到系统设置 → 应用 → ChaoxingHook → 权限 中手动开启",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** 应用列表权限已授权（或 ROM 不管控）→ 直接申请 Root；否则先弹应用列表权限申请 */
    fun requestRootOrAppList() {
        val defined = try {
            context.packageManager.getPermissionInfo(appListPermission, 0); true
        } catch (_: Exception) {
            false
        }
        val hasAppList = try {
            context.checkSelfPermission(appListPermission) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        if (defined && !hasAppList) {
            appListPermissionLauncher.launch(appListPermission)
        } else {
            requestRoot()
        }
    }

    // ==================== 地图选点 ====================
    val mapPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val lat = result.data!!.getDoubleExtra("latitude", 0.0)
            val lng = result.data!!.getDoubleExtra("longitude", 0.0)
            settings.update {
                latitude = lat.toString()
                longitude = lng.toString()
            }
        }
    }

    fun launchMapPicker() {
        mapPickerLauncher.launch(Intent(context, MapPickerActivity::class.java))
    }

    // 局部函数每次重组都是新实例，用 rememberUpdatedState 兜住最新引用，
    // 这样返回出去的 SettingsScaffold 能稳定 remember（不需要把函数当 remember key）
    val currentRequestRoot by rememberUpdatedState { requestRootOrAppList() }
    val currentLaunchMapPicker by rememberUpdatedState { launchMapPicker() }

    return remember {
        SettingsScaffold(
            requestRoot = { currentRequestRoot() },
            launchMapPicker = { currentLaunchMapPicker() },
        )
    }
}

/**
 * 当前风格：整屏只读一次。
 *
 * 切风格会重建 Activity，重建后 composition 从零开始，所以这里读到的永远是最终值；
 * 用 [remember] 而不是监听 SharedPreferences，可以避免重建瞬间出现两种风格混排的闪帧。
 */
@Composable
fun rememberCurrentUiStyle(): UiStyle {
    val context = LocalContext.current
    return remember { SettingStore.loadUiStyle(context) }
}

/**
 * 切换 UI 风格：先落盘，再重建 Activity。
 *
 * Compose 的主题（MIUIX / Nuke / Material 3）无法就地热替换，重建是最省事也最可靠的做法；
 * 配置是实时保存的，重建不会丢数据。
 *
 * 返回一个 `(UiStyle) -> Unit`，由各风格的选择控件调用。用 [rememberCurrentUiStyle] 拿到当前值，
 * 选中值相同就不做任何事（避免点当前项也白重建一次）。
 */
@Composable
fun rememberUiStyleSwitcher(onSwitched: () -> Unit = {}): (UiStyle) -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity
    val current by rememberUpdatedState(SettingStore.loadUiStyle(context))
    val currentOnSwitched by rememberUpdatedState(onSwitched)
    return remember(activity) {
        { style: UiStyle ->
            if (style != current) {
                SettingStore.saveUiStyle(context, style)
                currentOnSwitched()
                Toast.makeText(context, "已切换到 ${style.label} 风格", Toast.LENGTH_SHORT).show()
                activity?.recreate()
            }
        }
    }
}

/**
 * 风格切换的选项列表（各风格的选择控件都用它，保证三处文案一致）。
 */
val uiStyleOptions: List<UiStyle> get() = UiStyle.entries.toList()
