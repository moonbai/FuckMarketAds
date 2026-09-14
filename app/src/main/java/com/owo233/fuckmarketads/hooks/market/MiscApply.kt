package com.owo233.fuckmarketads.hooks.market

import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object MiscApply : BaseHook() {

    override val prefKey: String = Settings.KEY_MISC

    override val name: String
        get() = "部分细节处理"

    override fun init() {
        ClassUtil.loadClass("com.xiaomi.market.model.LocalAppInfo").apply {
            methodFinder()
                .filterByName("isInstalledByMarket")
                .first()
                .hooked { true }

            methodFinder()
                .filterByName("getInstallerSourceInUpdateInterface")
                .first()
                .hooked { "0" }
        }

        ClassUtil.loadClass("com.xiaomi.market.model.AppInfo").apply {
            methodFinder()
                .filterByName("maybeStolenApp") // 显示被篡改签名的APP
                .first()
                .hooked { false }

            methodFinder()
                .filterByName("isSignatureInconsistent") // 禁用签名不一致检查
                .first()
                .hooked { false }

            methodFinder()
                .filterByName("isDownloadDisable") // 允许下载被禁用的APP
                .first()
                .hooked { false }

            methodFinder()
                .filterByName("isInconsistentUpdate") // 禁用更新不一致检查
                .first()
                .hooked { false }

            methodFinder()
                .filterByName("shouldHideAutoUpdate") // 显示被隐藏的自动更新
                .first()
                .hooked { false }
        }
    }
}
