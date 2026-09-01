package com.fredoseep.chaoxinghook

import java.io.File
import java.io.FileWriter

/**
 * 配置读写（与 MainHook 读取的 chaoxing_loc.txt 同格式）
 * 写入目标在学习通私有目录下，需要 root（su cp）——保持与原 Java 实现一致。
 */
object ConfigManager {

    const val CONFIG_PATH = "/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt"

    data class HookConfig(
        var modifyLocation: Boolean = false,
        var longitude: String = "",
        var latitude: String = "",
        var modifyAddress: Boolean = false,
        var address: String = "",
        var modifyName: Boolean = false,
        var name: String = "",
        var randomizeDeviceFlag: Boolean = false,
        var autoCalculateLocation: Boolean = false,
        var bypassExamCheat: Boolean = true,
        var enableCopyRestriction: Boolean = true,
        var replaceExamScreenshot: Boolean = false,
        var fakeImagePath: String = "",
    )

    fun load(): HookConfig {
        val config = HookConfig()
        val file = File(CONFIG_PATH)
        if (!file.exists()) {
            save(config)
            return config
        }
        try {
            file.bufferedReader().use { br ->
                br.lineSequence().forEach { line ->
                    val l = line.trim()
                    when {
                        l.startsWith("是否开启定位修改:") -> config.modifyLocation = parseBoolean(l)
                        l.startsWith("经度:") -> config.longitude = parseString(l)
                        l.startsWith("纬度:") -> config.latitude = parseString(l)
                        l.startsWith("是否开启地址名修改:") -> config.modifyAddress = parseBoolean(l)
                        l.startsWith("地址名:") -> config.address = parseString(l)
                        l.startsWith("是否开启名字修改:") -> config.modifyName = parseBoolean(l)
                        l.startsWith("名字:") -> config.name = parseString(l)
                        l.startsWith("是否开启随机指纹:") -> config.randomizeDeviceFlag = parseBoolean(l)
                        l.startsWith("是否开启经纬度爆破:") -> config.autoCalculateLocation = parseBoolean(l)
                        l.startsWith("是否开启考试风控拦截:") -> config.bypassExamCheat = parseBoolean(l)
                        l.startsWith("是否开启复制限制解除:") -> config.enableCopyRestriction = parseBoolean(l)
                        l.startsWith("是否开启考试截图替换:") -> config.replaceExamScreenshot = parseBoolean(l)
                        l.startsWith("截图替换路径:") -> config.fakeImagePath = parseString(l)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return config
    }

    fun save(config: HookConfig): Boolean {
        val content = buildString {
            append("是否开启定位修改: ").append(config.modifyLocation).append('\n')
            append("经度: ").append(config.longitude).append('\n')
            append("纬度: ").append(config.latitude).append('\n')
            append("是否开启地址名修改: ").append(config.modifyAddress).append('\n')
            append("地址名: ").append(config.address).append('\n')
            append("是否开启名字修改: ").append(config.modifyName).append('\n')
            append("名字: ").append(config.name).append('\n')
            append("是否开启随机指纹: ").append(config.randomizeDeviceFlag).append('\n')
            append("是否开启经纬度爆破: ").append(config.autoCalculateLocation).append('\n')
            append("是否开启考试风控拦截: ").append(config.bypassExamCheat).append('\n')
            append("是否开启复制限制解除: ").append(config.enableCopyRestriction).append('\n')
            append("是否开启考试截图替换: ").append(config.replaceExamScreenshot).append('\n')
            append("截图替换路径: ").append(config.fakeImagePath).append('\n')
        }
        return writeFileWithRoot(CONFIG_PATH, content)
    }

    fun reset(): HookConfig = HookConfig().also { save(it) }

    private fun writeFileWithRoot(path: String, content: String): Boolean {
        return try {
            val file = File(path)
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p ${parentDir.absolutePath}")).waitFor()
            }
            val tempFile = File.createTempFile("chaoxing_config", ".tmp")
            FileWriter(tempFile).use { it.write(content) }
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp ${tempFile.absolutePath} $path"))
            val exitCode = process.waitFor()
            tempFile.delete()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun parseBoolean(line: String): Boolean =
        try { line.substring(line.indexOf(":") + 1).trim().equals("true", ignoreCase = true) } catch (_: Exception) { false }

    private fun parseString(line: String): String =
        try { line.substring(line.indexOf(":") + 1).trim() } catch (_: Exception) { "" }
}
