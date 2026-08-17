package com.fredoseep.chaoxinghook

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * MIUIX（HyperOS 风格）设置页
 * 覆盖原 XML 设置页全部功能：定位修改/经纬度爆破（互斥）、地址/名字修改、
 * 随机指纹、考试风控拦截、复制限制解除、考试截图替换、地图选点、实时保存、重置。
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val controller = remember { ThemeController(ColorSchemeMode.System) }

    var config by remember { mutableStateOf(ConfigManager.load()) }
    val currentConfig by rememberUpdatedState(config)

    fun update(block: ConfigManager.HookConfig.() -> Unit) {
        // 必须 copy() 创建新实例：mutableStateOf 按引用比较，apply 原地修改不触发重组
        config = config.copy().apply(block)
        val ok = ConfigManager.save(config)
        Toast.makeText(context, if (ok) "配置已保存" else "保存失败，可能需要 Root", Toast.LENGTH_SHORT).show()
    }

    val mapPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val lat = result.data!!.getDoubleExtra("latitude", 0.0)
            val lng = result.data!!.getDoubleExtra("longitude", 0.0)
            update {
                latitude = lat.toString()
                longitude = lng.toString()
            }
        }
    }

    fun launchMapPicker() {
        val intent = Intent(context, MapPickerActivity::class.java)
        mapPickerLauncher.launch(intent)
    }

    MiuixTheme(controller = controller) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = "ChaoxingHook 设置",
                )
            },
        ) { innerPadding ->
            LazyColumn(contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
            )) {

                // ============ 定位 ============
                item { SmallTitle(text = "定位") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            title = "定位修改",
                            summary = "打卡/签到提交自定义经纬度",
                            checked = config.modifyLocation,
                            onCheckedChange = { on ->
                                if (on) update { autoCalculateLocation = false }
                                update { modifyLocation = on }
                            },
                        )
                        AnimatedVisibility(visible = currentConfig.modifyLocation) {
                            CoordinateFields(
                                latitude = currentConfig.latitude,
                                longitude = currentConfig.longitude,
                                onLatitudeChange = { update { latitude = it } },
                                onLongitudeChange = { update { longitude = it } },
                                onMapPick = ::launchMapPicker,
                            )
                        }
                        SwitchPreference(
                            title = "经纬度爆破",
                            summary = "通过三点距离自动逼近目标坐标",
                            checked = config.autoCalculateLocation,
                            onCheckedChange = { on ->
                                if (on) update { modifyLocation = false }
                                update { autoCalculateLocation = on }
                            },
                        )
                        AnimatedVisibility(visible = currentConfig.autoCalculateLocation) {
                            CoordinateFields(
                                latitude = currentConfig.latitude,
                                longitude = currentConfig.longitude,
                                onLatitudeChange = { update { latitude = it } },
                                onLongitudeChange = { update { longitude = it } },
                                onMapPick = ::launchMapPicker,
                            )
                        }
                    }
                }

                // ============ 信息修改 ============
                item { SmallTitle(text = "信息修改") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            title = "地址名修改",
                            summary = "签到提交自定义地址名",
                            checked = config.modifyAddress,
                            onCheckedChange = { update { modifyAddress = it } },
                        )
                        AnimatedVisibility(visible = currentConfig.modifyAddress) {
                            SingleTextField(
                                value = currentConfig.address,
                                label = "地址名",
                                onValueChange = { update { address = it } },
                            )
                        }
                        SwitchPreference(
                            title = "名字修改",
                            summary = "签到提交自定义名字",
                            checked = config.modifyName,
                            onCheckedChange = { update { modifyName = it } },
                        )
                        AnimatedVisibility(visible = currentConfig.modifyName) {
                            SingleTextField(
                                value = currentConfig.name,
                                label = "名字",
                                onValueChange = { update { name = it } },
                            )
                        }
                    }
                }

                // ============ 风控与考试 ============
                item { SmallTitle(text = "风控与考试") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            title = "随机指纹",
                            summary = "每次注入随机的设备指纹（CLIENT_DEVICE_FLAG）",
                            checked = config.randomizeDeviceFlag,
                            onCheckedChange = { update { randomizeDeviceFlag = it } },
                        )
                        SwitchPreference(
                            title = "考试风控拦截",
                            summary = "拦截考试日志上报与切出计数（含防切屏 status 伪装）",
                            checked = config.bypassExamCheat,
                            onCheckedChange = { update { bypassExamCheat = it } },
                        )
                        SwitchPreference(
                            title = "复制限制解除",
                            summary = "拦截 notAllowCopy.css，允许网页复制",
                            checked = config.enableCopyRestriction,
                            onCheckedChange = { update { enableCopyRestriction = it } },
                        )
                        SwitchPreference(
                            title = "考试截图替换",
                            summary = "监考截图上传时替换为指定图片",
                            checked = config.replaceExamScreenshot,
                            onCheckedChange = { update { replaceExamScreenshot = it } },
                        )
                        AnimatedVisibility(visible = currentConfig.replaceExamScreenshot) {
                            SingleTextField(
                                value = currentConfig.fakeImagePath,
                                label = "截图替换路径",
                                onValueChange = { update { fakeImagePath = it } },
                            )
                        }
                    }
                }

                // ============ 其他 ============
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "重置所有配置",
                            summary = "恢复默认值并立即保存",
                            onClick = {
                                config = ConfigManager.reset()
                                Toast.makeText(context, "配置已重置", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 经度/纬度输入 + 地图选点（两处复用：普通定位与爆破）
 *  注意：必须包 Column —— AnimatedVisibility 内多个并列组件会重叠堆叠 */
@Composable
private fun CoordinateFields(
    latitude: String,
    longitude: String,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onMapPick: () -> Unit,
) {
    Column {
        SingleTextField(
            value = longitude,
            label = "经度",
            onValueChange = onLongitudeChange,
        )
        SingleTextField(
            value = latitude,
            label = "纬度",
            onValueChange = onLatitudeChange,
        )
        ArrowPreference(
            title = "地图选点",
            summary = "打开地图选择精确坐标",
            onClick = onMapPick,
        )
    }
}

/** 带清空按钮的单行输入框 */
@Composable
private fun SingleTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = TextFieldValue(value),
        onValueChange = { onValueChange(it.text) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        label = label,
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clickable { onValueChange("") },
                )
            }
        },
    )
}
