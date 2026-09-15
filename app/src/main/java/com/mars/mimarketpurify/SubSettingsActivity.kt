package com.mars.mimarketpurify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 二级设置页。主页只留高频开关，把「同一页面的一组开关」和「带子选项的功能」
 * 收进这里，避免主页变成一条需要反复滚动的长列表。
 *
 * 具体显示哪一组由 Intent 的 [EXTRA_PAGE] 决定（见 [Companion] 中的 PAGE_*）。
 * 之所以用「一个 Activity + 四种页面」而不是四个 Activity：这四页的结构
 * 完全一致（返回键顶栏 + 一组卡片式开关），差异只在数据，写四遍只会让
 * 样式悄悄分叉。
 */
class SubSettingsActivity : SettingsBaseActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        /** 广告净化：从主页搬过来的 7 个核心开关 */
        const val PAGE_ADS = "ads"
        /** 「我的」页精简的三个开关 */
        const val PAGE_MINE = "mine"
        /** 底部标签栏筛选：一个开关 + 一组「保留哪些标签」多选 */
        const val PAGE_TABS = "tabs"
        /** 其他界面精简项 */
        const val PAGE_MISC = "misc"
        /** 高级净化，从 XiaomiHelper 移植过来的商店规则，各自独立开关 */
        const val PAGE_EXTRA = "extra"
        /** 模块自身设置 */
        const val PAGE_MODULE = "module"

        fun intent(context: Context, page: String): Intent =
            Intent(context, SubSettingsActivity::class.java).putExtra(EXTRA_PAGE, page)
    }

    private var page: String = PAGE_MINE

    /** 「保留哪些标签」的勾选框列表 */
    private val tabChecks = mutableListOf<CheckBox>()
    /** 「保留哪些标签」整块，跟随筛选开关显隐 */
    private var tabSelectBlock: View? = null
    /** 「隐藏桌面图标」开关（独立于远程偏好，直接操作系统组件启用状态） */
    private var hideIconSwitch: CompoundButton? = null

    /**
     * 「广告净化」页的开关定义表。
     * 之前它写在 [MainActivity] 里，现在整组搬过来，两边共用同一份文案。
     */
    private val adFeatures = listOf(
        Feature(Settings.KEY_SPLASH, "移除开屏广告", "屏蔽应用商店启动时的开屏广告"),
        Feature(Settings.KEY_MAIN_TAB, "禁止前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗"),
        Feature(Settings.KEY_HOME_FEED, "隐藏信息流广告", "隐藏主页底部视频/应用推荐与热词栏"),
        Feature(Settings.KEY_SEARCH, "移除搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐"),
        Feature(Settings.KEY_UPDATE_DL, "移除升级/下载推荐", "应用升级页与下载页的软件推荐"),
        Feature(Settings.KEY_DETAIL, "移除详情页广告", "应用详情页的广告、评论与推荐位"),
        Feature(Settings.KEY_RANK, "移除榜单广告", "榜单界面的广告 / 推广卡片")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent?.getStringExtra(EXTRA_PAGE) ?: PAGE_MINE

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        setupRoot(header)
        buildSubTopBar(header, titleOf(page))

        when (page) {
            PAGE_ADS -> buildAds()
            PAGE_MINE -> buildMine()
            PAGE_TABS -> buildTabs()
            PAGE_MISC -> buildMisc()
            PAGE_EXTRA -> buildExtra()
            else -> buildModule()
        }
        // 首屏按已保存的偏好刷新一次（框架绑定后还会再刷新一次）
        refreshAll()
    }

    private fun titleOf(page: String): String = when (page) {
        PAGE_ADS -> "广告净化"
        PAGE_MINE -> "「我的」页精简"
        PAGE_TABS -> "底部标签栏"
        PAGE_MISC -> "其他界面精简"
        PAGE_EXTRA -> "高级净化"
        else -> "模块功能"
    }

    // ==================== 各组页面 ====================

    /**
     * 广告净化：原本平铺在主页的 7 个开关整组搬到这里。
     * 主页只留一行入口，省下的空间给新功能——主页此前已经长到要反复滚动才看得全。
     */
    private fun buildAds() {
        addSectionHeader("广告净化", "拦截商店各处的广告与软件推荐")
        val group = groupCard()
        adFeatures.forEach { f ->
            addSwitchRow(
                group = group,
                title = f.title,
                summary = f.summary,
                checked = readLocal(f.key, true),
                tag = f.key
            ) { on -> writeRemote(f.key, on) }
        }
        content.addView(group)
        addFooter("屏蔽后若页面空白，多为该页组件被整体过滤，关掉对应开关即可恢复。")
    }

    /** 高级净化：从 XiaomiHelper 移植的商店规则，原先挂在别的开关下，现在都独立出来 */
    private fun buildExtra() {
        addSectionHeader("高级净化", "深度运营内容、活动入口、弹窗与角标清理")
        val group = groupCard()
        addSwitchRow(
            group = group,
            title = "隐藏底栏角标",
            summary = "去掉底部标签页的数字角标与「新」字红点",
            checked = readLocal(Settings.KEY_TAB_BADGE, true),
            tag = Settings.KEY_TAB_BADGE
        ) { on -> writeRemote(Settings.KEY_TAB_BADGE, on) }
        addSwitchRow(
            group = group,
            title = "屏蔽首页活动入口",
            summary = "隐藏搜索框左侧云控下发的活动小图标 / 动图",
            checked = readLocal(Settings.KEY_ENTRANCE, true),
            tag = Settings.KEY_ENTRANCE
        ) { on -> writeRemote(Settings.KEY_ENTRANCE, on) }
        addSwitchRow(
            group = group,
            title = "「我的」页推广组",
            summary = "在数据层让底部推广应用列表直接返回空",
            checked = readLocal(Settings.KEY_MINE_AD_GROUP, true),
            tag = Settings.KEY_MINE_AD_GROUP
        ) { on -> writeRemote(Settings.KEY_MINE_AD_GROUP, on) }
        addSwitchRow(
            group = group,
            title = "详情页附加净化",
            summary = "详情页拼装推荐、底部多按钮推广栏、浏览器下载弹窗广告",
            checked = readLocal(Settings.KEY_DETAIL_EXTRAS, true),
            tag = Settings.KEY_DETAIL_EXTRAS
        ) { on -> writeRemote(Settings.KEY_DETAIL_EXTRAS, on) }
        addSwitchRow(
            group = group,
            title = "阻止升级提醒弹窗",
            summary = "不再弹出应用商店的升级提醒对话框",
            checked = readLocal(Settings.KEY_UPDATE_DIALOG, true),
            tag = Settings.KEY_UPDATE_DIALOG
        ) { on -> writeRemote(Settings.KEY_UPDATE_DIALOG, on) }
        content.addView(group)
        addFooter("这些开关会同时作用于「移除升级/下载推荐」等既有功能，关掉后对应位置恢复原样。")
    }

    /** 「我的」页精简：三个开关同属一个页面，天然适合收在一屏里 */
    private fun buildMine() {
        addSectionHeader("「我的」页精简", "清理「我的」页中不需要的板块与推荐")
        val group = groupCard()
        addSwitchRow(
            group = group,
            title = "应用推荐",
            summary = "隐藏页面顶部的应用推荐广告位",
            checked = readLocal(Settings.KEY_MINE_RECOMMEND, true),
            tag = Settings.KEY_MINE_RECOMMEND
        ) { on -> writeRemote(Settings.KEY_MINE_RECOMMEND, on) }
        addSwitchRow(
            group = group,
            title = "官方入口",
            summary = "隐藏页面中间的官方功能入口 tab",
            checked = readLocal(Settings.KEY_MINE_OFFICIAL_TAB, true),
            tag = Settings.KEY_MINE_OFFICIAL_TAB
        ) { on -> writeRemote(Settings.KEY_MINE_OFFICIAL_TAB, on) }
        addSwitchRow(
            group = group,
            title = "清理与卸载",
            summary = "隐藏手机清理与应用卸载入口；屏蔽后会把同排的「应用升级」卡片撑满整行",
            checked = readLocal(Settings.KEY_MINE_CLEANUP, true),
            tag = Settings.KEY_MINE_CLEANUP
        ) { on -> writeRemote(Settings.KEY_MINE_CLEANUP, on) }
        content.addView(group)
        addFooter("改动一般在下次进入「我的」页时生效。")
    }

    /** 底部标签栏：开关 + 多选。多选是这个开关的子选项，所以放在同一页 */
    private fun buildTabs() {
        addSectionHeader("底部标签栏", "隐藏不需要的底部标签，同时清理首页顶栏云控推广位")
        val group = groupCard()
        addSwitchRow(
            group = group,
            title = "启用筛选",
            summary = "关闭后底部标签与顶栏推广位均保持原样",
            checked = readLocal(Settings.KEY_TAB_FILTER, true),
            tag = Settings.KEY_TAB_FILTER
        ) { on ->
            writeRemote(Settings.KEY_TAB_FILTER, on)
            updateGateState()
        }
        buildTabSelectBlock(group)
        content.addView(group)
        addFooter("隐藏标签后需重启一次应用商店才会重建底栏。")
    }

    /** 其他界面精简：使用频率较低的界面清理项 */
    private fun buildMisc() {
        addSectionHeader("其他界面精简", "各类零散页面、弹窗的冗余内容清理")
        val group = groupCard()
        addSwitchRow(
            group = group,
            title = "隐藏应用安全检测",
            summary = "隐藏「我的」页中的应用安全检测视图",
            checked = readLocal(Settings.KEY_SECURITY, true),
            tag = Settings.KEY_SECURITY
        ) { on -> writeRemote(Settings.KEY_SECURITY, on) }
        addSwitchRow(
            group = group,
            title = "屏蔽领水果入口",
            summary = "隐藏福利活动 gif 动图入口（entrance_gif）",
            checked = readLocal(Settings.KEY_FRUIT, true),
            tag = Settings.KEY_FRUIT
        ) { on -> writeRemote(Settings.KEY_FRUIT, on) }
        addSwitchRow(
            group = group,
            title = "隐藏详情页「精选」",
            summary = "按文案匹配，仅在应用详情页生效",
            checked = readLocal(Settings.KEY_DETAIL_FEATURED, true),
            tag = Settings.KEY_DETAIL_FEATURED
        ) { on -> writeRemote(Settings.KEY_DETAIL_FEATURED, on) }
        addSwitchRow(
            group = group,
            title = "升级记录页推荐",
            summary = "隐藏「升级记录」底部的精选推荐、热门下载、大家还安装了",
            checked = readLocal(Settings.KEY_UPDATE_HISTORY, true),
            tag = Settings.KEY_UPDATE_HISTORY
        ) { on -> writeRemote(Settings.KEY_UPDATE_HISTORY, on) }
        addSwitchRow(
            group = group,
            title = "搜索页「也在看」",
            summary = "隐藏搜索结果底部的「搜索 xxx 的人也在看」",
            checked = readLocal(Settings.KEY_SEARCH_ALSO_VIEW, true),
            tag = Settings.KEY_SEARCH_ALSO_VIEW
        ) { on -> writeRemote(Settings.KEY_SEARCH_ALSO_VIEW, on) }
        content.addView(group)
        addFooter("升级记录与搜索结果按标题文案匹配，改版后可能失效，届时请反馈。")
    }

    /** 模块功能：这两项都不参与总开关门控，关闭总开关也不该把它们锁死 */
    private fun buildModule() {
        addSectionHeader("模块功能", "仅影响本模块的显示方式与调试选项")
        val group = groupCard()
        hideIconSwitch = addSwitchRow(
            group = group,
            title = "隐藏桌面图标",
            summary = "仅移除桌面抽屉中的图标，仍可从 LSPosed 模块列表进入主页",
            checked = isLauncherIconHidden(),
            tag = "hide_launcher_icon",
            gated = false,
            remote = false
        ) { hide -> applyHideIcon(hide) }
        addSwitchRow(
            group = group,
            title = "榜单调试提示",
            summary = "开启后进入榜单会输出未识别的视图树（logcat 前缀 [rank-tree]），用于反馈漏网的广告；用完请关掉",
            checked = readLocal(Settings.KEY_RANK_DEBUG, false),
            tag = Settings.KEY_RANK_DEBUG,
            default = false,
            gated = false
        ) { on -> writeRemote(Settings.KEY_RANK_DEBUG, on) }
        content.addView(group)
    }

    private fun addFooter(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(dp(4), dp(2), dp(4), dp(16))
        })
    }

    // ==================== 多选块 ====================

    /** 「保留哪些标签」的多选块：紧跟在启用筛选开关后面，它是这个开关的选项 */
    private fun buildTabSelectBlock(group: LinearLayout) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = dp(2)
                it.bottomMargin = dp(8)
            }
            // 整块再向右让出 12dp：多选是某个开关的**子选项**，
            // 缩进去一点才能一眼看出它从属于上面那行，而不是一个平级功能
            setPadding(dp(12), 0, 0, 0)
        }
        block.addView(TextView(this).apply {
            text = "保留哪些标签（取消勾选 = 隐藏该标签）"
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(dp(Ui.ROW_PAD_H), dp(4), dp(Ui.ROW_PAD_H), dp(2))
        })

        Settings.TAB_ITEMS.forEach { (tag, label) ->
            val cb = CheckBox(this).apply {
                text = label
                textSize = Ui.ROW_TITLE
                setTextColor(Ui.TEXT_PRIMARY)
                this.tag = tag
                isChecked = readLocalTabs().contains(tag)
                setPadding(dp(Ui.ROW_PAD_H), dp(4), dp(4), dp(4))
                compoundDrawablePadding = dp(10)
                minimumHeight = dp(Ui.TOUCH_MIN)
                // 原生 CheckBox 用系统 accent 色，在白底分组里几乎分辨不清，
                // 这里统一成强调蓝 / 明确的灰
                buttonDrawable?.let { buttonDrawable = it.tinted(Ui.ACCENT, Ui.CHECK_OFF) }
                setOnCheckedChangeListener { _, _ -> writeTabSelection() }
            }
            tabChecks.add(cb)
            block.addView(cb)
        }
        group.addView(block)
        tabSelectBlock = block
    }

    /** 根据勾选框状态，把保留标签写回远程偏好（逗号分隔） */
    private fun writeTabSelection() {
        val kept = tabChecks.filter { it.isChecked }.map { it.tag as String }.toSet()
        writeRemoteString(Settings.KEY_TAB_KEEP, kept.joinToString(","))
    }

    // ==================== 刷新与门控 ====================

    override fun onRefresh() {
        hideIconSwitch?.isChecked = isLauncherIconHidden()
        val kept = readLocalTabs()
        tabChecks.forEach { cb ->
            val t = cb.tag
            cb.isChecked = t is String && kept.contains(t)
        }
    }

    override fun updateGateState() {
        super.updateGateState()
        val master = readLocal(Settings.KEY_MASTER, true)
        val filterOn = readLocal(Settings.KEY_TAB_FILTER, true)
        tabSelectBlock?.visibility = if (filterOn) View.VISIBLE else View.GONE
        tabChecks.forEach { it.isEnabled = master && filterOn }
    }

    /** 二级页里的开关条目；主页与二级页共用同一份定义 */
    data class Feature(val key: String, val title: String, val summary: String)
}
