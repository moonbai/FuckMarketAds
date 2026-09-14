package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import com.mars.mimarketpurify.util.invokeAs
import com.mars.mimarketpurify.util.setFieldValue
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * 底部标签栏过滤 + 首页顶栏“云控推广位”清理。
 *
 * ① 底部标签（数据层 `TabInfo.fromJSON`）：
 *    - 沿用本项目的“可勾选保留”策略：按用户在主页勾选的 tag 集合精确保留
 *      （见 [Settings.getKeptTabs]），空集合视为保留全部，避免误清空底栏。
 *
 * ② 首页顶栏 subTab 推广位清理（参考 lisrain/NewFuckMarketAds_Fork v1.3.7）：
 *    - 显式黑名单：已知云控推广 tag / 标题（如「看剧」「短剧」）始终剔除；
 *    - 默认拒绝：当同组 subTab 中存在白名单成员（说明是本盟合 ≥ 某版本的结构）时，
 *      不在白名单内的新增云控 tab 一律移除，可自动挡掉未来换的新马甲；
 *      带“命中白名单成员”校验是为了防止旧版本结构不同导致误杀。
 *
 * ③ 渲染层 `PagerTabsInfo.fromTabInfo`（顶栏最终数据源）收口：
 *    - 即使将来换了别的解析 / 注入路径，顶栏仍要经过这里；
 *    - 结构性启发式：abNormal（特殊字体图标）且不在白名单内 => 云控推广位，移除；
 *    - 若过滤后会导致顶栏被清空，则整体放弃本次修改（不允许清空顶栏）。
 *
 * 所有新增逻辑均以 runCatching / try-catch 兜底：即使字段名或结构因版本变化而取不到，
 * 也只是跳过清理，绝不影响原有的底部标签过滤与其他 hook。
 */
object TabFilter : BaseHook() {

    override val prefKey: String = Settings.KEY_TAB_FILTER

    override val name: String
        get() = "筛选底部TAB标签与云控推广位"

    private const val HOME_TAG = "native_market_home"

    /** 首页顶栏常见正常 subTab（默认拒绝白名单） */
    private val homeSubTabWhitelist by lazy {
        setOf(
            "native_market_feature",       // 推荐
            "native_market_rank_software", // 榜单
            "must-have",                   // 必备
            "GOLDEN MI AWARD",             // 金米奖
            "Classification",              // 分类
            "software_sub5",               // 软件
            "minor"                        // 未成年人模式占位
        )
    }

    /** 已知云控推广位 tag（显式拒绝，优先级高于白名单） */
    private val subBlackTags by lazy {
        setOf("xiaomishipin", "native_market_shortplay", "native_market_agent")
    }

    /** 已知云控推广位标题（显式拒绝） */
    private val subBlackTitles by lazy {
        setOf("看剧", "短剧")
    }

    private var tabField: Field? = null

    override fun init() {
        hookTabInfoParse()
        // 渲染层收口为独立尝试，失败不影响数据层过滤
        runCatching { hookPagerTabsInfo() }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] PagerTabsInfo 收口不可用，跳过: ${it.message}", null)
        }
    }

    // = = = = 公共判定 = = = =

    private fun tagOf(tab: Any): String? {
        tabField?.let { f -> runCatching { return f.get(tab) as? String } }
        return runCatching { tab.invokeAs<String>("getTag") }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun titlesOf(tab: Any): Map<String, String>? =
        runCatching { tab.getFieldValue("titles") as? Map<String, String> }.getOrNull()

    private fun isBlacklisted(tag: String?, titles: Map<String, String>?): Boolean =
        (tag != null && subBlackTags.contains(tag)) ||
            titles?.values?.any { subBlackTitles.contains(it) } == true

    /**
     * 仅当该组 tabs 里存在白名单成员时才启用默认拒绝，
     * 防止旧版本结构不同导致的误杀。
     */
    private fun deniedByWhitelist(parentTag: String?, siblings: List<String?>, tag: String?): Boolean {
        if (parentTag != HOME_TAG) return false
        val looksManaged = siblings.any { it != null && homeSubTabWhitelist.contains(it) }
        val whitelisted = tag != null && homeSubTabWhitelist.contains(tag)
        return looksManaged && !whitelisted
    }

    // = = = = ① 数据层：TabInfo.fromJSON = = = =

    private fun hookTabInfoParse() {
        try {
            val clazz = ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")
            tabField = runCatching {
                clazz.fieldFinder().filterByName("tag").filterByType(String::class.java).firstOrNull()
            }.getOrNull()

            clazz.methodFinder()
                .filterByName("fromJSON")
                .filterByParamCount(1)
                .first()
                .hooked {
                    // 用户勾选要保留的标签集合（按 tag 精确匹配），空集合 = 保留全部
                    val kept = Settings.getKeptTabs()
                    if (kept.isEmpty()) return@hooked proceed()

                    val result = proceed()
                    val list = (result as List<*>).toMutableList()
                    list.removeAll { item ->
                        if (item == null) return@removeAll true
                        val tag = runCatching { tagOf(item) }.getOrNull()
                        // 取不到 tag 的项一律移除，避免残留无法识别的标签
                        tag == null || tag !in kept
                    }
                    // 附加：清理保留下来的标签内部的云控推广 subTab（如「看剧」）
                    list.forEach { runCatching { sanitizeSubTabs(it, 0) } }
                    return@hooked list
                }
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked TabInfo.fromJSON", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] hook TabInfo 失败: ${e.message}", null)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun sanitizeSubTabs(tab: Any?, depth: Int) {
        if (tab == null || depth > 2) return
        val parentTag = runCatching { tagOf(tab) }.getOrNull()
        val subs = runCatching { tab.invokeAs<MutableList<Any>>("getSubTabs") }.getOrNull() ?: return
        if (subs.isEmpty()) return
        val subTags = subs.map { runCatching { tagOf(it) }.getOrNull() }
        val removed = subs.filterIndexed { i, _ ->
            val titles = runCatching { titlesOf(subs[i]) }.getOrNull()
            val hit = isBlacklisted(subTags[i], titles) ||
                deniedByWhitelist(parentTag, subTags, subTags[i])
            if (hit) {
                HookEnv.base.log(
                    Log.INFO, TAG,
                    "[TabFilter] removed subTab: ${subTags[i]}(${titles?.get("cn")}) under $parentTag",
                    null
                )
            }
            hit
        }
        if (removed.isNotEmpty()) subs.removeAll(removed.toSet())
        subs.forEach { runCatching { sanitizeSubTabs(it, depth + 1) } }
    }

    // = = = = ② 渲染层：PagerTabsInfo.fromTabInfo = = = =

    private fun hookPagerTabsInfo() {
        try {
            val clazz = ClassUtil.loadClass("com.xiaomi.market.ui.PagerTabsInfo")
            val method = clazz.methodFinder().filterByName("fromTabInfo").firstOrNull()
                ?: clazz.methodFinder().firstOrNull {
                    Modifier.isStatic(modifiers) &&
                        parameterTypes.size == 1 &&
                        parameterTypes[0].name.endsWith("TabInfo") &&
                        returnType.name.endsWith("PagerTabsInfo")
                }
            if (method == null) {
                HookEnv.base.log(Log.VERBOSE, TAG, "[TabFilter] PagerTabsInfo.fromTabInfo 不存在，跳过", null)
                return
            }
            method.hooked {
                val result = proceed()
                if (result != null) {
                    val parentTag = runCatching { args[0]?.let { tagOf(it) } }.getOrNull()
                    runCatching { filterPagerTabsInfo(result, parentTag) }
                        .onFailure {
                            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] filterPagerTabsInfo: ${it.message}", null)
                        }
                }
                return@hooked result
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PagerTabsInfo.fromTabInfo(${method.name})", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] hook PagerTabsInfo 失败: ${e.message}", null)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun filterPagerTabsInfo(info: Any, parentTag: String?) {
        val tags = info.getFieldValue("tags") as? MutableList<String> ?: return
        if (tags.isEmpty()) return
        val urls = info.getFieldValue("urls") as? MutableList<String>
        val titles = info.getFieldValue("titles") as? MutableList<Map<String, String>>
        val abNormals = info.getFieldValue("abNormals") as? MutableList<Boolean>
        val tabInfos = info.getFieldValue("tabInfos") as? MutableMap<String, Any>

        val dropped = mutableListOf<String>()
        val keepIdx = mutableListOf<Int>()
        tags.forEachIndexed { i, tag ->
            val titleMap = titles?.getOrNull(i)
            val whitelisted = parentTag == HOME_TAG && homeSubTabWhitelist.contains(tag)
            // 特殊字体图标 = 云控推广位的结构性特征
            val promoIcon = abNormals?.getOrNull(i) == true && !whitelisted
            val hit = isBlacklisted(tag, titleMap) || promoIcon ||
                deniedByWhitelist(parentTag, tags, tag)
            if (hit) dropped += "$tag(${titleMap?.get("cn") ?: ""})" else keepIdx += i
        }
        // 不允许把顶栏清空
        if (dropped.isEmpty() || keepIdx.isEmpty()) return
        dropped.forEach {
            HookEnv.base.log(Log.INFO, TAG, "[TabFilter] dropped pager tab: $it under $parentTag", null)
        }
        replaceWith(tags, keepIdx)
        urls?.let { replaceWith(it, keepIdx) }
        titles?.let { replaceWith(it, keepIdx) }
        abNormals?.let { replaceWith(it, keepIdx) }
        tabInfos?.keys?.retainAll(tags.toSet())
        val def = runCatching { info.getFieldValue("defaultSelectedTag") as? String }.getOrNull()
        if (def != null && def !in tags) {
            info.setFieldValue("defaultSelectedTag", tags.firstOrNull())
        }
    }

    private fun <T> replaceWith(list: MutableList<T>, keepIdx: List<Int>) {
        val copy = keepIdx.map { list[it] }
        list.clear()
        list.addAll(copy)
    }
}
