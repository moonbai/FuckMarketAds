package com.mars.mimarketpurify.hooks.market

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import com.mars.mimarketpurify.util.invokeAs
import dalvik.system.DexFile
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

    /** 广告角标文字：不依赖 bean 字段的第二条腿，按可见文本判断 */
    private val adLabels = setOf("广告", "推广", "赞助", "热推", "ad", "ads")

    /** 已上报过的未识别形态，避免日志刷屏 */
    private val reported = Collections.synchronizedSet(mutableSetOf<String>())

    /** 文本兜底扫描的限流时间戳：滚动时 onBindData 会频繁触发，不能每次都遍历 */
    @Volatile
    private var lastScanAt = 0L

    override fun init() {
        var bound = 0

        // 光靠猜类名是不够的：实测 10 个候选里只命中 1 个。
        // 这里直接扫 dex，把商店里所有名字带 rank 的类都翻出来一起挂。
        val discovered = discoverRankClasses()
        HookEnv.base.log(
            Log.WARN, TAG, "$name: dex 扫描额外发现 ${discovered.size} 个 rank 类", null
        )

        (candidates + discovered).distinct().forEach { className ->
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
                                        hideLabelledAds(view)
                                    }
                                }
                            }
                            return@hooked proceed()
                        }
                        bound++
                    }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: 候选类不存在，跳过 $className", null)
            }
        }
        // 关键诊断：如果 bound 为 0，说明一个候选类都没命中——榜单根本没走这里的渲染，
        // 这时无论补多少关键字都没用，必须换容器（见 logUnknownShape 的提示）。
        HookEnv.base.log(
            Log.WARN,
            TAG,
            "$name: 已挂载 $bound 个绑定点（候选 ${candidates.size} 个 + 扫描 ${discovered.size} 个）",
            null
        )
    }

    /**
     * 枚举 dex 里所有类名，挑出 `com.xiaomi.market` 下名字含 "rank" 的类。
     *
     * 这是为了不再赌类名：商店每个版本把榜单拆成哪些 Fragment / Adapter 都不一样，
     * 与其猜，不如把 dex 里的类名列一遍。反射 `BaseDexClassLoader.pathList` 属于隐藏 API，
     * 在部分 ROM / 框架上会失败，因此整体包在 [runCatching] 里，失败就退回纯候选类方案。
     */
    private fun discoverRankClasses(): List<String> {
        val found = mutableListOf<String>()
        runCatching {
            val pathList = getClassLoader().getFieldValue("pathList")
            val elements = pathList?.getFieldValue("dexElements") as? Array<*> ?: return found
            elements.forEach { element ->
                val dex = element?.getFieldValue("dexFile") as? DexFile
                    ?: (element?.getFieldValue("path") as? String)
                        ?.let { p -> runCatching { DexFile(p) }.getOrNull() }
                    ?: return@forEach
                val entries = dex.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (name.startsWith("com.xiaomi.market") && name.contains("rank", true)) {
                        found += name
                    }
                }
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "$name: dex 扫描不可用：${it.message}", null)
        }
        return found
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

    /**
     * 文本兜底：向上找到列表容器，遍历可见的 item，凡是带「广告 / 推广 / 赞助」字样的整条隐藏。
     *
     * 这条不依赖 bean 字段，专门用来对付「组件类型里完全没有 ad 关键字」的情况。
     * 限流 1.5s，且只扫直接子项的前几层文本，避免滚动时卡顿。
     */
    private fun hideLabelledAds(view: View) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScanAt < 1500) return
        lastScanAt = now

        runCatching {
            val list = findListContainer(view) ?: return
            val count = list.childCount.coerceAtMost(24)
            for (i in 0 until count) {
                val item = list.getChildAt(i) ?: continue
                if (item.visibility != View.VISIBLE) continue
                if (hasAdLabel(item, 0)) hide(item)
            }
        }
    }

    /** 向上找 RecyclerView（按类名判断，避免为此引入 recyclerview 依赖） */
    private fun findListContainer(view: View): ViewGroup? {
        var p = view.parent
        while (p is View) {
            if (p is ViewGroup && p::class.java.name.contains("RecyclerView")) return p
            p = p.parent
        }
        return null
    }

    /** 递归查找广告角标文本，最多 5 层 */
    private fun hasAdLabel(view: View, depth: Int): Boolean {
        if (depth > 5) return false
        if (view is TextView) {
            val t = view.text?.toString()?.trim()?.lowercase()
            if (t != null && t in adLabels) return true
        }
        if (view !is ViewGroup) return false
        val count = view.childCount.coerceAtMost(16)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            if (hasAdLabel(child, depth + 1)) return true
        }
        return false
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
        if (reported.add(shape)) {
            // 用 WARN 而不是 DEBUG：多数框架 / 日志 App 会过滤掉 DEBUG，等于白打
            HookEnv.base.log(Log.WARN, TAG, shape, null)
            if (Settings.isEnabled(Settings.KEY_RANK_DEBUG, false)) {
                // 手机上直接可见：不需要电脑抓 logcat
                view.post {
                    runCatching { Toast.makeText(view.context, shape, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}
