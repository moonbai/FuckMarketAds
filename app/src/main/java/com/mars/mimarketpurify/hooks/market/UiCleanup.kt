package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.graphics.drawable.GradientDrawable
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

    /**
     * 屏蔽同组其它入口后需要**撑满整行**的入口。
     *
     * 「手机清理 / 应用卸载 / 应用更新」在「我的」页是同一行的格子，
     * 把前两个隐藏后第三个仍保持着原来的格宽，右边留一大块空白。
     * 所以隐藏之外还要把它拉宽——否则看起来像是布局坏了。
     */
    private val expandIds = listOf("update_layout", "mine_update_layout")

    /** 缓存解析结果：id -> 是否目标，避免每个 View 都做一次资源名解析 */
    @Volatile
    private var resolvedIds: Set<Int>? = null

    @Volatile
    private var resolvedExpandIds: Set<Int>? = null

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
            else if (isExpandTarget(v)) expand(v)
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

    /** 需要撑满整行的入口：命中「我的」页的 update_layout */
    private fun isExpandTarget(v: View): Boolean {
        if (!Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) return false
        // update_layout 这种通用 id 在别的页面也可能出现，限定只在主界面动手
        if (v.context?.javaClass?.name?.contains("MarketTabActivity") != true) return false
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false
        return id in expandIdSet(v)
    }

    /**
     * 把「应用升级」卡片拉宽到撑满整行，并**顺带把里面的图标网格重排成一行**。
     *
     * 关键取舍：**不去重建商店自己的图标网格**。
     * 那个网格是 RecyclerView / 自绘容器，图标 View 很可能被复用、
     * 事后还会被商店的绑定逻辑改回去——一旦打架就是整块卡片显示错乱。
     * 更稳的办法是只改容器尺寸，让原本 2 列 2 行的格子**自然**摊成 1 行 4 列。
     *
     * 为此做两件事：
     *  1. 卡片撑满 + 右侧内边距与左对齐，否则图标贴着卡片圆角；
     *  2. 卡片高度由**内容测量决定**（把固定高度与最小高度清掉），
     *     否则行数从 2 变 1 之后底部的「一键升级」按钮会被裁掉。
     */
    private fun expand(v: View) {
        runCatching {
            val heightBefore = v.height
            val paddingH = v.paddingLeft.coerceAtLeast(v.paddingRight)
            val lp = v.layoutParams
            if (lp != null) {
                // ConstraintLayout 里 MATCH_PARENT 无效，必须用 0dp（match_constraint）
                lp.width = if (lp.javaClass.name.contains("ConstraintLayout")) 0
                else ViewGroup.LayoutParams.MATCH_PARENT
                // 横向 LinearLayout 的 weight 会盖掉宽度，必须清零
                runCatching { lp.javaClass.getField("weight").set(lp, 0f) }
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                v.layoutParams = lp
            }
            // 内边距左右对齐，图标不会贴着圆角
            v.setPadding(paddingH, v.paddingTop, paddingH, v.paddingBottom)
            clearMinHeight(v)
            v.requestLayout()

            // 高度变了说明行数真的重排了，值得记一笔；没变就别刷日志
            v.post {
                runCatching {
                    if (heightBefore > 0 && v.height != heightBefore) {
                        HookEnv.base.log(
                            Log.WARN, TAG,
                            "$name: 应用升级卡片重排 ${heightBefore}px -> ${v.height}px", null
                        )
                    }
                    roundChildren(v)
                }
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 拉宽失败 ${it.message}", null)
        }
    }

    /**
     * 清掉限制高度的属性。
     * 注意不能反射调用 `setMinimumHeight` / `setMinimumWidth`——
     * 那是 View 的 public 方法，在部分 ROM 上会被内联优化掉（NoSuchMethod）。
     * 只反射自己的字段，失败就放弃（高度已经设成 WRAP_CONTENT，多数情况够用）。
     */
    private fun clearMinHeight(v: View) {
        runCatching {
            val f = View::class.java.getDeclaredField("mMinHeight")
            f.isAccessible = true
            f.setInt(v, 0)
        }
    }

    /**
     * 圆角修正：只处理**圆形/大圆角白底按钮**的反直觉情况——
     * 卡片内边距变大后仍撑满宽度的按钮，左右会顶到卡片圆角上。
     * 给它补上等于卡片内边距的圆角半径，视觉上就「坐」进卡片里了。
     *
     * 只认背景是 [GradientDrawable] 的 View，不碰商店自己的图片背景。
     */
    private fun roundChildren(root: View, depth: Int = 0) {
        if (depth > 3 || root !is ViewGroup) return
        val pad = root.paddingLeft
        val count = root.childCount.coerceAtMost(16)
        for (i in 0 until count) {
            val child = root.getChildAt(i) ?: continue
            runCatching {
                val bg = child.background
                if (bg is GradientDrawable && child.width > 0) {
                    // 只有“已经接近满宽”的按钮才需要收圆角，图标之类不会被误伤
                    val nearlyFull = child.width >= root.width - 2 * pad - 8
                    val radius = bg.cornerRadius
                    if (nearlyFull && radius > 0f && radius < pad) {
                        bg.cornerRadius = pad.toFloat()
                    }
                }
            }
            roundChildren(child, depth + 1)
        }
    }

    /** 首次调用时把 id 名解析成 int 并缓存；解析不到（版本变了）就静默跳过 */
    private fun targetIds(v: View): Set<Int> {
        resolvedIds?.let { return it }
        val set = resolve(v, mineIds)
        resolvedIds = set
        HookEnv.base.log(
            Log.WARN, TAG, "$name: 解析到 ${set.size}/${mineIds.size} 个屏蔽 id", null
        )
        return set
    }

    private fun expandIdSet(v: View): Set<Int> {
        resolvedExpandIds?.let { return it }
        val set = resolve(v, expandIds)
        resolvedExpandIds = set
        HookEnv.base.log(
            Log.WARN, TAG, "$name: 解析到 ${set.size}/${expandIds.size} 个拉宽 id", null
        )
        return set
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
