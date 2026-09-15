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

    /**
     * 应用升级卡片的**渲染方法**：命中即让它们直接返回 null。
     *
     * 思路来自 XiaomiHelper —— 它 hook `MineUpdateView` 的
     * `applyUpdateViewOrchardStyle` / `applyViewOrchardState` /
     * `applyEmptyViewOrchardState` 并 `result(null)`，
     * 从源头上关掉那套「果园皮肤」。
     *
     * 这比在视图层改尺寸稳得多：皮肤（草地背景、异形卡片）本身就是渲染出来的，
     * 拦掉渲染方法就不会画，也谈不上"改坏布局"。
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

        hookOrchardSkin()
    }

    /**
     * 关掉应用升级卡片的「果园皮肤」。
     *
     * 这是 XiaomiHelper 的思路：不去跟渲染结果较劲，而是让渲染方法直接返回 null。
     * 之前我在视图层调尺寸，结果卡片被撑得极高、图标竖排——那是因为
     * 皮肤本身就是一套自绘背景 + 异形约束，改它的尺寸只会把整套约束搞乱。
     */
    private fun hookOrchardSkin() {
        if (!Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) return
        updateViewClasses.forEach { owner ->
            runCatching {
                val cls = ClassUtil.loadClass(owner)
                orchardMethods.forEach { method ->
                    cls.methodFinder()
                        .filterByName(method)
                        .forEach { m ->
                            // 让皮肤渲染方法直接返回 null（block 返回值即方法返回值）
                            m.hooked { null }
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
     * 把「应用升级」卡片**横向铺开**，并把它里面的四个图标排成一行。
     *
     * 踩过的两个坑，都记在这里免得再踩：
     *  1. 早期版本直接改 `update_layout` 的 `layoutParams`（把高度交给内容测量），
     *     结果卡片被撑得极高、底部露出大片背景图——卡片里有背景图层与一堆约束，
     *     改它自己的尺寸会让测量彻底失控。所以现在**高度一律不碰**；
     *  2. `update_icon_layout` 并不是横向容器，之前 `setEqualWeight` 一发现父容器
     *     不是横向就退化成 `fillWidth`（`MATCH_PARENT`），四个图标于是各占满一行，
     *     看着就是竖排。现在先把容器掰成横向，再把图标搬进来等分。
     *
     * 具体三步：
     *  1. **卡片铺满**：父容器是横向 LinearLayout 时给它 `width=0 / weight=1`，
     *     吃掉清理 / 卸载被隐藏后空出来的那一整块；
     *  2. **标题行**（`mine_app_update_title_layout`）撑满宽；
     *  3. **图标行**（`update_icon_layout`）切横向，`app_icon1..4` 搬进去等分；
     *  4. **一键升级按钮**（`update_button_layout`）撑满并在两侧留出内边距，
     *     圆角对齐卡片内边距，避免顶到卡片圆角上。
     *
     * 用 `post` 延后到测量完成后再改，并且每张卡片只处理一次。
     */
    private fun rebuildCard(v: View) {
        if (!tunedCards.add(System.identityHashCode(v))) return
        runCatching {
            val pad = v.paddingLeft.coerceAtLeast(v.paddingRight)
            val tinted = mutableListOf<String>()

            // ① 卡片自己先横向铺开：清理 / 卸载被隐藏后，同排会空出一整块，
            //    卡片不主动吃下这块空间的话就会缩在最左边。
            //    只在父容器是**横向** LinearLayout 时动手——纵向容器上设 weight
            //    会把卡片拉成满屏高，那是上一个版本翻车的样子。
            if (expandInRow(v)) tinted += "卡片铺满"

            findByIdName(v, titleLayoutId)?.let { fillWidth(it); tinted += "标题行" }

            // ② 图标行：强制横向，并把 4 个图标**搬进这一行**等分
            val iconRow = findByIdName(v, "update_icon_layout")
            if (iconRow != null) {
                fillWidth(iconRow)
                forceHorizontal(iconRow)
                val icons = iconNames.mapNotNull { findByIdName(v, it) }
                if (icons.size >= 2) {
                    // 图标未必直接挂在 iconRow 下（可能每个外面还裹了一层），
                    // 那样的话在各自的小容器里设 weight 只会得到「两行各两个」。
                    // 先把它们统一搬到 iconRow 里，再等分。
                    reparentIcons(icons, iconRow)
                    icons.forEach { icon -> setEqualWeight(icon) }
                    tinted += "图标行x${icons.size}"
                    HookEnv.base.log(
                        Log.WARN, TAG,
                        "$name: 图标行容器 = ${iconRow::class.java.simpleName}",
                        null
                    )
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
     *
     * 这一步之前是坏的：只在父容器「恰好已经是横向 [LinearLayout]」时才设 weight，
     * 否则退化成 [fillWidth]（`MATCH_PARENT`）——于是 4 个图标各占满一行，看着就是竖排。
     * 现在改成**先把父容器掰成横向**再设 weight，退化分支只在父容器压根不是
     * LinearLayout 时才走。
     */
    private fun setEqualWeight(target: View) {
        runCatching {
            val parent = target.parent as? LinearLayout
            val lp = target.layoutParams
            if (parent != null && lp is LinearLayout.LayoutParams) {
                if (parent.orientation != LinearLayout.HORIZONTAL) {
                    parent.orientation = LinearLayout.HORIZONTAL
                }
                lp.width = 0
                lp.weight = 1f
                lp.gravity = Gravity.CENTER
                target.layoutParams = lp
                // 图标被拉宽后别被 fitXY 拉变形
                (target as? ImageView)?.scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                fillWidth(target)
            }
            target.requestLayout()
        }
    }

    /** 把容器切成横向（仅 LinearLayout 有效），返回是否成功 */
    private fun forceHorizontal(row: View): Boolean {
        val ll = row as? LinearLayout ?: return false
        if (ll.orientation != LinearLayout.HORIZONTAL) {
            ll.orientation = LinearLayout.HORIZONTAL
        }
        ll.gravity = Gravity.CENTER_VERTICAL
        return true
    }

    /**
     * 把 4 个图标统一搬进 [row]，保证它们**同属一个横向容器**。
     *
     * 商店里这几个图标常被分别裹在自己的小容器里（甚至每个独占一行），
     * 在各自的小容器里设 weight 永远排不成一行。整体搬家是最直接的办法：
     * 只动 View 的父子关系，不改任何尺寸，搬完再由 [setEqualWeight] 等分。
     */
    private fun reparentIcons(icons: List<View>, row: View) {
        // 只往 LinearLayout 里搬：换成 ConstraintLayout 之类的容器会因为
        // 缺少约束直接把图标画成 0 尺寸
        val host = row as? LinearLayout ?: return
        icons.forEach { icon ->
            runCatching {
                val parent = icon.parent as? ViewGroup
                if (parent != null && parent !== host) {
                    parent.removeView(icon)
                    host.addView(icon)
                }
            }
        }
    }

    /**
     * 把卡片在**横向**父容器里撑开：宽度清 0 + weight=1，吃掉同排剩下的空间。
     * 纵向容器一律不动——在那里加 weight 会让卡片顶满整屏高。
     */
    private fun expandInRow(target: View): Boolean {
        runCatching {
            val parent = target.parent as? LinearLayout ?: return false
            if (parent.orientation != LinearLayout.HORIZONTAL) return false
            val lp = target.layoutParams as? LinearLayout.LayoutParams ?: return false
            if (lp.width == 0 && lp.weight > 0f) return false
            lp.width = 0
            lp.weight = 1f
            target.layoutParams = lp
            target.requestLayout()
            return true
        }
        return false
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
