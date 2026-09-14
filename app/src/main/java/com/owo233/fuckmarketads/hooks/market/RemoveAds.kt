package com.owo233.fuckmarketads.hooks.market

import android.view.View
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.HookSettings
import com.owo233.fuckmarketads.init.BaseHook
import com.owo233.fuckmarketads.util.getFieldValue
import com.owo233.fuckmarketads.util.invokeAs
import com.owo233.fuckmarketads.util.setFieldValue
import io.github.kyuubiran.ezxhelper.core.finder.ConstructorFinder.`-Static`.constructorFinder
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object RemoveAds : BaseHook() {

    private val pageCollapseStateExpand by lazy {
        ClassUtil.loadClass($$"com.xiaomi.market.ui.UpdateListRvAdapter$PageCollapseState")
            .getFieldValue("Expand")
    }

    private val detailTypeV4 by lazy {
        ClassUtil.loadClass("com.xiaomi.market.business_ui.detail.DetailType")
            .getFieldValue("V4")
    }

    override val name: String
        get() = "移除一系列广告"

    override fun init() {
        val ctx = HookEnv.base

        // ===== 开屏广告 =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_SPLASH)) {
            ClassUtil.loadClass("com.xiaomi.market.ui.splash.DetailSplashAdManager").apply {
                methodFinder()
                    .filter {
                        name in setOf(
                            "canRequestSplashAd",
                            "isRequestApprovedByServer",
                            "isOpenFromMsa",
                            "isRequesting"
                        )
                    }.forEach { HookEnv.base.hook(it).intercept { false } }

                methodFinder()
                    .filterByName("tryToRequestSplashAd")
                    .first()
                    .also { HookEnv.base.hook(it).intercept { null } }
            }

            ClassUtil.loadClass("com.xiaomi.market.ui.splash.SplashManager").apply {
                methodFinder()
                    .filter {
                        name in setOf(
                            "canShowSplash",
                            "needShowSplash",
                            "needRequestFocusVideo",
                            "isPassiveSplashAd"
                        )
                    }.forEach { HookEnv.base.hook(it).intercept { false } }

                methodFinder()
                    .filter {
                        name in setOf(
                            "tryAdSplash",
                            "trySplashWhenApplicationForeground",
                            "preLoadSplashCover",
                            "shownSplashCoverIfNeed"
                        )
                    }.forEach { HookEnv.base.hook(it).intercept { null } }
            }
        }

        // ===== 搜索页推荐广告 + 搜索建议 =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_SEARCH)) {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.search.NativeSearchSugFragment")
                .methodFinder()
                .filterByName("getRequestParams")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        @Suppress("UNCHECKED_CAST")
                        return@intercept (result as Map).toMutableMap().apply {
                            this["adFlag"] = 0
                        }
                    }
                }

            ClassUtil.loadClass("com.xiaomi.market.business_ui.search.NativeSearchGuideFragment").apply {
                methodFinder()
                    .filterByName("parseResponseData")
                    .first()
                    .also { method ->
                        HookEnv.base.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            @Suppress("UNCHECKED_CAST")
                            return@intercept (result as List).filter { component ->
                                component.javaClass.name.contains("SearchHistoryComponent")
                            }
                        }
                    }

                methodFinder()
                    .filterByName("isLoadMoreEndGone")
                    .first()
                    .also { HookEnv.base.hook(it).intercept { true } }
            }
        }

        // ===== 搜索结果广告 =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_SEARCH_RESULT)) {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.search.NativeSearchResultFragment")
                .methodFinder()
                .filterByName("parseResponseData")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        @Suppress("UNCHECKED_CAST")
                        return@intercept (result as List).filter { component ->
                            component.javaClass.name.contains("ListAppComponent")
                        }
                    }
                }
        }

        // ===== 更新页推荐广告 =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_UPDATE)) {
            ClassUtil.loadClass("com.xiaomi.market.ui.UpdateListRvAdapter").apply {
                methodFinder()
                    .filterByName("generateRecommendGroupItems")
                    .first()
                    .also { HookEnv.base.hook(it).intercept { null } }

                constructorFinder().forEach { ctor ->
                    HookEnv.base.hook(ctor).intercept { chain ->
                        val result = chain.proceed()

                        fieldFinder()
                            .filterByName("forceExpanded")
                            .first()
                            .set(chain.thisObject, true)

                        fieldFinder()
                            .filterByName("foldButtonVisible")
                            .first()
                            .set(chain.thisObject, false)

                        fieldFinder()
                            .filterByName("pageCollapseState")
                            .first()
                            .set(chain.thisObject, pageCollapseStateExpand)

                        return@intercept result
                    }
                }
            }
        }

        // ===== 详情页广告 =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_DETAIL)) {
            ClassUtil.loadClass("com.xiaomi.market.common.network.retrofit.response.bean.AppDetailV3").apply {
                methodFinder()
                    .filter {
                        name in setOf(
                            "isBrowserMarketAdOff",
                            "isBrowserSourceFileAdOff"
                        )
                    }.forEach { HookEnv.base.hook(it).intercept { true } }

                methodFinder()
                    .filter {
                        name in setOf(
                            "needShowAds",
                            "needShowAdsWithSourceFile",
                            "isInternalAd",
                            "showRecommend",
                            "showTopBanner",
                            "showTopVideo",
                            "isSourceFileShowAdStyle",
                            "getShowOpenScreenAd"
                        )
                    }.forEach { HookEnv.base.hook(it).intercept { false } }
            }

            ClassUtil.loadClass("com.xiaomi.market.ui.detail.BaseDetailActivity")
                .methodFinder()
                .filterByName("initParams")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        chain.thisObject?.setFieldValue("detailType", detailTypeV4)
                        return@intercept chain.proceed()
                    }
                }
        }

        // ===== 主界面推荐拦截（开屏以外的通用推荐） =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_SPLASH)) {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.main.MarketTabActivity")
                .methodFinder()
                .filter {
                    name in setOf(
                        "tryShowRecallReCommend",
                        "tryShowRecommend",
                        "trySplash",
                        "fetchSearchHotList"
                    )
                }.forEach { HookEnv.base.hook(it).intercept { null } }
        }

        // ===== 视频列表 =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_VIDEO)) {
            ClassUtil.loadClass("com.xiaomi.market.common.view.ListAppsView")
                .methodFinder()
                .filterByName("onBindData")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        val bean = chain.args.getOrNull(1)
                        if (bean != null) {
                            val componentType = bean.invokeAs("getComponentType")
                            val blackList = listOf("VideoList", "Apps")
                            if (blackList.any { componentType?.contains(it) == true }) {
                                val view = chain.thisObject as View
                                view.visibility = View.GONE
                                view.layoutParams.height = 0
                                return@intercept null
                            }
                        }
                        return@intercept chain.proceed()
                    }
                }
        }

        // ===== 热词 =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_HOTWORD)) {
            ClassUtil.loadClass("com.xiaomi.market.common.component.hotwords.view.VerticalHotWordsView")
                .methodFinder()
                .filterByName("onBindData")
                .first()
                .also { method ->
                    HookEnv.base.hook(method).intercept { chain ->
                        val view = chain.thisObject as View
                        view.visibility = View.GONE
                        val lp = view.layoutParams
                        if (lp != null) {
                            lp.height = 0
                            view.layoutParams = lp
                        }
                        return@intercept null
                    }
                }
        }

        // ===== 下载队列 / 为你优先（兜底） =====
        if (HookSettings.isEnabled(ctx, HookSettings.KEY_ADS_SPLASH)) {
            ClassUtil.loadClass("com.xiaomi.market.ui.DownloadListFragment")
                .methodFinder()
                .filterByName("parseRecommendGroupResult")
                .first()
                .also { HookEnv.base.hook(it).intercept { null } }
        }
    }
}