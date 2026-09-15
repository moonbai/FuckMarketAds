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
 * 都不一样**，只认一种写法必然漏。因此这里采用五层递进判定：
 *
 *  1. **容器类**：覆盖榜单各子页（含游戏榜）的候选类 + dex 扫描出来的所有 rank 类，
 *     `onBindData` 的所有重载都挂上；
 *  2. **组件类型**：逐个尝试常见的类型 getter，按「词」匹配广告关键字
 *     （用分词而不是简单 contains，避免 `load` / `head` 这类词被误判成含 "ad"）；
 *  3. **布尔广告标记**：`isAd` / `isPromote` / `isSponsored` 等，存在且返回 true 即视为广告；
 *  4. **视图兜底**：隐藏 id 命名明显为广告位（ad_* / *_ad / banner / promote）的子视图，
 *     以及带「广告 / 推广 / 赞助」字样的整条 item；
 *  5. **排名徽章宽度**：bean 被混淆成 `mi` 这类单字母类名时，前三层的 getter 全部失效
 *     （日志表现为 `type=<none>`）。此时改看 `iv_app_ranking` 的实测宽度——
 *     广告项复用这个 id 画的是一个约 5dp 的小图标，远窄于真正的名次数字。
 *
 * 仍然漏网的榜单项会把自己的「容器类 + bean 类 + 组件类型」以 WARN 打到 logcat
 * （tag 为 [TAG]，前缀 `[rank]`）；开启「榜单调试提示」后还会直接 Toast 出来，
 * 并附上实测的徽章宽度样本，用于校准第 5 层的阈值。
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

    /**
     * 数据层拦截用的方法名候选。
     * 取名自组件化列表的常见解析入口（参考实现的搜索页用的是 `parseResponseData`）。
     */
    private val parseMethods = listOf(
        "parseResponseData", "parseResponse", "parseData", "parseResult",
        "handleResponseData", "buildComponents", "getComponents"
    )

    /**
     * 数据层拦截的宿主类候选。
     * 只在榜单相关类上挂，避免在别的页面误删组件。
     */
    private val dataOwnerClasses = listOf(
        "com.xiaomi.market.business_ui.rank.RankFragment",
        "com.xiaomi.market.business_ui.rank.RankListFragment",
        "com.xiaomi.market.business_ui.rank.RankSubFragment",
        "com.xiaomi.market.business_ui.rank.RankChildFragment",
        "com.xiaomi.market.business_ui.rank.RankTabFragment"
    )

    /** 中文广告角标：按「包含」匹配，实际文案常带后缀（如「广告 · 下载」） */
    private val cnLabels = listOf("广告", "推广", "赞助", "热推")
    /** 英文广告角标：必须按**词**匹配，否则 Adobe 会因为含 "ad" 被整条误杀 */
    private val enLabels = setOf("ad", "ads", "sponsor", "sponsored", "promoted")

    /**
     * 名次图标的资源名。实测（1080 宽、density 2.8125）正常项这里是 **14x39**——
     * 所以「宽度 14px 就是广告」这个假设是**反的**：14px 恰恰是正常名次图标的尺寸。
     * 用它做绝对阈值会把所有正常项都判成广告，只能靠安全阀兜住，等于没用。
     * 现在它只用于**相对比较**（明显窄于同列其他项才算可疑）。
     */
    private const val RANK_BADGE = "iv_app_ranking"

    /**
     * 名次**数字**的资源名，实测正常项为 29x60。
     * 真正的榜单一律有名次，广告项没有——所以“拿不到可见的名次数字”才是可靠判据。
     */
    private const val RANK_NUMBER = "tv_app_ranking"

    /**
     * 已知的正常榜单组件类型（归一化后比较：小写、去掉下划线）。
     * 命中即视为正常项，不再打 `[rank]` 诊断日志——否则满屏都是正常项，
     * 真正可疑的那几行反而被淹没了。
     */
    private val knownNormalTypes = setOf("nativeranklistapps", "ranklistapps")

    /** 徽章宽度低于同列中位数这个比例时视为可疑（绝对阈值在这台设备上不可用） */
    private const val BADGE_RATIO = 0.5f

    /** 已上报过的未识别形态，避免日志刷屏 */
    private val reported = Collections.synchronizedSet(mutableSetOf<String>())

    /** 资源名 -> id 的缓存：`getIdentifier` 走资产表查询，别每次绑定都查一遍 */
    private val resolvedIds = Collections.synchronizedMap(mutableMapOf<String, Int>())

    /** 文本兜底扫描的限流时间戳：滚动时 onBindData 会频繁触发，不能每次都遍历 */
    @Volatile
    private var lastScanAt = 0L

    /** 宽度扫描的限流时间戳（同上，但节奏可以稍快一点） */
    @Volatile
    private var lastWidthScanAt = 0L

    /** 调试 Toast 的限流时间戳 */
    @Volatile
    private var lastToastAt = 0L

    override fun init() {
        // 第一优先：从**数据层**剔除广告。
        // 视图层那套（宽度 / 文案 / 类名）都是在跟已渲染出来的东西较劲，
        // 而广告本来就是服务端下发的一个组件对象——在解析结果里直接把它拿掉，
        // 后面就没有任何"怎么把这条藏干净"的问题了。
        hookDataLayer()

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
                                        // 榜单头部（RankHeaderView）也要抓：广告常常挂在 header 里，
                                        // 那里既没有排名徽章也没有 bean 类型，前几条判据全都落空
                                        hideAdViews(view)
                                        hideHeaderBanner(view)
                                        hideLabelledAds(view)
                                        hideByBadgeWidth(view)
                                        view.post { runCatching { dumpTree(view) } }
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
     * 数据层拦截：hook 榜单的数据解析方法，从返回的组件列表里把广告剔除。
     *
     * 思路来自 XiaomiHelper 对搜索页的处理——它不碰任何一个 View，
     * 而是 hook `NativeSearchResultFragment.parseResponseData`，
     * 对解析出的组件列表做 `retainAll { 白名单组件 }`。
     * 广告在数据层面就没有了，视图层自然干净，也不会出现"藏了但留个空位"。
     *
     * 这里的做法是**反向排除**而不是白名单保留：榜单的组件类型太多
     * （应用项、头部、分类、加载更多……），白名单很容易把正常内容也误杀；
     * 只剔除明确命中广告特征的组件，风险小得多。
     *
     * 方法名与组件类名都做多候选，命中不上就静默跳过，退回视图层判据。
     */
    private fun hookDataLayer() {
        parseMethods.forEach { method ->
            dataOwnerClasses.forEach { owner ->
                runCatching {
                    ClassUtil.loadClass(owner)
                        .methodFinder()
                        .filterByName(method)
                        .forEach { m ->
                            // hooked 的 block 返回值即方法返回值（见 BaseHook），
                            // 这里没有 YukiHookAPI 的 result(...) 可调用，直接返回过滤后的列表
                            m.hooked {
                                val original = proceed()
                                dropAdComponents(original) ?: original
                            }
                        }
                }
            }
        }
    }

    /**
     * 从解析结果里剔除广告组件。
     * 返回 null 表示"没动过"，调用方应原样返回，避免把非列表结果搞坏。
     */
    private fun dropAdComponents(result: Any?): Any? {
        val list = when (result) {
            is List<*> -> result
            // 有些版本把组件列表包在 Pair / data 对象里，这里只处理 List 这一种最常见形态
            else -> return null
        }
        if (list.isEmpty()) return null
        val kept = list.filterNot { isAdComponent(it) }
        if (kept.size == list.size) return null
        HookEnv.base.log(
            Log.WARN, TAG, "$name: 数据层剔除 ${list.size - kept.size}/${list.size} 个广告组件", null
        )
        return kept
    }

    /** 组件对象是否广告：类名 / 字符串字段命中广告特征 */
    private fun isAdComponent(component: Any?): Boolean {
        val c = component ?: return false
        if (tokens(c.javaClass.simpleName).any { it in adTokens }) return true
        // 组件对象里的组件类型字段（组件化渲染里通常叫 type / componentType）
        typeGetters.forEach { getter ->
            val text = readType(c, getter) ?: return@forEach
            if (tokens(text).any { it in adTokens }) return true
        }
        return false
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

    /**
     * 从参数里挑出疑似 bean 的对象：排除 View、基础类型，以及**宿主 Fragment / Activity**。
     *
     * 最后这条是实测出来的坑：`onBindData` 常常把所在 Fragment 一起传进来，
     * 于是 `firstOrNull` 挑中的是 Fragment（日志里表现为 `<- RankTabFragment`），
     * 后续十几个类型 getter 全打在 Fragment 上，自然永远返回 null。
     * 把 Fragment / Activity / Context 排除掉，才可能拿到真正的 bean。
     */
    private fun isCandidateBean(arg: Any?): Boolean {
        if (arg == null) return false
        if (arg is View || arg is Number || arg is Boolean || arg is CharSequence) return false
        val n = arg.javaClass.name
        return !n.contains("Fragment") &&
            !n.contains("Activity") &&
            !n.contains("Context")
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

    /** 类型归一化：小写并去掉下划线，`native_rank_list_apps` 与 `nativeRankListApps` 视为同一个 */
    private fun normalizeType(raw: String): String = raw.lowercase().replace("_", "")

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

    /**
     * 视图兜底：递归隐藏「类名或资源 id 命中广告关键字」的子视图。
     *
     * 只扫一层是不够的——广告位通常套在两三层容器里。这里递归 5 层，
     * 每层最多看 24 个子节点；类名判据同样走 [tokens] 分词，
     * 所以 `HeaderAdapterView` 不会被当成含 "ad" 误杀。
     */
    private fun hideAdViews(view: View, depth: Int = 0) {
        if (depth > 5) return
        if (depth > 0 && isAdView(view)) {
            hide(view)
            return
        }
        if (view !is ViewGroup) return
        val count = view.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            hideAdViews(child, depth + 1)
        }
    }

    private fun isAdView(v: View): Boolean =
        isAdResourceName(v) || tokens(v::class.java.simpleName).any { it in adTokens }

    /**
     * 头部 banner：榜单的 `RankHeaderView` 里常塞一条接近满屏宽、宽高比很大的横幅广告。
     * 这类视图既没有广告字样的 id，也不在列表项里，只能按形状认——
     * 宽度 ≥ 屏宽 70%、高度 ≥ 56dp、且宽高比 ≥ 3:1。
     *
     * 只对名字含 "header" 的容器生效，避免把列表里正常的大图卡片一起干掉。
     */
    private fun hideHeaderBanner(view: View) {
        if (!view::class.java.simpleName.contains("header", true)) return
        val dm = view.resources.displayMetrics
        val minW = (dm.widthPixels * 0.7f).toInt()
        val minH = (56 * dm.density).toInt()
        runCatching { scanBanners(view, 0, minW, minH) }
    }

    private fun scanBanners(view: View, depth: Int, minW: Int, minH: Int) {
        if (depth > 6) return
        val w = if (view.width > 0) view.width else view.measuredWidth
        val h = if (view.height > 0) view.height else view.measuredHeight
        if (depth > 0 && w >= minW && h >= minH && h * 3 <= w) {
            hide(view)
            return
        }
        if (view !is ViewGroup) return
        val count = view.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            scanBanners(child, depth + 1, minW, minH)
        }
    }

    /**
     * 调试用：把整个视图树（类名 # 资源名 + 实测尺寸）打到 logcat，前缀 `[rank-tree]`。
     *
     * 前面几层判据都依赖「猜对关键字」，而商店一改版就可能全猜错。
     * 与其继续猜，不如把树打出来——看一眼就知道该按哪个 id / 哪个尺寸下手。
     */
    private fun dumpTree(view: View) {
        if (!Settings.isEnabled(Settings.KEY_RANK_DEBUG, false)) return
        // 去重键要带上「有没有名次数字」这一位：按类名去重的话，
        // 同类型的第一个 item 会把后面所有 item（包括广告项）全挡掉，
        // 结果永远只能看到第一棵树，等于白 dump。
        val hasRank = findByIdName(view, RANK_NUMBER)
            ?.let { it.visibility == View.VISIBLE && (it.width > 0 || it.measuredWidth > 0) }
            ?: false
        if (!reported.add("tree:" + view::class.java.simpleName + ":" + hasRank)) return
        logTree(view, 0)
    }

    private fun logTree(view: View, depth: Int) {
        if (depth > 6) return
        val w = if (view.width > 0) view.width else view.measuredWidth
        val h = if (view.height > 0) view.height else view.measuredHeight
        val id = nameOf(view)
        HookEnv.base.log(
            Log.WARN,
            TAG,
            "[rank-tree] ${"· ".repeat(depth)}${view::class.java.simpleName}" +
                (if (id != null) "#$id" else "") + " ${w}x$h",
            null
        )
        if (view !is ViewGroup) return
        val count = view.childCount.coerceAtMost(20)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            logTree(child, depth + 1)
        }
    }

    private fun nameOf(v: View): String? {
        if (v.id == View.NO_ID || v.id <= 0) return null
        return runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()
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

    /**
     * 宽度判据：靠 [RANK_BADGE] 的实际宽度把广告项挑出来。
     *
     * 之所以需要这条：新版商店把榜单 bean 混淆成了 `mi` 这种单字母类名，
     * `getComponentType` 之类的 getter 全部取不到值（日志里表现为 `type=<none>`），
     * 于是基于 bean 关键字的一整套判定直接失效。但**视图层没有混淆**——
     * 广告项仍然复用 `iv_app_ranking` 画一个比名次数字窄得多的图标。
     *
     * 两点实现细节：
     *  1. `onBindData` 阶段 View 往往还没走完 layout，`width` 读出来是 0，
     *     所以整个测量要 `post` 到下一帧再做；
     *  2. 加了安全阀：如果一轮里几乎所有项都命中，那多半是阈值定错了，
     *     宁可放过也不把整个榜单清空。
     */
    private fun hideByBadgeWidth(view: View) {
        val now = SystemClock.uptimeMillis()
        if (now - lastWidthScanAt < 1000) return
        lastWidthScanAt = now

        // 找不到 RecyclerView 时退到直接父容器：绑定点是 item view，它的 parent 就是列表
        val list = findListContainer(view) ?: (view.parent as? ViewGroup) ?: return
        list.post { runCatching { scanBadgeWidths(list) } }
    }

    private fun scanBadgeWidths(list: ViewGroup) {
        val count = list.childCount.coerceAtMost(32)
        val normal = ArrayList<Pair<View, Int>>()   // 有名次数字的正常项 -> 名次图标宽度
        val suspects = ArrayList<View>()            // 拿不到可见名次数字的可疑项
        for (i in 0 until count) {
            val item = list.getChildAt(i) ?: continue
            if (item.visibility != View.VISIBLE) continue
            // 主判据：名次数字是否真实可见。广告项不参与排名，自然没有名次。
            // 注意不能只判断“存在”——未绑定数据的 View 可能尺寸还是 0
            val number = findByIdName(item, RANK_NUMBER)
            val numbered = number != null &&
                number.visibility == View.VISIBLE &&
                (number.width > 0 || number.measuredWidth > 0)
            if (!numbered) {
                suspects += item
                continue
            }
            val badge = findByIdName(item, RANK_BADGE)
            val w = if (badge == null) 0
            else if (badge.width > 0) badge.width else badge.measuredWidth
            if (w > 0) normal += item to w
        }

        val total = normal.size + suspects.size
        val widths = if (normal.isEmpty()) {
            "<未测到>"
        } else {
            normal.take(12).joinToString(",") { it.second.toString() }
        }
        val shape = "[rank] 名次宽度 $widths px / 无名次 ${suspects.size} 项 / 共 $total 项"
        reportWidths(list, shape, 0)

        // 安全阀：可疑项超过 1/4 就不动手。榜单前三名常用另一种大卡片布局
        // （同样没有 tv_app_ranking），样本少时它们占比会很高，宁可放过。
        if (total < 4 || suspects.isEmpty() || suspects.size * 4 > total) return

        suspects.forEach { hide(it) }
        HookEnv.base.log(
            Log.WARN,
            TAG,
            "$name: 无可见名次，隐藏 ${suspects.size} 条（$shape）",
            null
        )

        // 相对宽度：正常项的名次图标尺寸一致（实测 14x39），
        // 明显窄于中位数的才算可疑。绝对阈值在这台设备上是错的，只能用相对值。
        if (normal.size >= 4) {
            val mid = normal.map { it.second }.sorted()[normal.size / 2]
            val threshold = (mid * BADGE_RATIO).toInt().coerceAtLeast(1)
            val narrow = normal.filter { it.second <= threshold }
            if (narrow.isNotEmpty() && narrow.size * 4 <= normal.size) {
                narrow.forEach { hide(it.first) }
                HookEnv.base.log(
                    Log.WARN, TAG, "$name: 名次图标偏窄，隐藏 ${narrow.size} 条（中位数 $mid px）", null
                )
            }
        }
    }

    /** 宽度样本只报一次，避免滚动时刷屏；开了调试开关额外弹 Toast（且 3s 内只弹一次） */
    private fun reportWidths(list: View, shape: String, hits: Int) {
        if (!reported.add(shape)) return
        HookEnv.base.log(Log.WARN, TAG, shape, null)
        if (!Settings.isEnabled(Settings.KEY_RANK_DEBUG, false)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastToastAt < 3000) return
        lastToastAt = now
        list.post {
            runCatching {
                val tip = if (hits > 0) "$shape / 已隐藏 $hits 条" else shape
                Toast.makeText(list.context, tip, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 按资源名在子树里找 View；id 解析不到（版本不含该资源）时静默返回 null */
    private fun findByIdName(view: View, name: String, depth: Int = 0): View? {
        val id = resolveId(view, name)
        if (id == 0) return null
        if (view.id == id) return view
        if (depth >= 6 || view !is ViewGroup) return null
        val count = view.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            findByIdName(child, name, depth + 1)?.let { return it }
        }
        return null
    }

    private fun resolveId(view: View, name: String): Int {
        resolvedIds[name]?.let { if (it != 0) return it }
        val id = runCatching {
            view.resources.getIdentifier(name, "id", "com.xiaomi.market")
        }.getOrNull() ?: 0
        if (id != 0) resolvedIds[name] = id
        return id
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

    /**
     * 递归查找广告角标文本，最多 5 层。
     *
     * 中文角标用「包含」匹配——实际文案常常是「广告 · 下载」而不是光秃秃的「广告」；
     * 英文标签则必须按**词**匹配，否则 `Adobe` 会因为含 "ad" 被整条误杀。
     */
    private fun hasAdLabel(view: View, depth: Int): Boolean {
        if (depth > 5) return false
        if (view is TextView) {
            val t = view.text?.toString()?.trim()?.lowercase() ?: ""
            if (t.isNotEmpty()) {
                if (cnLabels.any { it in t }) return true
                if (tokens(t).any { it in enLabels }) return true
            }
        }
        if (view !is ViewGroup) return false
        val count = view.childCount.coerceAtMost(16)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            if (hasAdLabel(child, depth + 1)) return true
        }
        return false
    }

    /**
     * 上报尚未识别的榜单项形态，便于精确定位漏网的广告。
     *
     * 已知的正常类型（如 `nativeRankListApps`）会被静默跳过：修复 Fragment 误判后
     * bean 层终于能读到类型，一屏十几个正常项会打出十几行一样的日志，
     * 真正可疑的那一行反而被淹没了。
     */
    private fun logUnknownShape(view: View, bean: Any?) {
        val type = if (bean == null) null else {
            typeGetters.firstNotNullOfOrNull { getter -> readType(bean, getter) }
        }
        if (type != null && normalizeType(type) in knownNormalTypes) return
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
