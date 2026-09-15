package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
 * Fix 2026‑09‑16：
 * - 头像/个人信息屏蔽无效：新增针对顶部信息区的**主动深度搜索**，不只依赖View自id命中
 * - 手机清理留白：移除强制修改layoutParams宽高；增加子项全部隐藏后尝试隐藏父行兜底
 * - 彻底移除全部更新卡片拉伸、布局重排代码；仅保留去除果园背景与padding
 * - KEY_MINE_SUMMARY 默认值保持 false（与设置页一致），开关打开后主动扫描整树查找目标
 * - mine_summary_root不再作为主锚点（很多版本无有效ID），放到备选末尾
 */
object UiCleanup : BaseHook() {

    override val prefKey: String? = null
    override val name: String = "界面元素屏蔽"

    private val mineRecommendIds = listOf("mine_ad_container")
    private val mineTabIds = listOf("mine_middle_menu_container")

    private val mineCleanupIds = listOf(
        "phone_clear_forbid_layout",
        "phone_clear_layout",
        "mine_uninstall_app_layout",
        "mine_clean_root_layout"
    )

    /**
     * 头像、昵称、消息、收藏：优先子控件，mine_summary_root仅作为备选
     */
    private val mineSummaryIds = listOf(
        "mine_avatar",
        "mine_nickname",
        "mine_message",
        "mine_message_layout",
        "mine_favorites",
        "mine_favorites_count",
        "mine_favorites_layout",
        "mine_favorites_arrow",
        "mine_summary_root"
    )

    private val featuredTexts = setOf("精选")

    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())

    private val orchardMethods = listOf(
        "applyUpdateViewOrchardStyle",
        "applyViewOrchardState",
        "applyEmptyViewOrchardState"
    )
    private val updateViewClasses = listOf(
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateView",
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateLayout"
    )

    override fun init() {
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

        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        hookOrchardSkin()
    }

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
                        val decor = (thisObject as? Activity)?.window?.decorView ?: return@hooked result
                        scanTree(decor, 0)
                        // 头像区开关打开时，额外一轮深度查找，兜底懒加载Fragment
                        if(Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)){
                            deepFindAndHide(decor, idSet(decor,"summary",mineSummaryIds))
                        }
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
        // 手机清理兜底：如果本行所有可见子View全部隐藏，尝试隐藏父行
        if(Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)){
            runCatching {
                val selfIdSet = idSet(v,"cleanup",mineCleanupIds)
                if(v.id in selfIdSet && v is ViewGroup){
                    var anyVisible = false
                    for(i in 0 until v.childCount){
                        val child = v.getChildAt(i)
                        if(child.visibility == View.VISIBLE) anyVisible = true
                    }
                    if(!anyVisible){
                        hide(v)
                    }
                }
            }
        }
    }

    /**
     * 主动深度遍历，专门给头像区兜底；不依赖onAttachedToWindow命中
     */
    private fun deepFindAndHide(root: View, targetIds: Set<Int>, depth: Int = 0){
        if(depth >12) return
        if(root.id in targetIds){
            hide(root)
        }
        if(root !is ViewGroup) return
        for(i in 0 until root.childCount){
            deepFindAndHide(root.getChildAt(i) ?: continue, targetIds, depth+1)
        }
    }

    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            if (isMineTarget(v) || isFeaturedTarget(v)) hide(v)
        }
    }

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

    private fun isFeaturedTarget(v: View): Boolean {
        if (v !is TextView) return false
        if (!Settings.isEnabled(Settings.KEY_DETAIL_FEATURED, true)) return false
        val text = v.text?.toString()?.trim() ?: return false
        if (text !in featuredTexts) return false
        return v.context?.javaClass?.name?.contains("AppDetailActivity") == true
    }

    private fun idSet(v: View, key: String, names: List<String>): Set<Int> {
        resolved[key]?.let { return it }
        val set = resolve(v, names)
        resolved[key] = set
        HookEnv.base.log(
            Log.WARN, TAG, "$name: $key 解析到 ${set.size}/${names.size} 个 id", null
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

    /**
     * 基础隐藏：只用GONE，不再强行修改layoutParams宽高，避免商店布局回写覆盖
     */
    private fun hide(v: View) {
        runCatching {
            v.visibility = View.GONE
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 隐藏失败 ${it.message}", null)
        }
    }
}
