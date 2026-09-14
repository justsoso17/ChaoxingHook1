package com.fredoseep.chaoxinghook

/**
 * 配置读写（与 MainHook 读取的 chaoxing_loc.txt 同格式）。
 *
 * ## 为什么读写都必须走 root
 *
 * 目标文件在学习通**自己的**私有外部目录
 * `/storage/emulated/0/Android/data/com.chaoxing.mobile/files/`。
 * 本模块（targetSdk 35、未声明任何存储权限）受作用域存储约束：
 * Android 11+ 起系统禁止应用访问**其他应用**的 `Android/data` —— 该限制连
 * `MANAGE_EXTERNAL_STORAGE` 都绕不过去，表现为 `File.exists()` 恒返回 false、
 * `File.bufferedReader()` 抛 EACCES。
 *
 * 因此本类**完全不使用 File API 读写配置**，一律通过 `su -c` 完成。
 *
 * ## 历史 bug（「退出软件后配置丢失」的根因）
 *
 * 早期 `load()` 用 `File.exists()` 判断文件是否存在。真机上它**恒为 false**，于是：
 *
 * 1. 每次打开设置页都走进「文件不存在 → 写默认模板」分支，
 *    把磁盘上用户已保存的配置**覆盖成默认值**；
 * 2. 紧接着 `bufferedReader()` 又抛 EACCES 被空 catch 吞掉，返回的仍是默认值。
 *
 * 结果：设置页永远显示默认值，用户改完退出再进就「变回默认」—— 而写入本身是走 su 的，
 * 学习通那边确实短暂读到过正确值，所以这个 bug 一直没被察觉。
 *
 * 修复要点：**只有 root 明确回答「文件不存在」时才允许写默认值**，
 * 读取失败一律不碰磁盘（见 [loadFailed]）。
 */
object ConfigManager {

    const val CONFIG_PATH = "/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt"

    /** 考试截图替换的兜底路径，与 MainHook.FAKE_UPLOAD_FILE_PATH 对应 */
    const val DEFAULT_FAKE_IMAGE_PATH = "/storage/emulated/0/Download/fake_exam_image.png"

    /** root 侧探测到文件不存在时回显的哨兵，用于把「不存在」和「空文件」都归为 [ReadResult.Absent] */
    private const val ABSENT_MARKER = "__CXH_CONFIG_ABSENT__"

    /**
     * 配置读取结果。
     *
     * **必须**把「文件真的不存在」和「读不到（没 root / su 被拒）」分开：
     * 前者可以安全地写默认模板，后者若写盘就会清空用户配置。
     */
    private sealed interface ReadResult {
        /** 正常读到内容 */
        data class Ok(val text: String) : ReadResult

        /** root 明确回答：文件不存在（或被清空）—— 可以写默认模板 */
        data object Absent : ReadResult

        /** 拿不到 root / su 执行失败 —— **绝不能**据此写盘 */
        data object Failed : ReadResult
    }

    /**
     * 上次 [load] 是否失败（多半是没给 root）。
     *
     * 失败期间内存里只是「默认值」而非磁盘真实内容，此时 [save] 会把默认值写下去、
     * 等于清空用户配置，所以必须拒绝落盘。
     */
    @Volatile
    var loadFailed: Boolean = false
        private set

    /**
     * hook 行为开关。
     *
     * 默认值必须与 MainHook 的**默认文件模板**（`getSignConfig()` 里那条 write）逐项一致 ——
     * 两边都能创建这个文件，默认值不一致就会变成「谁先创建谁说了算」。
     * 现存两处已对齐：`randomizeDeviceFlag`（此处 false → true）、
     * `fakeImagePath`（此处 "" → [DEFAULT_FAKE_IMAGE_PATH]）。
     */
    data class HookConfig(
        var modifyLocation: Boolean = false,
        var longitude: String = "",
        var latitude: String = "",
        var modifyAddress: Boolean = false,
        var address: String = "",
        var modifyName: Boolean = false,
        var name: String = "",
        var randomizeDeviceFlag: Boolean = true,
        var autoCalculateLocation: Boolean = false,
        var bypassExamCheat: Boolean = true,
        var enableCopyRestriction: Boolean = true,
        var replaceExamScreenshot: Boolean = false,
        var fakeImagePath: String = DEFAULT_FAKE_IMAGE_PATH,
    )

    /**
     * 读取配置。
     *
     * - 读到内容 → 解析后返回；
     * - root 确认文件不存在 → 写一份默认模板并返回默认值（**仅此一条分支允许写默认值**）；
     * - 读取失败 → 置 [loadFailed] 并返回内存默认值，**磁盘原样不动**。
     */
    fun load(): HookConfig = when (val result = readViaRoot(CONFIG_PATH)) {
        is ReadResult.Ok -> {
            loadFailed = false
            parseConfig(result.text)
        }

        ReadResult.Absent -> {
            loadFailed = false
            writeViaRoot(CONFIG_PATH, serialize(HookConfig()))
            HookConfig()
        }

        ReadResult.Failed -> {
            loadFailed = true
            HookConfig()
        }
    }

    /**
     * 保存配置。
     *
     * @return 是否写入成功；[loadFailed] 为 true 时直接返回 false（读不到就不写，防止覆盖）
     */
    fun save(config: HookConfig): Boolean {
        if (loadFailed) return false
        return writeViaRoot(CONFIG_PATH, serialize(config))
    }

    /**
     * 恢复默认值并落盘。
     *
     * 这是用户在确认框里显式点过的破坏性操作，因此**不受** [loadFailed] 保护 ——
     * 读不到配置时「重置」仍应把文件写成默认值。
     *
     * @return 是否写入成功
     */
    fun reset(): Boolean {
        val ok = writeViaRoot(CONFIG_PATH, serialize(HookConfig()))
        if (ok) loadFailed = false
        return ok
    }

    // ==================== root I/O ====================

    /**
     * 经 `su -c` 读取目标文件。
     *
     * 脚本在 **root 侧**用 `[ -f ]` 判断存在性（本应用自己的 `File.exists()` 在作用域存储下不可信），
     * 并把 stderr 并进 stdout —— 只留一条管道，避免「子进程写满 stderr 导致父进程读 stdout 时死锁」。
     */
    private fun readViaRoot(path: String): ReadResult {
        return try {
            val script = "exec 2>&1; if [ -f \"$path\" ]; then cat \"$path\"; else echo \"$ABSENT_MARKER\"; fi"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))

            // 先读完 stdout（含合并进来的 stderr）再 waitFor：配置很小，不会撑满管道
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            val text = output.trim()

            when {
                exitCode != 0 -> ReadResult.Failed
                text.isEmpty() || text == ABSENT_MARKER -> ReadResult.Absent
                // 每行都是「键: 值」，一个冒号都没有说明拿到的不是配置（例如 su 的报错文本）
                !text.contains(':') -> ReadResult.Failed
                else -> ReadResult.Ok(output)
            }
        } catch (_: Exception) {
            ReadResult.Failed
        }
    }

    /**
     * 经 `su -c` 写入目标文件。
     *
     * 实现要点：
     * - 内容经 **stdin** 送进 root 侧，不再落一个本应用私有目录里的临时文件 ——
     *   既避开 `java.io.tmpdir` 在不同 ROM 上指向不一致的问题，
     *   也不再要求 root 能读本应用的私有缓存目录；
     * - 先写 `.new` 再 `mv -f` **原子替换**（同目录、同文件系统），
     *   学习通不会读到写了一半的配置；`mv` 不被允许时回退到 `cat > 目标`（旧实现就是 cp，
     *   这里保留兜底，避免"新写法在个别 ROM 上比旧的更容易失败"）;
     * - `@Synchronized` 串行化：输入框每敲一个字符都会触发一次保存，
     *   并发 spawn 多个 su 时完成顺序可能倒挂，导致最后落盘的是较早那次输入。
     */
    @Synchronized
    private fun writeViaRoot(path: String, content: String): Boolean {
        return try {
            val dir = path.substringBeforeLast('/')
            val tmp = "$path.new"
            val script = "exec 2>&1; mkdir -p \"$dir\" && cat > \"$tmp\" && " +
                    "{ if mv -f \"$tmp\" \"$path\"; then :; else cat \"$tmp\" > \"$path\" && rm -f \"$tmp\"; fi; }"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))

            process.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }

            // 必须排空子进程输出：管道写满会让 su 永远阻塞在 waitFor() 上（表现为「保存卡死」）
            process.inputStream.bufferedReader().use { it.readText() }

            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 序列化 ====================

    /** 顺序与 MainHook 的默认模板、解析分支逐字对应，勿随意调整 */
    private fun serialize(config: HookConfig): String = buildString {
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

    private fun parseConfig(text: String): HookConfig {
        val config = HookConfig()
        text.lineSequence().forEach { raw ->
            val l = raw.trim()
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
        return config
    }

    private fun parseBoolean(line: String): Boolean =
        try { line.substring(line.indexOf(":") + 1).trim().equals("true", ignoreCase = true) } catch (_: Exception) { false }

    private fun parseString(line: String): String =
        try { line.substring(line.indexOf(":") + 1).trim() } catch (_: Exception) { "" }
}
