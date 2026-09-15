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
        /** 「我的」页的三个开关 */
        const val PAGE_MINE = "mine"
        /** 底部标签栏筛选：一个开关 + 一组「保留哪些标签」多选 */
        const val PAGE_TABS = "tabs"
        /** 其他界面净化项 */
        const val PAGE_MISC = "misc"
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
            PAGE_MINE -> buildMine()
            PAGE_TABS -> buildTabs()
            PAGE_MISC -> buildMisc()
            else -> buildModule()
        }
        // 首屏按已保存的偏好刷新一次（框架绑定后还会再刷新一次）
        refreshAll()
    }

    private fun titleOf(page: String): String = when (page) {
        PAGE_MINE -> "「我的」页"
        PAGE_TABS -> "底部标签栏"
        PAGE_MISC -> "其他界面净化"
        else -> "模块自身"
    }

    // ==================== 各组页面 ====================

    /** 「我的」页：三个开关同属一个页面，天然适合收在一屏里 */
    private fun buildMine() {
        addSectionHeader("「我的」页", "清理「我的」页中不需要的板块")
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

    /** 使用频率较低的界面清理项 */
    private fun buildMisc() {
        addSectionHeader("其他界面净化", "主页未列出、但仍可单独关闭的清理项")
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
        content.addView(group)
    }

    /** 模块自身：这两项都不参与总开关门控，关闭总开关也不该把它们锁死 */
    private fun buildModule() {
        addSectionHeader("模块自身", "仅影响本模块的显示方式")
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
}
