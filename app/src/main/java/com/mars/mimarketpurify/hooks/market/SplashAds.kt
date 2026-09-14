package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 移除开屏广告。
 *
 * 修复点：原先开屏相关的多个方法 hook 写在一个大 init 里，一旦某个类/方法因商店更新
 * 而找不到，整段都会失败。这里把每个拦截点用 [runCatching] 隔离，单个失效不影响其余。
 * 每个拦截点通过 [com.mars.mimarketpurify.init.BaseHook.hooked] 受子开关实时控制。
 */
object SplashAds : BaseHook() {

    override val prefKey: String = Settings.KEY_SPLASH

    override val name: String
        get() = "移除开屏广告"

    override fun init() {
        // 底层 SplashManager
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.splash.SplashManager").apply {
                methodFinder()
                    .filter {
                        name in setOf(
                            "canShowSplash",
                            "needShowSplash",
                            "needRequestFocusVideo",
                            "isPassiveSplashAd"
                        )
                    }.forEach { it.hooked { false } }

                methodFinder()
                    .filter {
                        name in setOf(
                            "tryAdSplash",
                            "trySplashWhenApplicationForeground",
                            "preLoadSplashCover",
                            "shownSplashCoverIfNeed"
                        )
                    }.forEach { it.hooked { null } }
            }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: SplashManager 拦截失败", it) }

        // 详情页开屏广告管理
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.splash.DetailSplashAdManager").apply {
                methodFinder()
                    .filter {
                        name in setOf(
                            "canRequestSplashAd",
                            "isRequestApprovedByServer",
                            "isOpenFromMsa",
                            "isRequesting"
                        )
                    }.forEach { it.hooked { false } }

                methodFinder()
                    .filterByName("tryToRequestSplashAd")
                    .first()
                    .hooked { null }
            }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: DetailSplashAdManager 拦截失败", it) }
    }
}
