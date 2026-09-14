package com.owo233.fuckmarketads.hooks.market

import android.util.Log
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 移除搜索相关的软件推荐。
 *
 * 覆盖 README 中：
 *  - 移除搜索建议的软件推荐（NativeSearchSugFragment）
 *  - 移除搜索页面的软件推荐（NativeSearchGuideFragment）
 *  - 移除搜索结果的软件推荐（NativeSearchResultFragment）
 *
 * 注意：搜索结果页的过滤会保留“应用列表”组件、剔除其它组件，这可能使个别应用
 * （如“小米商城”）在搜索中不可见。该行为沿用原实现，若影响使用可在主页关闭本开关。
 */
object SearchAds : BaseHook() {

    override val prefKey: String = Settings.KEY_SEARCH

    override val name: String
        get() = "移除搜索推荐"

    override fun init() {
        // 搜索建议：关闭广告标记
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.search.NativeSearchSugFragment"
            ).methodFinder()
                .filterByName("getRequestParams")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        @Suppress("UNCHECKED_CAST")
                        return@intercept (result as Map<String, Any>).toMutableMap().apply {
                            this["adFlag"] = 0
                        }
                    }
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索建议拦截失败", it) }

        // 搜索页面：仅保留搜索历史组件
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.search.NativeSearchGuideFragment"
            ).apply {
                methodFinder()
                    .filterByName("parseResponseData")
                    .first()
                    .also { method ->
                        HookEnv.base.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            // com.xiaomi.market.common.component.componentbeans.SearchHistoryComponent
                            @Suppress("UNCHECKED_CAST")
                            return@intercept (result as List<Any>).filter { component ->
                                component.javaClass.name.contains("SearchHistoryComponent")
                            }
                        }
                    }

                methodFinder()
                    .filterByName("isLoadMoreEndGone")
                    .first()
                    .also { HookEnv.base.hook(it).intercept { true } }
            }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索页面拦截失败", it) }

        // 搜索结果页面：仅保留应用列表组件
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.search.NativeSearchResultFragment"
            ).methodFinder()
                .filterByName("parseResponseData")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        // com.xiaomi.market.common.component.componentbeans.ListAppComponent
                        @Suppress("UNCHECKED_CAST")
                        return@intercept (result as List<Any>).filter { component ->
                            component.javaClass.name.contains("ListAppComponent")
                        }
                    }
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索结果拦截失败", it) }
    }
}
