package com.owo233.fuckmarketads.hooks.market

import android.util.Log
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 禁止切换前台时展示的广告/推荐（主界面 MarketTabActivity 相关）。
 *
 * 对应 README 中的“禁止切换前台展示广告”：拦截 tryShowRecallReCommend、
 * tryShowRecommend、trySplash、fetchSearchHotList 等方法。
 */
object MainTabAds : BaseHook() {

    override val prefKey: String = Settings.KEY_MAIN_TAB

    override val name: String
        get() = "禁止前台广告/推荐"

    override fun init() {
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.main.MarketTabActivity")
                .methodFinder()
                .filter {
                    name in setOf(
                        "tryShowRecallReCommend",
                        "tryShowRecommend",
                        "trySplash",
                        "fetchSearchHotList"
                    )
                }.forEach { it.hooked { null } }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name 拦截失败", it) }
    }
}
