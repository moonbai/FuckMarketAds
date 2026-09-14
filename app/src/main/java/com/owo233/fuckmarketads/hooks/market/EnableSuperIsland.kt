package com.owo233.fuckmarketads.hooks.market

import android.util.Log
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.Locale

/**
 * 强制启用“下载进度通知进入小米超级岛”。
 *
 * 参考 lisrain/NewFuckMarketAds_Fork 的实现思路（v1.3.7）：
 *   云控灰度链路：
 *     InstallChecker.requestIslandMsg() -> 服务端 SHOW_ISLAND_URL
 *     -> 响应 showIslandMessage 写入 RefInfo 控制参数
 *     -> DownloadInstallInfo.getShowIslandMsg()
 *     -> MarketIslandManager.isShowIsland() 闩锁 is_show_island_notification
 *     -> determineNotificationState() 构建带 MIUI_FOCUS_* 的岛通知
 *
 * hook 策略（无视服务端灰度，双点位兜底）：
 *   1. getShowIslandMsg 恒为 true（数据面唯一读取点）；
 *   2. isShowIsland 恒为 true（渲染收口点兜底）。
 *
 * 本功能受 [Settings.KEY_ISLAND] 开关控制，可在模块主页单独启用 / 停用。
 */
object EnableSuperIsland : BaseHook() {

    override val prefKey: String = Settings.KEY_ISLAND

    private const val DOWNLOAD_INFO = "com.xiaomi.market.business_core.downloadinstall.data.DownloadInstallInfo"
    private const val ISLAND_MANAGER = "com.xiaomi.market.business_ui.island.MarketIslandManager"

    override val name: String
        get() = "强制启用下载超级岛"

    override fun init() {
        hookShowIslandMsg()
        hookIsShowIsland()
    }

    private fun hookShowIslandMsg() {
        try {
            val clazz = ClassUtil.loadClass(DOWNLOAD_INFO)
            val method = clazz.methodFinder().filterByName("getShowIslandMsg").firstOrNull()
                ?: clazz.methodFinder().firstOrNull {
                    parameterTypes.isEmpty() &&
                        returnType == java.lang.Boolean::class.java &&
                        name.lowercase(Locale.ROOT).contains("island")
                }
            if (method == null) {
                HookEnv.base.log(Log.WARN, TAG, "[EnableSuperIsland] getShowIslandMsg not found", null)
                return
            }
            method.hooked { true }
            HookEnv.base.log(Log.DEBUG, TAG, "[EnableSuperIsland] hooked DownloadInstallInfo.getShowIslandMsg(${method.name})", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[EnableSuperIsland] hook getShowIslandMsg failed: ${e.message}", null)
        }
    }

    private fun hookIsShowIsland() {
        try {
            val clazz = ClassUtil.loadClass(ISLAND_MANAGER)
            val method = clazz.methodFinder().filterByName("isShowIsland").firstOrNull()
                ?: clazz.methodFinder().firstOrNull {
                    returnType == Boolean::class.javaPrimitiveType &&
                        parameterTypes.size == 1 &&
                        List::class.java.isAssignableFrom(parameterTypes[0]) &&
                        name.lowercase(Locale.ROOT).contains("island")
                }
            if (method == null) {
                HookEnv.base.log(Log.WARN, TAG, "[EnableSuperIsland] isShowIsland not found", null)
                return
            }
            method.hooked { true }
            HookEnv.base.log(Log.DEBUG, TAG, "[EnableSuperIsland] hooked MarketIslandManager.isShowIsland(${method.name})", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[EnableSuperIsland] hook isShowIsland failed: ${e.message}", null)
        }
    }
}
