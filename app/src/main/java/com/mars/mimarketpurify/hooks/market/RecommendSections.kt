package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.Collections

/**
 * 按**板块标题文案**整块隐藏推荐位。
 *
 * 两个目标页面的共同点：推荐位是「一个标题 + 一排应用」组成的卡片，
 * 标题文案稳定（精选推荐 / 搜索 xxx 的人也在看），但容器 id / 类名随版本乱变，
 * 按 id 匹配基本抓不住。所以这里反过来——先认标题，再往上找到整块卡片的根，
 * 把根整个隐藏，应用列表自然一起消失。
 *
 * 两个页面各用一个独立开关（[Settings.KEY_UPDATE_HISTORY] / [Settings.KEY_SEARCH_ALSO_VIEW]，
 * 因此本 hook 不设单一 prefKey。
 */
object RecommendSections : BaseHook() {

    override val prefKey: String? = null

    override val name: String
        get() = "推荐板块屏蔽"

    /** 「升级记录」页底部三类推荐的标题 */
    private val historyTitles = setOf(
        "精选推荐", "热门下载", "大家还安装了", "大家还装了", "大家还在装"
    )

    /** 「升级记录」页 */
    private const val HISTORY_HOST = "UpdateHistoryActivity"

    /** 搜索结果页「搜索 xxx 的人也在看」——文案带搜索词，只能按片段匹配 */
    private val alsoViewTokens = listOf("也在看", "还在看", "还看了")

    /** 搜索结果页 */
    private const val SEARCH_HOST = "SearchActivityPhone"

    /** 已隐藏过的板块标题，避免日志刷屏 */
    private val reported = Collections.synchronizedSet(mutableSetOf<String>())

    override fun init() {
        // 路径 A：任何视图一挂上来就检查，滚动加载的新卡片也能覆盖
        runCatching {
            ClassUtil.loadClass("android.view.View")
                .methodFinder()
                .filterByName("onAttachedToWindow")
                .first()
                .hooked {
                    val result = proceed()
                    (thisObject as? View)?.let { inspect(it) }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: View.onAttachedToWindow 挂钩失败", it)
        }

        // 路径 B：进入这两个页面时整树补扫一遍，防止路径 A 在某些框架上挂不上
        hookRescan("com.xiaomi.market.ui.UpdateHistoryActivity")
        hookRescan("com.xiaomi.market.ui.SearchActivityPhone")
    }

    private fun hookRescan(className: String) {
        runCatching {
            ClassUtil.loadClass(className)
                .methodFinder()
                .filterByName("onResume")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        (thisObject as? Activity)?.window?.decorView?.let { scanTree(it, 0) }
                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $className，跳过补扫", null)
        }
    }

    private fun scanTree(v: View, depth: Int) {
        if (depth > 10) return
        inspect(v)
        if (v !is ViewGroup) return
        val count = v.childCount.coerceAtMost(32)
        for (i in 0 until count) {
            scanTree(v.getChildAt(i) ?: continue, depth + 1)
        }
    }

    private fun inspect(v: View) {
        if (v !is TextView) return
        if (v.visibility != View.VISIBLE) return
        val text = v.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        val host = v.context?.javaClass?.name.orEmpty()
        if (host.isEmpty()) return
        val hit = when {
            host.contains(HISTORY_HOST) &&
                Settings.isEnabled(Settings.KEY_UPDATE_HISTORY, true) &&
                text in historyTitles -> true

            host.contains(SEARCH_HOST) &&
                Settings.isEnabled(Settings.KEY_SEARCH_ALSO_VIEW, true) &&
                alsoViewTokens.any { text.contains(it) } -> true

            else -> false
        }
        if (hit) hideSection(v, text)
    }

    /**
     * 从标题往上找整块卡片的根，然后隐藏它。
     *
     * 判断依据有两条，谁先命中用谁：
     *  - 父容器是 RecyclerView → 当前节点就是列表里的一个 item（整块卡片）；
     *  - 退而求其次，取「宽度接近满屏、且比标题高」的最近祖先。
     *
     * 另外挡了一道：高度超过屏幕 70% 的祖先一律不认，
     * 免得一路找到 decorView 把整个页面干掉。
     */
    private fun hideSection(title: View, text: String) {
        runCatching {
            val dm = title.resources.displayMetrics
            val fullW = (dm.widthPixels * 0.7f).toInt()
            val maxH = (dm.heightPixels * 0.7f).toInt()
            var cur = title
            var best: View? = null
            repeat(7) {
                val parent = cur.parent as? View ?: return@repeat
                // 父容器是 RecyclerView → cur 就是列表里的整块卡片，直接用它
                if (isRecycler(parent)) {
                    best = cur
                    return@repeat
                }
                cur = parent
                if (cur.width >= fullW && cur.height > title.height && cur.height <= maxH) {
                    best = cur
                }
            }
            val target = best ?: return
            if (target.visibility == View.GONE) return
            target.visibility = View.GONE
            target.layoutParams?.let { lp ->
                lp.height = 0
                target.layoutParams = lp
            }
            if (reported.add(text)) {
                HookEnv.base.log(Log.WARN, TAG, "$name: 已隐藏推荐板块「$text」", null)
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 隐藏「$text」失败 ${it.message}", null)
        }
    }

    /** 按类名判断 RecyclerView，避免为此引入 recyclerview 依赖 */
    private fun isRecycler(v: View): Boolean =
        v is ViewGroup && v::class.java.name.contains("RecyclerView")
}
