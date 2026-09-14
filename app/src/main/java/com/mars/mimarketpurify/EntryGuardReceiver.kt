package com.mars.mimarketpurify

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 入口自愈。
 *
 * 旧版本把 MainActivity 自身承载了 MAIN+LAUNCHER，并在「隐藏桌面图标」时直接禁用它，
 * 导致框架无法解析到任何入口 —— 用户会被彻底锁在模块主页之外。
 *
 * 新版本中 MainActivity 只承载 CATEGORY_INFO（见 AndroidManifest.xml），永不被禁用；
 * 但「已被旧版本禁用」这一状态会随覆盖安装被系统保留下来，因此这里在收到
 * [Intent.ACTION_MY_PACKAGE_REPLACED] 时把 MainActivity 重新启用，完成恢复。
 */
class EntryGuardReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ensureEntryEnabled(context)
    }

    companion object {

        /**
         * 确保主页 Activity 处于启用状态。
         * 幂等且开销极小，重复调用无害。
         */
        fun ensureEntryEnabled(context: Context) {
            runCatching {
                val pm = context.packageManager
                val cn = ComponentName(context, MainActivity::class.java)
                if (pm.getComponentEnabledSetting(cn) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    pm.setComponentEnabledSetting(
                        cn,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}
