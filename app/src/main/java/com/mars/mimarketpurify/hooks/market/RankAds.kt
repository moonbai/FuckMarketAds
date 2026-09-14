package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 移除「榜单」界面（tab = native_market_rank）的广告 / 推广卡片。
 *
 * 小米应用商店的榜单列表项同样是组件化渲染，广告 / 推广项会带有一个组件类型字段
 * （常见命名 getComponentType / getType / getItemType），命中 [adKeywords] 即判定为广告并隐藏其容器。
 *
 * 因无法在编译期确定各 ROM / 商店版本榜单的具体实现类名，这里采用“多候选 + 优雅降级”：
 * 依次尝试若干可能的榜单类（含列表通用容器 [ListAppsView]），命中其一即用其一；
 * 若某类名在当前版本不存在，[ClassUtil.loadClass] 抛异常被 [runCatching] 吞掉，不影响其余功能。
 *
 * 若仍发现榜单广告未被拦截，请用 Layout Inspector / 反编译确认广告项所属的类与方法名、
 * 或组件类型字符串后反馈，我再精确收敛拦截条件。
 */
object RankAds : BaseHook() {

    override val prefKey: String = Settings.KEY_RANK

    override val name: String
        get() = "移除榜单广告"

    /** 组件类型命中即视为广告 / 推广 */
    private val adKeywords = listOf("ad", "ads", "banner", "recommend", "promo", "splash")

    /** 可能的榜单实现类（多候选，命中其一即可） */
    private val candidates = listOf(
        "com.xiaomi.market.business_ui.rank.RankFragment",
        "com.xiaomi.market.business_ui.rank.RankActivity",
        "com.xiaomi.market.business_ui.rank.RankListFragment",
        "com.xiaomi.market.business_ui.rank.RankListAdapter",
        "com.xiaomi.market.common.view.ListAppsView"
    )

    override fun init() {
        candidates.forEach { className ->
            runCatching {
                ClassUtil.loadClass(className)
                    .methodFinder()
                    .filterByName("onBindData")
                    .firstOrNull()
                    ?.hooked {
                        if (tryHideAd(thisObject, args.getOrNull(1))) return@hooked null
                        return@hooked proceed()
                    }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: 候选类不存在，跳过 $className", null)
            }
        }
    }

    /** 命中广告则返回 true 并隐藏容器；否则返回 false */
    private fun tryHideAd(viewObj: Any?, bean: Any?): Boolean {
        if (bean == null) return false
        val type = bean.invokeAs<String?>("getComponentType")
            ?: bean.invokeAs<String?>("getType")
            ?: bean.invokeAs<String?>("getItemType")
            ?: return false
        if (!adKeywords.any { type.contains(it, ignoreCase = true) }) return false
        val v = viewObj as? View ?: return false
        v.visibility = View.GONE
        v.layoutParams?.let { lp ->
            lp.height = 0
            v.layoutParams = lp
        }
        return true
    }
}
