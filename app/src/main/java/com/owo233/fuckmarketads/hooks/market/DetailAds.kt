package com.owo233.fuckmarketads.hooks.market

import android.util.Log
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.BaseHook
import com.owo233.fuckmarketads.util.getFieldValue
import com.owo233.fuckmarketads.util.setFieldValue
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 移除应用详情页的广告、评论与推荐。
 *
 *  - AppDetailV3：拦截各类“是否展示广告/推荐”的判定方法；
 *  - BaseDetailActivity.initParams：将详情类型固定为 V4（comments? 推荐位相关）。
 */
object DetailAds : BaseHook() {

    override val prefKey: String = Settings.KEY_DETAIL

    override val name: String
        get() = "移除详情页广告"

    private val detailTypeV4 by lazy {
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.detail.DetailType")
                .getFieldValue("V4")
        }.getOrNull()
    }

    override fun init() {
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.common.network.retrofit.response.bean.AppDetailV3"
            ).apply {
                methodFinder()
                    .filter {
                        name in setOf(
                            "isBrowserMarketAdOff",
                            "isBrowserSourceFileAdOff"
                        )
                    }.forEach { it.hooked { true } }

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
                    }.forEach { it.hooked { false } }
            }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: AppDetailV3 拦截失败", it) }

        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.detail.BaseDetailActivity")
                .methodFinder()
                .filterByName("initParams")
                .first()
                .hooked {
                    detailTypeV4?.let { v4 ->
                        thisObject?.setFieldValue("detailType", v4)
                    }
                    return@hooked proceed()
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: BaseDetailActivity 拦截失败", it) }
    }
}
