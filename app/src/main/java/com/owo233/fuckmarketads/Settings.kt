package com.owo233.fuckmarketads

import android.util.Log
import com.owo233.fuckmarketads.TAG
import io.github.libxposed.api.XposedModule
import kotlin.concurrent.Volatile

/**
 * 功能开关的集中管理。
 *
 * 读取：在 hooked 进程内通过 libxposed 的 [XposedModule.getRemotePreferences] 读取，
 *       该接口由框架在进程间同步，对 hooked app 只读。
 * 写入：在模块自身的 App 进程内通过 [io.github.libxposed.service.XposedService]
 *       写入同一 group，二者使用相同的 [PREFS_GROUP] 即保持同步。
 *
 * 关键修复点：读取被包在 runCatching 中，当框架不支持 remote preferences（或尚未就绪）时，
 * 回落为默认值（开启），保证模块不会因为“读不到开关”而整体失效。
 */
object Settings {

    /** remote preferences 的分组名，读写两端必须一致 */
    const val PREFS_GROUP = "settings"

    // ===== 开关 key =====
    /** 总开关：关闭后所有功能都不生效 */
    const val KEY_MASTER = "master"

    /** 开屏广告 */
    const val KEY_SPLASH = "splash_ads"

    /** 禁止前台/主页广告与推荐（MarketTabActivity 相关） */
    const val KEY_MAIN_TAB = "main_tab_ads"

    /** 隐藏主页信息流广告低栏 / 热词 */
    const val KEY_HOME_FEED = "home_feed_ads"

    /** 搜索相关推荐（建议 / 搜索页 / 搜索结果） */
    const val KEY_SEARCH = "search_ads"

    /** 升级页 / 下载页软件推荐 */
    const val KEY_UPDATE_DL = "update_download_ads"

    /** 应用详情页广告、评论与推荐 */
    const val KEY_DETAIL = "detail_ads"

    /** 隐藏应用安全检测视图 */
    const val KEY_SECURITY = "hide_security"

    /** 精简底部标签栏（总开关：是否启用标签筛选） */
    const val KEY_TAB_FILTER = "tab_filter"

    /**
     * 底部标签栏“保留哪些标签”的选择集合（逗号分隔存储）。
     * 由用户在主页勾选后写入；hook 侧读取并据此动态保留。
     * 空字符串 / 空集合视为“保留全部”，避免误清空底栏。
     */
    const val KEY_TAB_KEEP = "tab_keep"

    /** [KEY_TAB_KEEP] 的默认值：仅保留「首页 / 我的」，与改造前行为一致 */
    const val DEFAULT_TAB_KEEP = "native_market_home,native_market_mine"

    /**
     * 已知底部标签（tag -> 显示名）。用于主页多选 UI。
     * 不同版本可能增删标签，这里列出较全的常见项；运行时不存在的标签不影响过滤。
     */
    val TAB_ITEMS: LinkedHashMap<String, String> = linkedMapOf(
        "native_market_home" to "首页",
        "native_market_mine" to "我的",
        "native_market_video" to "视频号",
        "native_market_agent" to "智能体",
        "native_app_assemble" to "应用号",
        "native_market_game" to "游戏",
        "native_market_rank" to "榜单",
    )

    /** 细节修正（非正版 / 隐藏更新等） */
    const val KEY_MISC = "misc_apply"

    /** 屏蔽「领水果」活动入口（gif 动图 ImageView：entrance_gif） */
    const val KEY_FRUIT = "hide_fruit_entry"

    /** 移除「榜单」界面广告 / 推广卡片 */
    const val KEY_RANK = "rank_ads"

    /**
     * 每次读取都重新获取远程偏好对象，避免持有进程内快照导致“开关改了不生效”。
     * libxposed 的远程偏好本身支持跨进程实时更新，但不同版本行为不一，
     * 这里选择每次即时读取，代价极小（仅在广告/推荐相关方法被调用时触发一次 provider 查询）。
     */
    private fun getPrefs(): android.content.SharedPreferences? {
        return runCatching {
            (HookEnv.base as XposedModule).getRemotePreferences(PREFS_GROUP)
        }.onFailure { e ->
            HookEnv.base.log(Log.WARN, TAG, "无法读取远程偏好（开关将使用默认值）: ${e.message}", null)
        }.getOrNull()
    }

    /** 总开关是否开启，默认开启 */
    fun isMasterEnabled(): Boolean = isEnabled(KEY_MASTER, true)

    /** 指定开关是否开启；默认值由 [def] 决定（绝大多数功能默认开启） */
    fun isEnabled(key: String, def: Boolean = true): Boolean {
        return getPrefs()?.getBoolean(key, def) ?: def
    }

    /**
     * 读取“保留哪些底部标签”的集合（按 tag）。
     * 存储为逗号分隔字符串，避免依赖远程偏好对 [Set] 的支持差异。
     * 空集合（用户未勾选任何项 / 未写入）视为保留全部，hook 侧据此不裁剪底栏。
     */
    fun getKeptTabs(): Set<String> {
        val raw = getPrefs()?.getString(KEY_TAB_KEEP, DEFAULT_TAB_KEEP) ?: DEFAULT_TAB_KEEP
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
