package com.fredoseep.chaoxinghook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * 设置页入口：MIUIX（HyperOS 风格）Compose UI
 * 类名与 AndroidManifest 保持一致，原 XML 布局实现已由 Compose 重写。
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen()
        }
    }
}
