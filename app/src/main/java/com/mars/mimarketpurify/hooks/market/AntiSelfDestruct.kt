package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.libxposed.api.XposedInterface
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 防止应用商店因连续崩溃而“自我卸载 / 自毁”，并主动重置崩溃计数器。
 *
 * 参考 lisrain/NewFuckMarketAds_Fork 的稳定性增强逻辑（v1.3.6）：
 *   - 拦截 PackageManagerCompat.deletePackage()：若目标是商店自身包名，直接放行空结果，阻断自毁；
 *   - 拦截 SelfUpdateService.isValidVersion()：始终返回 true，使旧版本也能正常更新回来，打破死锁；
 *   - 在 hook 初始化阶段重置商店的“未捕获异常计数”（uncaught_exception_file），从源头消除自毁触发条件。
 *
 * 本模块为纯保护性安全网，不受单个功能开关控制（由总开关统一门控），
 * 每个 hook 点均包 try-catch，单个失败不影响其他 hook。
 */
object AntiSelfDestruct : BaseHook() {

    override val name: String
        get() = "防止商店崩溃自毁"

    override fun init() {
        // 先重置崩溃计数器（从源头消除自毁触发条件）
        resetCrashCounter()
        hookDeletePackage()
        hookIsValidVersion()
        HookEnv.base.log(Log.INFO, TAG, "AntiSelfDestruct hooks installed", null)
    }

    /**
     * 拦截 PackageManagerCompat.deletePackage()
     * 如果是删除自身包，直接阻止，防止应用商店因连续崩溃被自毁。
     */
    private fun hookDeletePackage() {
        try {
            ClassUtil.loadClass(
                "com.xiaomi.market.common.compat.PackageManagerCompat"
            ).methodFinder()
                .filterByName("deletePackage")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain: XposedInterface.Chain ->
                        val packageName = chain.args[0] as? String ?: return@intercept chain.proceed()
                        val currentPkg = try {
                            ClassUtil.loadClass("com.xiaomi.market.AppGlobals")
                                .methodFinder()
                                .filterByName("getPkgName")
                                .first()
                                .invoke(null) as? String
                        } catch (_: Exception) { null }

                        if (packageName == currentPkg) {
                            HookEnv.base.log(
                                Log.WARN, TAG,
                                "Blocked self-deletion of $packageName",
                                null
                            )
                            return@intercept null
                        }

                        return@intercept chain.proceed()
                    }
                }
        } catch (e: Exception) {
            HookEnv.base.log(Log.ERROR, TAG, "Failed to hook deletePackage: ${e.message}", e)
        }
    }

    /**
     * 拦截 SelfUpdateService.isValidVersion()
     * 始终返回 true，使商店不会因 invalidVersion 文件拒绝更新。
     * 即使被回滚到旧版本，也能正常更新回来，打破死锁。
     */
    private fun hookIsValidVersion() {
        try {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_core.update.self.SelfUpdateService"
            ).methodFinder()
                .filterByName("isValidVersion")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { true }
                }
        } catch (e: Exception) {
            HookEnv.base.log(Log.ERROR, TAG, "Failed to hook isValidVersion: ${e.message}", e)
        }
    }

    /**
     * 重置商店的崩溃计数器（uncaught_exception_file 中的 exceptionTimes_<versionCode>）。
     * 计数器过高会触发商店自我卸载，这里在 hook 初始化时清零，从源头阻断自毁。
     */
    @Suppress("DEPRECATION") // versionCode 在高版本仍有值（minSdk 29），此处按 int 使用即可
    private fun resetCrashCounter() {
        try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentApp = atClass.getMethod("currentApplication").invoke(null) as? android.content.Context
                ?: return
            val pkgInfo = currentApp.packageManager.getPackageInfo(currentApp.packageName, 0)
            val key = "exceptionTimes_${pkgInfo.versionCode}"
            val prefs = currentApp.getSharedPreferences("uncaught_exception_file", 0)
            val count = prefs.getInt(key, 0)
            if (count > 0) {
                prefs.edit().putInt(key, 0).commit()
                HookEnv.base.log(Log.INFO, TAG, "Crash counter reset: was $count for version ${pkgInfo.versionCode}", null)
            }
        } catch (e: Exception) {
            HookEnv.base.log(Log.ERROR, TAG, "Failed to reset crash counter: ${e.message}", null)
        }
    }
}
