package com.owo233.fuckmarketads.hooks.market

import android.util.Log
import android.view.View
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.BaseHook
import com.owo233.fuckmarketads.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 隐藏主页信息流广告低栏 / 热词栏。
 *
 *  - ListAppsView.onBindData：隐藏 "VideoList" / "Apps" 类型的推荐组件；
 *  - VerticalHotWordsView.onBindData：隐藏热词容器。
 */
object HomeFeed : BaseHook() {

    override val prefKey: String = Settings.KEY_HOME_FEED

    override val name: String
        get() = "隐藏信息流广告低栏"

    override fun init() {
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.common.view.ListAppsView")
                .methodFinder()
                .filterByName("onBindData")
                .first()
                .hooked {
                    val bean = args.getOrNull(1)
                    if (bean != null) {
                        val componentType = bean.invokeAs<String>("getComponentType")
                        /**
                         * nativeFeaturedHorizontalVideoList
                         * horizontalApps
                         */
                        val blackList = listOf("VideoList", "Apps")
                        if (blackList.any { componentType?.contains(it) == true }) {
                            val view = thisObject as View
                            view.visibility = View.GONE
                            view.layoutParams.height = 0
                            return@hooked null
                        }
                    }
                    return@hooked proceed()
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: ListAppsView 拦截失败", it) }

        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.common.component.hotwords.view.VerticalHotWordsView"
            ).methodFinder()
                .filterByName("onBindData")
                .first()
                .hooked {
                    val view = thisObject as View
                    view.visibility = View.GONE
                    val lp = view.layoutParams
                    if (lp != null) {
                        lp.height = 0
                        view.layoutParams = lp
                    }
                    return@hooked null
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: VerticalHotWordsView 拦截失败", it) }
    }
}
