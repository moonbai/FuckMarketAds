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

/**
 * 按**资源 id / 可见文本**屏蔽指定的界面元素。
 *
 * 之所以不写死“第几层第几个子 View”：商店每次改版布局层级都会变，
 * 但资源 id（`mine_ad_container` 这类）和角标文案是相对稳定的锚点。
 *
 * 覆盖两条路径，互为兜底：
 *  - **A**：hook `View.onAttachedToWindow` —— 任何视图一挂上来就检查，
 *    对 Fragment / 懒加载的「我的」页同样有效，且不会先闪一下再消失；
 *  - **B**：进入主界面 / 详情页时再整树补扫一遍，防止路径 A 在某些框架上挂不上。
 *
 * id 与文案都做了“解析不到就跳过”的处理，商店换包名 / 改名都不会崩，只是不生效。
 */
object UiCleanup : BaseHook() {

    /** 本 hook 内每个元素各用自己的开关判断，因此不设单一 prefKey */
    override val prefKey: String? = null

    override val name: String
        get() = "界面元素屏蔽"

    /**
     * 「我的」页：广告容器 / 官方入口 tab / 手机清理入口 / 应用卸载入口。
     *
     * 手机清理与应用卸载在「我的」页是**同一组里的相邻两行**，只隐藏前者时
     * 外层容器仍在，看起来就像没生效；两个一起屏蔽才干净。
     */
    private val mineIds = listOf(
        "mine_ad_container",
        "mine_middle_menu_container",
        // 手机清理在不同版本里 id 不同，两种都列上；解析不到的会自动跳过
        "phone_clear_forbid_layout",
        "phone_clear_layout",
        "mine_uninstall_app_layout"
    )

    /** 应用详情页的「精选」入口 */
    private val featuredTexts = setOf("精选")

    /** 缓存解析结果：id -> 是否目标，避免每个 View 都做一次资源名解析 */
    @Volatile
    private var resolvedIds: Set<Int>? = null

    override fun init() {
        // 路径 A：视图一 attach 就检查
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

        // 路径 B：进入这两个页面时整树补扫
        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")
    }

    private fun hookActivityRescan(className: String) {
        runCatching {
            ClassUtil.loadClass(className)
                .methodFinder()
                .filterByName("onResume")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        (thisObject as? Activity)?.window?.decorView
                            ?.let { scanTree(it, 0) }
                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $className，跳过补扫", null)
        }
    }

    private fun scanTree(v: View, depth: Int) {
        if (depth > 8) return
        inspect(v)
        if (v !is ViewGroup) return
        val count = v.childCount.coerceAtMost(32)
        for (i in 0 until count) {
            scanTree(v.getChildAt(i) ?: continue, depth + 1)
        }
    }

    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            if (isMineTarget(v) || isFeaturedTarget(v)) hide(v)
        }
    }

    /** 「我的」页的三个容器：按资源 id 精确命中 */
    private fun isMineTarget(v: View): Boolean {
        if (!Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) return false
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false
        return id in targetIds(v)
    }

    /** 详情页「精选」：按文案命中，并限定只在详情页里生效，避免误伤别处的“精选” */
    private fun isFeaturedTarget(v: View): Boolean {
        if (v !is TextView) return false
        if (!Settings.isEnabled(Settings.KEY_DETAIL_FEATURED, true)) return false
        val text = v.text?.toString()?.trim() ?: return false
        if (text !in featuredTexts) return false
        return v.context?.javaClass?.name?.contains("AppDetailActivity") == true
    }

    /** 首次调用时把 id 名解析成 int 并缓存；解析不到（版本变了）就静默跳过 */
    private fun targetIds(v: View): Set<Int> {
        resolvedIds?.let { return it }
        val set = mineIds.mapNotNull { name ->
            val id = runCatching {
                v.resources.getIdentifier(name, "id", "com.xiaomi.market")
            }.getOrNull()
            if (id != null && id > 0) id else null
        }.toSet()
        resolvedIds = set
        HookEnv.base.log(
            Log.WARN, TAG, "$name: 解析到 ${set.size}/${mineIds.size} 个目标 id", null
        )
        return set
    }

    private fun hide(v: View) {
        runCatching {
            v.visibility = View.GONE
            v.layoutParams?.let { lp ->
                lp.height = 0
                v.layoutParams = lp
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 隐藏失败 ${it.message}", null)
        }
    }
}
