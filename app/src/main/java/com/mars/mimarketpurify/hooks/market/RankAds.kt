package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.Collections

/**
 * 移除「榜单」界面（tab = native_market_rank，含应用榜 / 游戏榜等子页）的广告与推广卡片。
 *
 * 小米应用商店的榜单列表项是组件化渲染的：每个 item 由一个 bean 描述，组件类型通常通过
 * `getComponentType` 之类的 getter 暴露。难点在于**不同商店版本、不同榜单子页的字段名与容器类
 * 都不一样**，只认一种写法必然漏。因此这里采用四层递进判定：
 *
 *  1. **容器类**：覆盖榜单各子页（含游戏榜）的候选类，`onBindData` 的所有重载都挂上；
 *  2. **组件类型**：逐个尝试常见的类型 getter，按「词」匹配广告关键字
 *     （用分词而不是简单 contains，避免 `load` / `head` 这类词被误判成含 "ad"）；
 *  3. **布尔广告标记**：`isAd` / `isPromote` / `isSponsored` 等，存在且返回 true 即视为广告；
 *  4. **视图兜底**：隐藏 id 命名明显为广告位（ad_* / *_ad / banner / promote）的子视图。
 *
 * 仍然漏网的榜单项会把自己的「容器类 + bean 类 + 组件类型」以 DEBUG 打到 logcat
 * （tag 为 [TAG]，前缀 `[rank]`），据此即可把关键字精确补进去。
 */
object RankAds : BaseHook() {

    override val prefKey: String = Settings.KEY_RANK

    override val name: String
        get() = "移除榜单广告"

    /** 命中其一即视为广告 / 推广（按词匹配） */
    private val adTokens = setOf(
        "ad", "ads", "advert", "advertise", "advertisement", "advertorial",
        "banner", "promo", "promote", "promotion", "sponsor", "sponsored",
        "recommend", "recommendation", "splash"
    )

    /** 榜单 item 可能的组件类型 getter：不同商店版本命名不一，逐个尝试 */
    private val typeGetters = listOf(
        "getComponentType", "getItemComponentType", "getItemType", "getType",
        "getViewType", "getItemViewType", "getTemplateType", "getCardType",
        "getBizType", "getStyleType", "getStyle", "getComponentName"
    )

    /** 布尔型的“是不是广告”标记 */
    private val adFlags = listOf(
        "isAd", "getIsAd", "isAdvertise", "isAdvertisement", "isAdItem",
        "isPromote", "isPromotion", "isPromotionItem", "isSponsor", "isSponsored",
        "isRecommendAd", "isRankAd", "isMarketAd"
    )

    /**
     * 可能的榜单 / 列表容器实现（多候选，命中其一即可）。
     * 榜单主页与各分类子页（精品 / 游戏 / 新锐…）在不同版本拆成不同的 Fragment，
     * 这里尽量列全；不存在的类名会被 [runCatching] 吞掉。
     */
    private val candidates = listOf(
        "com.xiaomi.market.business_ui.rank.RankFragment",
        "com.xiaomi.market.business_ui.rank.RankActivity",
        "com.xiaomi.market.business_ui.rank.RankListFragment",
        "com.xiaomi.market.business_ui.rank.RankListAdapter",
        "com.xiaomi.market.business_ui.rank.RankSubFragment",
        "com.xiaomi.market.business_ui.rank.RankChildFragment",
        "com.xiaomi.market.business_ui.rank.RankTabFragment",
        "com.xiaomi.market.business_ui.rank.GameRankFragment",
        "com.xiaomi.market.business_ui.rank.adapter.RankListAdapter",
        "com.xiaomi.market.common.view.ListAppsView"
    )

    /** 已上报过的未识别形态，避免日志刷屏 */
    private val reported = Collections.synchronizedSet(mutableSetOf<String>())

    override fun init() {
        candidates.forEach { className ->
            runCatching {
                ClassUtil.loadClass(className)
                    .methodFinder()
                    .filterByName("onBindData")
                    // 所有重载都挂：不同版本的参数顺序 / 个数不一样，只取第一个容易漏
                    .forEach { m ->
                        m.hooked {
                            // 不把 args 显式声明成 Array / List：libxposed 的 Chain#getArgs
                            // 在两侧都用 firstOrNull 即可，避免类型写死
                            val view = thisObject as? View
                            if (view != null) {
                                val bean = args.firstOrNull { isCandidateBean(it) }
                                when {
                                    bean != null && isAdBean(bean) -> {
                                        hide(view)
                                        return@hooked null
                                    }
                                    else -> {
                                        logUnknownShape(view, bean)
                                        hideAdSubViews(view)
                                    }
                                }
                            }
                            return@hooked proceed()
                        }
                    }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: 候选类不存在，跳过 $className", null)
            }
        }
    }

    /** 从参数里挑出疑似 bean 的对象：排除 View 与基础类型 */
    private fun isCandidateBean(arg: Any?): Boolean {
        return arg != null &&
            arg !is View &&
            arg !is Number &&
            arg !is Boolean &&
            arg !is CharSequence
    }

    private fun isAdBean(bean: Any): Boolean {
        // ① 组件类型
        typeGetters.forEach { getter ->
            val text = readType(bean, getter) ?: return@forEach
            if (tokens(text).any { it in adTokens }) return true
        }
        // ② 布尔广告标记
        adFlags.forEach { flag ->
            if (runCatching { bean.invokeAs<Boolean?>(flag) }.getOrNull() == true) return true
        }
        // ③ bean 自身的类名（如 XXXAdBean）
        if (tokens(bean::class.java.simpleName).any { it in adTokens }) return true
        return false
    }

    private fun readType(bean: Any, getter: String): String? =
        runCatching { bean.invokeAs<Any?>(getter)?.toString() }.getOrNull()

    /**
     * 分词：把 `native_rank_ad` / `nativeRankAd` 拆成 [native, rank, ad]，
     * 这样才不会把 `load` 这类词因为包含 "ad" 就误判成广告。
     */
    private fun tokens(raw: String): List<String> =
        raw.replace(Regex("[^a-zA-Z0-9]+"), " ")
            .replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
            .lowercase()
            .split(' ')
            .filter { it.isNotEmpty() }

    /** 视图兜底：隐藏 id 命名明显是广告位的直接子视图（仅一层，避免误伤正文） */
    private fun hideAdSubViews(view: View) {
        if (view !is ViewGroup) return
        runCatching {
            val count = view.childCount.coerceAtMost(16)
            for (i in 0 until count) {
                val child = view.getChildAt(i) ?: continue
                if (isAdResourceName(child)) hide(child)
            }
        }
    }

    private fun isAdResourceName(v: View): Boolean {
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false
        val n = runCatching { v.resources.getResourceEntryName(id) }
            .getOrNull()?.lowercase() ?: return false
        return n == "ad" ||
            n.startsWith("ad_") ||
            n.endsWith("_ad") ||
            n.contains("banner") ||
            n.contains("advert") ||
            n.contains("promot")
    }

    private fun hide(view: View) {
        runCatching {
            view.visibility = View.GONE
            view.layoutParams?.let { lp ->
                lp.height = 0
                view.layoutParams = lp
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 隐藏失败 ${it.message}", null)
        }
    }

    /** 上报尚未识别的榜单项形态，便于精确补充关键字 */
    private fun logUnknownShape(view: View, bean: Any?) {
        val type = if (bean == null) null else {
            typeGetters.firstNotNullOfOrNull { getter -> readType(bean, getter) }
        }
        val shape = buildString {
            append("[rank] ")
            append(view::class.java.simpleName)
            append(" <- ")
            append(bean?.javaClass?.simpleName ?: "<no bean>")
            append(" type=")
            append(type ?: "<none>")
        }
        if (reported.add(shape)) HookEnv.base.log(Log.DEBUG, TAG, shape, null)
    }
}
