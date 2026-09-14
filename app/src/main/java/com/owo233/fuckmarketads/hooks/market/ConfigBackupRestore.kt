package com.owo233.fuckmarketads.hooks.market

import android.app.Application
import android.content.Context
import android.util.Log
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.io.File

/**
 * 自动备份商店 SharedPreferences 配置，并在检测到回滚（配置被清空/丢失）时自动恢复。
 *
 * 参考 lisrain/NewFuckMarketAds_Fork 的稳定性增强逻辑（v1.3.6）：
 *   - AppGlobals 在 hook 初始化阶段尚未就绪，因此延迟到 Application.onCreate() 执行；
 *   - 正常情况：备份主配置等关键 SharedPreferences 到文件；
 *   - 检测到回滚（主配置条目数 <= 1）：从备份恢复。
 *
 * 备份目录位于应用自身的 externalFilesDir 下，不额外申请权限。
 * 本模块为纯保护性安全网，由总开关统一门控；所有读写均包 try-catch，失败不影响商店运行。
 */
object ConfigBackupRestore : BaseHook() {

    private const val BACKUP_DIR_NAME = "market_config_backup"

    private val PREF_FILES_TO_BACKUP = listOf(
        "com.xiaomi.market_preferences", // 默认主配置
        "app_update",                     // 应用更新
        "self_update",                    // 自更新
        "host"                            // 服务器地址
    )

    override val name: String
        get() = "备份恢复商店配置"

    override fun init() {
        // AppGlobals.getContext() 在 hook 初始化阶段尚未就绪
        // 需要延迟到 Application.onCreate() 执行，此时 context 一定可用
        try {
            ClassUtil.loadClass("android.app.Application")
                .methodFinder()
                .filterByName("onCreate")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        val app = chain.thisObject as? Application
                        if (app != null) {
                            deferWork(app)
                        }
                        return@intercept chain.proceed()
                    }
                }
            HookEnv.base.log(Log.INFO, TAG, "ConfigBackupRestore: hook installed (deferred to onCreate)", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.ERROR, TAG, "ConfigBackupRestore: failed to hook Application.onCreate: ${e.message}", e)
        }
    }

    private fun deferWork(context: Context) {
        try {
            val backupDir = getBackupDir(context)
            val isRolledBack = detectRollback(context)

            if (isRolledBack) {
                HookEnv.base.log(
                    Log.WARN, TAG,
                    "Rollback detected — restoring config from backup",
                    null
                )
                restoreConfig(context, backupDir)
            } else {
                backupConfig(context, backupDir)
            }

            HookEnv.base.log(Log.INFO, TAG, "ConfigBackupRestore: done", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.ERROR, TAG, "ConfigBackupRestore: error: ${e.message}", e)
        }
    }

    private fun getBackupDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), BACKUP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /** 主配置条目数为空或仅剩 1 条时，判定为发生了回滚 */
    private fun detectRollback(context: Context): Boolean {
        val mainPrefs = context.getSharedPreferences(
            "com.xiaomi.market_preferences", Context.MODE_PRIVATE
        )
        return mainPrefs.all.isEmpty() || mainPrefs.all.size <= 1
    }

    private fun backupConfig(context: Context, backupDir: File) {
        for (prefName in PREF_FILES_TO_BACKUP) {
            try {
                val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val allEntries = prefs.all
                if (allEntries.isEmpty()) continue

                val backupFile = File(backupDir, "$prefName.json")
                val jsonBuilder = StringBuilder("{")
                var first = true
                for ((key, value) in allEntries) {
                    if (!first) jsonBuilder.append(",")
                    first = false
                    jsonBuilder.append("\"${escapeJson(key)}\":")
                    jsonBuilder.append(escapeJsonValue(value))
                }
                jsonBuilder.append("}")

                backupFile.writeText(jsonBuilder.toString())
                HookEnv.base.log(
                    Log.DEBUG, TAG,
                    "Backed up $prefName (${allEntries.size} entries)",
                    null
                )
            } catch (e: Exception) {
                HookEnv.base.log(
                    Log.ERROR, TAG,
                    "Failed to backup $prefName: ${e.message}",
                    null
                )
            }
        }
    }

    private fun restoreConfig(context: Context, backupDir: File) {
        for (prefName in PREF_FILES_TO_BACKUP) {
            try {
                val backupFile = File(backupDir, "$prefName.json")
                if (!backupFile.exists()) continue

                val jsonStr = backupFile.readText()
                if (jsonStr.isBlank() || jsonStr == "{}") continue

                val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                val entries = parseJson(jsonStr)

                for ((key, value) in entries) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        null -> { /* skip null values */ }
                    }
                }
                editor.apply()
                HookEnv.base.log(
                    Log.INFO, TAG,
                    "Restored $prefName (${entries.size} entries)",
                    null
                )
            } catch (e: Exception) {
                HookEnv.base.log(
                    Log.ERROR, TAG,
                    "Failed to restore $prefName: ${e.message}",
                    null
                )
            }
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun escapeJsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> "\"${escapeJson(value)}\""
            else -> "\"${escapeJson(value.toString())}\""
        }
    }

    /** 极简 JSON 解析：仅处理本模块自身写出的扁平 Entry，避免引入额外依赖 */
    private fun parseJson(json: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val trimmed = json.trim()
            .removePrefix("{")
            .removeSuffix("}")
            .trim()

        if (trimmed.isEmpty()) return result

        var i = 0
        while (i < trimmed.length) {
            while (i < trimmed.length && trimmed[i] == ' ') i++
            if (i >= trimmed.length || trimmed[i] != '"') break

            i++
            val keyBuilder = StringBuilder()
            while (i < trimmed.length && trimmed[i] != '"') {
                if (trimmed[i] == '\\' && i + 1 < trimmed.length) {
                    keyBuilder.append(trimmed[i + 1])
                    i += 2
                } else {
                    keyBuilder.append(trimmed[i])
                    i++
                }
            }
            if (i < trimmed.length) i++
            val key = keyBuilder.toString()

            while (i < trimmed.length && (trimmed[i] == ' ' || trimmed[i] == ':')) i++

            val value = readValue(trimmed, i)
            if (value != null) {
                result[key] = value.first
                i = value.second
            }

            while (i < trimmed.length && (trimmed[i] == ' ' || trimmed[i] == ',')) i++
        }

        return result
    }

    private fun readValue(str: String, start: Int): Pair<Any?, Int>? {
        if (start >= str.length) return null
        return when (str[start]) {
            '"' -> {
                var i = start + 1
                val builder = StringBuilder()
                while (i < str.length && str[i] != '"') {
                    if (str[i] == '\\' && i + 1 < str.length) {
                        builder.append(str[i + 1])
                        i += 2
                    } else {
                        builder.append(str[i])
                        i++
                    }
                }
                if (i < str.length) i++
                builder.toString() as Any to i
            }
            't' -> (true as Any) to (start + 4)
            'f' -> (false as Any) to (start + 5)
            'n' -> null to (start + 4)
            '-', in '0'..'9' -> {
                var i = start
                while (i < str.length && (str[i] in '0'..'9' || str[i] == '.' || str[i] == '-' || str[i] == 'e' || str[i] == 'E')) i++
                val numStr = str.substring(start, i)
                val numValue: Any = if (numStr.contains('.') || numStr.contains('e') || numStr.contains('E')) {
                    numStr.toDouble()
                } else {
                    val longVal = numStr.toLongOrNull()
                    if (longVal != null && longVal > Int.MAX_VALUE) {
                        longVal
                    } else {
                        numStr.toIntOrNull() ?: 0
                    }
                }
                numValue to i
            }
            else -> null
        }
    }
}
