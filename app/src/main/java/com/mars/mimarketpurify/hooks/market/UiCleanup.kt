package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
 */
object UiCleanup : BaseHook() {

    /** 本 hook 内每个元素各用自己的开关判断，因此不设单一 prefKey */
    override val prefKey: String? = null

    override val name: String
        get() = "界面元素屏蔽"

    /**
     * 「我的」页的三组目标，各自对应一个独立开关——
     * 三件事的副作用完全不同：应用推荐和官方 tab 只是「不显示」，
     * 而清理 / 卸载隐藏后还会把「应用升级」卡片拉宽，值得单独控制。
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

    /** 应用详情页的「精选」入口 */
    private val featuredTexts = setOf("精选")

    /** 缓存解析结果：key -> 已解析的 id 集合，避免每个 View 都做一次资源名解析 */
    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())

    /** 已重排过的「应用升级」卡片，避免反复处理同一个视图 */
    private val tunedCards = Collections.synchronizedSet(mutableSetOf<Int>())

    /** 要把内部零件撑到整行宽的应用升级卡片 */
    private val cardIds = listOf("update_layout", "mine_update_layout")

    /** 卡片里需要横排铺满的图标（app_icon1..4，按 id 名拼） */
    private val iconNames = (1..4).map { "app_icon$it" }

    private val titleLayoutId = "mine_app_update_title_layout"
    private val buttonLayoutId = "update_button_layout"

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
            else if (isCardTarget(v)) v.post { rebuildCard(v) }
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
                id in idSet(v, "cleanup", mineCleanupIds))
    }

    /** 详情页「精选」：按文案命中，并限定只在详情页里生效，避免误伤别处的“精选” */
    private fun isFeaturedTarget(v: View): Boolean {
        if (v !is TextView) return false
        if (!Settings.isEnabled(Settings.KEY_DETAIL_FEATURED, true)) return false
        val text = v.text?.toString()?.trim() ?: return false
        if (text !in featuredTexts) return false
        return v.context?.javaClass?.name?.contains("AppDetailActivity") == true
    }

    /** 需要撑满整行的入口：命中「我的」页的 update_layout */
    private fun isCardTarget(v: View): Boolean {
        if (!Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) return false
        // update_layout 这类 id 在别的页面也可能出现，限定只在主界面动手
        if (v.context?.javaClass?.name?.contains("MarketTabActivity") != true) return false
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false
        return id in idSet(v, "card", cardIds)
    }

    /**
     * 重构「应用升级」卡片内部布局，而不是去动卡片自己的尺寸。
     *
     * 上一版的做法（把卡片拉宽、高度交给内容测量）翻车了：卡片被撑得极高，
     * 图标仍挤在左侧 2x2，底部露出大片背景图。原因很清楚——
     * `update_layout` 里有背景图层与一堆布局约束，直接改它的 `layoutParams`
     * 会让测量结果完全失控。
     *
     * 现在换成「只改零件、不动卡片」：
     *  1. **标题行**（`mine_app_update_title_layout`）撑满宽；
     *  2. **图标行**（`update_icon_layout`）本身已经是横向容器，
     *     把里面 4 个图标 `app_icon1..4` 的宽度清成 0 并给 `weight=1`，
     *     它们就等分整行——**不改容器层级、不搬 View**，所以商店自己的
     *     绑定逻辑最多把 weight 改回去，也不会把卡片搞乱；
     *  3. **一键升级按钮**（`update_button_layout`）撑满并在两侧留出内边距；
     *  4. 按钮圆角对齐卡片内边距，避免顶到卡片圆角上。
     *
     * 用 `post` 延后到测量完成后再改，并且每张卡片只处理一次。
     */
    private fun rebuildCard(v: View) {
        if (!tunedCards.add(System.identityHashCode(v))) return
        runCatching {
            val pad = v.paddingLeft.coerceAtLeast(v.paddingRight)
            val tinted = mutableListOf<String>()

            findByIdName(v, titleLayoutId)?.let { fillWidth(it); tinted += "标题行" }

            // 图标行：先让它满宽，再让 4 个图标等分
            val iconRow = findByIdName(v, "update_icon_layout")
            if (iconRow != null) {
                fillWidth(iconRow)
                val icons = iconNames.mapNotNull { findByIdName(v, it) }
                if (icons.size >= 2) {
                    icons.forEach { icon ->
                        setEqualWeight(icon)
                        // 图标本身要能看见，别被压成 0
                        runCatching { icon.minimumWidth = icon.width.coerceAtLeast(1) }
                    }
                    tinted += "图标行x${icons.size}"
                }
            }

            // 一键升级按钮：撑满 + 两侧留白 + 圆角贴合卡片
            findByIdName(v, buttonLayoutId)?.let { btn ->
                fillWidth(btn)
                (btn.layoutParams as? ViewGroup.MarginLayoutParams)?.let { ml ->
                    ml.marginStart = pad
                    ml.marginEnd = pad
                    btn.layoutParams = ml
                }
                roundButton(btn, pad)
                tinted += "升级按钮"
            }

            if (tinted.isNotEmpty()) {
                HookEnv.base.log(
                    Log.WARN, TAG, "$name: 应用升级卡片已重排（${tinted.joinToString("、")}）", null
                )
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 重排卡片失败 ${it.message}", null)
            tunedCards.remove(System.identityHashCode(v))
        }
    }

    /** 撑满父容器：兼容普通 ViewGroup 与 ConstraintLayout（后者必须用 0dp） */
    private fun fillWidth(target: View) {
        runCatching {
            val lp = target.layoutParams ?: return
            lp.width = if (lp.javaClass.name.contains("ConstraintLayout")) 0
            else ViewGroup.LayoutParams.MATCH_PARENT
            runCatching { lp.javaClass.getField("weight").set(lp, 0f) }
            target.layoutParams = lp
            target.requestLayout()
        }
    }

    /**
     * 让子 View 等分父容器的横向空间。
     * `weight=1` 只有横向 [LinearLayout] 认；父容器不是的话就退化成撑满，
     * 至少不会把图标挤成一条缝。
     */
    private fun setEqualWeight(target: View) {
        runCatching {
            val parent = target.parent as? LinearLayout
            val lp = target.layoutParams
            if (parent != null && parent.orientation == LinearLayout.HORIZONTAL &&
                lp is LinearLayout.LayoutParams
            ) {
                lp.width = 0
                lp.weight = 1f
                lp.gravity = Gravity.CENTER
                target.layoutParams = lp
            } else {
                fillWidth(target)
            }
            target.requestLayout()
        }
    }

    /** 把按钮圆角对齐卡片内边距；只处理粒子白底按钮，不碰图片背景 */
    private fun roundButton(btn: View, pad: Int) {
        if (pad <= 0) return
        runCatching {
            val bg = btn.background as? GradientDrawable ?: return
            val radius = bg.cornerRadius
            // 圆角比内边距小才需要补；已经是胶囊(pad*2 以上)的保持原样更自然
            if (radius > 0f && radius < pad) bg.cornerRadius = pad.toFloat()
        }
    }

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
     * 递归到 6 层、每层最多 24 个子节点——`update_layout` 里零件不多，
     * 这个范围足够，也不会在最坏情况下遍历太久。
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
