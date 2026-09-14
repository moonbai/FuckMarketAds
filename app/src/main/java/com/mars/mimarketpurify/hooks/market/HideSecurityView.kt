package com.mars.mimarketpurify.hooks.market

import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.ConstructorFinder.`-Static`.constructorFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object HideSecurityView : BaseHook() {

    override val prefKey: String = Settings.KEY_SECURITY

    override val name: String
        get() = "隐藏应用安全检查视图"

    override fun init() {
        ClassUtil.loadClass(
            "com.xiaomi.market.business_ui.main.mine.app_security.MineAppSecurityView"
        ).apply {
            constructorFinder().forEach { ctor ->
                ctor.hooked {
                    val result = proceed()
                    (thisObject as View).visibility = View.GONE
                    return@hooked result
                }
            }

            methodFinder().filterByName("checkSettingSwitch").first().hooked { false }

            methodFinder().filterByName("checkShown").first().hooked { false }
        }
    }
}
