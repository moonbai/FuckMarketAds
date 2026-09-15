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

    /**
     * 应用升级卡片**所在的那一行**。
     *
     * 实测 `update_layout` 直接挂在 `mine_app_update_layout` 这样的行容器下，
     * 隐藏「手机清理 / 应用卸载」后空出来的是**那一行**的空间，
     * 卡片自己不长个宽度——所以必须往上找一层，把行容器本身撑满。
     * 从卡片往上最多找 3 层，遇到横向 LinearLayout 就认。
     */
    private val cardRowNames = listOf(
        "mine_app_update_layout",
        "mine_update_layout",
        "app_update_layout"
    )

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
        if (id !in idSet(v, "card", cardIds)) return false
        // 诊断：卡片自己多大、父容器是什么容器、方向是横向还是纵向。
        // 上一版就是因为不知道这几件事，才一路改错（父容器不是横向
        // LinearLayout 时 weight 根本不生效，卡片自然铺不开）。
        val parent = v.parent as? ViewGroup
        HookEnv.base.log(
            Log.WARN, TAG,
            "$name: 应用升级卡片 ${v.width}x${v.height}，父容器 " +
                "${parent?.let { it::class.java.simpleName } ?: "<无>"}" +
                (parent?.let { if (isHorizontal(it)) "(横向)" else "(纵向/未知)" } ?: "") +
                " ${parent?.width}x${parent?.height}",
            null
        )
        return true
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
     * 具体四步：
     *  1. **那一行铺满**：从卡片往上找行容器（`mine_app_update_layout` 等）
     *     或最近的横向 LinearLayout，给它 `width=0 / weight=1`。
     *     注意撑满的**不是卡片自己**——卡片宽度由行容器决定，
     *     行容器不长个，卡片怎么改都没用；
     *  2. **标题行**（`mine_app_update_title_layout`）撑满宽；
     *  3. **图标**：不管原本挂在哪，统一搬进一条横向行再等分
     *     （沿用它原本的容器，不可用就新建一条）；
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

            // ① 横向铺开。关键是**找对那个容器**：要撑满的往往不是卡片本身，
            //    而是卡片所在的行容器（`mine_app_update_layout` 之类）。
            //    上一版只改卡片、而且父容器不是横向就直接放弃，所以一直没效果。
            when (val r = expandRow(v)) {
                null -> tinted += "铺满失败(找不到横向祖先)"
                else -> tinted += "铺满:${r::class.java.simpleName}"
            }

            findByIdName(v, titleLayoutId)?.let { fillWidth(it); tinted += "标题行" }

            // ② 图标：**不管它们原本在哪个容器里**，一律搬进同一个横向容器再等分。
            //    上一版先把 update_icon_layout 切成横向就撒手，而图标可能压根
            //    不在里面（或各自裹着自己的容器）——结果还是竖排。
            //    与其逐层猜结构，不如直接建一个横向行当东家。
            val icons = iconNames.mapNotNull { findByIdName(v, it) }
            if (icons.size >= 2) {
                val row = ensureIconRow(v, icons[0].parent as? LinearLayout)
                if (row != null) {
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = Gravity.CENTER_VERTICAL
                    reparentIcons(icons, row)
                    icons.forEach { icon -> setEqualWeight(icon) }
                    tinted += "图标行x${icons.size}"
                    HookEnv.base.log(
                        Log.WARN, TAG,
                        "$name: 图标行 = ${row::class.java.simpleName}（原容器 " +
                            "${icons[0].parent?.let { (it as? View)?.let { p -> p::class.java.simpleName } }}）",
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

    /**
     * 找一条横向 LinearLayout 当图标的「东家」。
     *
     * 优先复用图标原本所在的容器（尽量少动结构），但它必须是横向的
     * LinearLayout——否则在别处新建一条横向行。返回 null 表示这次放弃
     * （宁可原样不动，也不要把图标搬进一个摆不对的容器里）。
     */
    private fun ensureIconRow(card: View, existing: LinearLayout?): LinearLayout? {
        if (existing != null && existing !== card) {
            if (existing.orientation != LinearLayout.HORIZONTAL) {
                existing.orientation = LinearLayout.HORIZONTAL
            }
            existing.gravity = Gravity.CENTER_VERTICAL
            return existing
        }
        // 退路：新建一条横向行插进卡片，宽度撑满
        runCatching {
            val host = card as? ViewGroup ?: return null
            val row = LinearLayout(card.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            host.addView(row)
            return row
        }
        return null
    }

    /**
     * 把 4 个图标统一搬进 [row]，保证它们**同属一个横向容器**。
     *
     * 商店里这几个图标常被分别裹在自己的小容器里（甚至每个独占一行），
     * 在各自的小容器里设 weight 永远排不成一行。整体搬家是最直接的办法：
     * 只动 View 的父子关系，不改任何尺寸，搬完再由 [setEqualWeight] 等分。
     */
    private fun reparentIcons(icons: List<View>, row: View) {
        val host = row as? ViewGroup ?: return
        icons.forEach { icon ->
            runCatching {
                val parent = icon.parent as? ViewGroup ?: return@runCatching
                if (parent === host) return@runCatching
                parent.removeView(icon)
                host.addView(icon)
            }
        }
    }

    /**
     * 把「应用升级」那一行撑满整个宽度。
     *
     * 从卡片开始往上找，**最多探 3 层**，命中任一即用：
     *  1. 名字就是行容器（[cardRowNames]）的祖先；
     *  2. 最近的横向 LinearLayout 祖先。
     *
     * 找到之后只设 `width=0 / weight=1`（吃掉同排剩余空间），**不碰高度**——
     * 这个改动对卡片还是对行容器都安全。找不到横向祖先就返回 null，
     * 由调用方记一笔日志，方便下次定位。
     */
    private fun expandRow(card: View): View? {
        runCatching {
            var cur: View = card
            var horizontal: View? = null
            repeat(3) {
                val parent = cur.parent as? View ?: return@repeat
                cur = parent
                val name = nameOf(parent)
                if (name != null && cardRowNames.any { it == name }) {
                    setFillRow(parent)
                    return parent
                }
                if (horizontal == null && isHorizontal(parent)) horizontal = parent
            }
            // 直接父容器就是横向的，优先用它（不必再往上找）
            val direct = card.parent as? View
            if (direct != null && isHorizontal(direct)) {
                setFillRow(direct)
                return direct
            }
            // 卡片自己就挂在一行里（前面找到的横向祖先），但它的宽度是固定的，
            // 这时把**卡片**撑开也有效
            if (horizontal != null) {
                setFillRow(horizontal)
                return horizontal
            }
            return null
        }
        return null
    }

    /** 真正设 weight 的地方；已经是 0/1 就跳过，避免无谓的重排 */
    private fun setFillRow(target: View) {
        runCatching {
            val ll = target as? LinearLayout ?: return
            val lp = ll.layoutParams as? LinearLayout.LayoutParams ?: return
            if (lp.width == 0 && lp.weight > 0f) return
            lp.width = 0
            lp.weight = 1f
            ll.layoutParams = lp
            ll.requestLayout()
        }
    }

    private fun isHorizontal(v: View): Boolean =
        (v as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL

    /** 取资源名；没有 id / 解析失败时返回 null（商店换包名也不会崩） */
    private fun nameOf(v: View): String? {
        if (v.id == View.NO_ID || v.id <= 0) return null
        return runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()
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
