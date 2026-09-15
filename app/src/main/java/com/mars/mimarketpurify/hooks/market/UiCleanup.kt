package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections
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
 *
 * 新增：我的顶部信息区屏蔽（头像、昵称、消息、收藏）KEY_MINE_SUMMARY
 * Update: 2026‑09‑16 —— **暂时注释全部卡片拉伸逻辑**
 */
object UiCleanup : BaseHook() {

    /** 本 hook 内每个元素各用自己的开关判断，因此不设单一 prefKey */
    override val prefKey: String? = null

    override val name: String
        get() = "界面元素屏蔽"

    /**
     * 「我的」页的四组目标，各自对应一个独立开关
     * 新增：mineSummaryIds 顶部信息区（头像、昵称、消息、收藏）
     */
    private val mineRecommendIds = listOf("mine_ad_container")

    private val mineTabIds = listOf("mine_middle_menu_container")

    /**
     * 手机清理与应用卸载在「我的」页是**同一组里的相邻两行**，只隐藏前者时
     * 外层容器仍在，看起来就像没生效；两个一起屏蔽才干净。
     * 手机清理在不同版本里 id 不同，两种都列上；解析不到的会自动跳过。
     */
    private val mineCleanupIds = listOf(
        "phone_clear_forbid_layout",
        "phone_clear_layout",
        "mine_uninstall_app_layout"
    )

    /**
     * 我的页顶部信息区：头像、名称、消息、收藏
     */
    private val mineSummaryIds = listOf(
        "mine_summary_root",
        "mine_avatar",
        "mine_nickname",
        "mine_message",
        "mine_message_layout",
        "mine_favorites",
        "mine_favorites_count",
        "mine_favorites_layout",
        "mine_favorites_arrow"
    )

    /** 应用详情页的「精选」入口 */
    private val featuredTexts = setOf("精选")

    /** 缓存解析结果：key -> 已解析的 id 集合，避免每个 View 都做一次资源名解析 */
    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())

    //region ========== 【暂时注释：应用升级卡片拉伸逻辑，需要时取消注释】 ==========
    ///** 已重排过的「应用升级」卡片，避免反复处理同一个视图 */
    //private val tunedCards = Collections.synchronizedSet(mutableSetOf<Int>())
    //
    ///**
    // * 应用升级卡片**所在的那一行**。
    // *
    // * 实测 `update_layout` 直接挂在 `mine_app_update_layout` 这样的行容器下，
    // * 隐藏「手机清理 / 应用卸载」后空出来的是**那一行**的空间，
    // * 卡片自己不长个宽度——所以必须往上找一层，把行容器本身撑满。
    // * 从卡片往上最多找 3 层，遇到横向 LinearLayout 就认。
    // */
    //private val cardRowNames = listOf(
    //    "mine_app_update_layout",
    //    "mine_update_layout",
    //    "app_update_layout"
    //)
    //
    ///** 要把内部零件撑到整行宽的应用升级卡片 */
    //private val cardIds = listOf("update_layout", "mine_update_layout")
    //
    ///** 卡片里需要横排铺满的图标（app_icon1..4，按 id 名拼） */
    //private val iconNames = (1..4).map { "app_icon$it" }
    //
    //private val titleLayoutId = "mine_app_update_title_layout"
    //private val buttonLayoutId = "update_button_layout"
    //endregion

    /**
     * 应用升级卡片的**渲染方法**：关掉果园皮肤（保留，不注释）
     *
     * 思路来自 XiaomiHelper —— hook `MineUpdateView` 的
     * `applyUpdateViewOrchardStyle` / `applyViewOrchardState` /
     * `applyEmptyViewOrchardState`
     */
    private val orchardMethods = listOf(
        "applyUpdateViewOrchardStyle",
        "applyViewOrchardState",
        "applyEmptyViewOrchardState"
    )

    /** 应用升级卡片的 View 类（果园皮肤与卡片内部都在这里） */
    private val updateViewClasses = listOf(
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateView",
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateLayout"
    )

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

        hookOrchardSkin()
    }

    /**
     * 关掉应用升级卡片的「果园皮肤」。
     */
    private fun hookOrchardSkin() {
        updateViewClasses.forEach { owner ->
            runCatching {
                val cls = ClassUtil.loadClass(owner)
                orchardMethods.forEach { method ->
                    cls.methodFinder()
                        .filterByName(method)
                        .forEach { m ->
                            m.hooked {
                                val result = proceed()
                                (thisObject as? View)?.let { view ->
                                    view.background = null
                                    view.setPadding(0, 0, 0, 0)
                                }
                                result
                            }
                        }
                }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $owner，跳过果园皮肤处理", null)
            }
        }
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
            // 只做元素屏蔽；卡片拉伸逻辑已注释
            if (isMineTarget(v) || isFeaturedTarget(v)) hide(v)
            // else if (isCardTarget(v)) v.post { rebuildCard(v) }
        }
    }

    /** 「我的」页的目标：每组各看自己的开关，按资源 id 精确命中 */
    private fun isMineTarget(v: View): Boolean {
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false
        return (Settings.isEnabled(Settings.KEY_MINE_RECOMMEND, true) &&
                id in idSet(v, "recommend", mineRecommendIds)) ||
                (Settings.isEnabled(Settings.KEY_MINE_OFFICIAL_TAB, true) &&
                        id in idSet(v, "tab", mineTabIds)) ||
                (Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true) &&
                        id in idSet(v, "cleanup", mineCleanupIds)) ||
                (Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false) &&
                        id in idSet(v, "summary", mineSummaryIds))
    }

    /** 详情页「精选」：按文案命中，并限定只在详情页里生效，避免误伤别处的“精选” */
    private fun isFeaturedTarget(v: View): Boolean {
        if (v !is TextView) return false
        if (!Settings.isEnabled(Settings.KEY_DETAIL_FEATURED, true)) return false
        val text = v.text?.toString()?.trim() ?: return false
        if (text !in featuredTexts) return false
        return v.context?.javaClass?.name?.contains("AppDetailActivity") == true
    }

    //region ========== 【暂时注释：卡片拉伸全部方法】 ==========
    ///** 需要撑满整行的入口：命中「我的」页的 update_layout */
    //private fun isCardTarget(v: View): Boolean {
    //    if (!Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) return false
    //    if (v.context?.javaClass?.name?.contains("MarketTabActivity") != true) return false
    //    val id = v.id
    //    if (id == View.NO_ID || id <= 0) return false
    //    if (id !in idSet(v, "card", cardIds)) return false
    //    val parent = v.parent as? ViewGroup
    //    HookEnv.base.log(
    //        Log.WARN, TAG,
    //        "$name: 应用升级卡片 ${v.width}x${v.height}，父容器 " +
    //                "${parent?.let { it::class.java.simpleName } ?: "<无>"}" +
    //                (parent?.let { if (isHorizontal(it)) "(横向)" else "(纵向/未知)" } ?: "") +
    //                " ${parent?.width}x${parent?.height}",
    //        null
    //    )
    //    return true
    //}
    //
    //private fun rebuildCard(v: View) { ... }
    //private fun fillWidth(target: View) { ... }
    //private fun setEqualWeight(target: View) { ... }
    //private fun ensureIconRow(card: View, existing: LinearLayout?): LinearLayout? { ... }
    //private fun reparentIcons(icons: List<View>, row: View) { ... }
    //private fun expandRow(card: View): View? { ... }
    //private fun setFillRow(target: View) { ... }
    //private fun isHorizontal(v: View): Boolean = (v as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL
    //private fun roundButton(btn: View, pad: Int) { ... }
    //endregion

    /** 按缓存键取一组已解析的 id；每组只在首次用到时解析一次 */
    private fun idSet(v: View, key: String, names: List<String>): Set<Int> {
        resolved[key]?.let { return it }
        val set = resolve(v, names)
        resolved[key] = set
        HookEnv.base.log(
            Log.WARN, TAG, "$name: $key 解析到 ${set.size}/${names.size} 个 id", null
        )
        return set
    }

    /**
     * 按资源名在子树里找 View。
     */
    private fun findByIdName(view: View, name: String, depth: Int = 0): View? {
        val id = idSet(view, "single:$name", listOf(name)).firstOrNull() ?: return null
        return findByIdDeep(view, id, depth)
    }

    private fun findByIdDeep(view: View, id: Int, depth: Int): View? {
        if (view.id == id) return view
        if (depth >= 6 || view !is ViewGroup) return null
        val count = view.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            findByIdDeep(child, id, depth + 1)?.let { return it }
        }
        return null
    }

    private fun resolve(v: View, names: List<String>): Set<Int> =
        names.mapNotNull { n ->
            val id = runCatching {
                v.resources.getIdentifier(n, "id", "com.xiaomi.market")
            }.getOrNull()
            if (id != null && id > 0) id else null
        }.toSet()

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
