package com.mars.mimarketpurify

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.service.XposedService

/**
 * 程序主页：**顶栏固定 + 内容区滚动**，整体遵循 HyperOS 风格的分组卡片布局。
 *
 * 主页只保留**高频开关**：广告移除这一组，以及功能增强两项。其余按「同一个页面」
 * 或「带子选项」为维度收进 [SubSettingsActivity]——主页此前近二十行开关需要反复
 * 滚动才能看全，而其中大半属于「我的」页 / 底部标签栏这类局部设置，平时很少动。
 *
 * 布局要点：
 *  - 根布局为纵向 [LinearLayout]：固定顶栏（标题 / 副标题）+ 下方 ScrollView，
 *    因此标题始终可见，滚动只发生在内容区；
 *  - 功能开关按分组放进 [groupCard()] 容器，组内不画分隔线、只用少量留白分行，
 *    而不是每行一张独立卡片——这是 HyperOS 设置的标准形态；
 *  - 每行的「标题 + 摘要 + 开关」整体可点击，点击整行即翻转开关；
 *  - 所有配色、字号、间距、触摸目标尺寸统一取自 [Ui]。
 *
 * 「隐藏桌面图标」不再禁用本 Activity，而是禁用桌面入口 alias，
 * 保证 LSPosed 等框架始终可以打开主页（详见 manifest 注释）。
 */
class MainActivity : SettingsBaseActivity() {

    private lateinit var titleView: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView

    /** 二级页「广告移除」里的 7 个开关，用于在主页入口行显示启用数量 */
    private val adKeys = listOf(
        Settings.KEY_SPLASH, Settings.KEY_MAIN_TAB, Settings.KEY_HOME_FEED, Settings.KEY_SEARCH,
        Settings.KEY_UPDATE_DL, Settings.KEY_DETAIL, Settings.KEY_RANK
    )

    /** 二级页「「我的」页」里的四个开关，用于在主页入口行显示启用数量 */
    private val mineKeys = listOf(
        Settings.KEY_MINE_RECOMMEND,
        Settings.KEY_MINE_OFFICIAL_TAB,
        Settings.KEY_MINE_CLEANUP,
        Settings.KEY_MINE_SUMMARY
    )
    
    /** 二级页「其他界面净化」里的开关 */
    private val miscKeys = listOf(
        Settings.KEY_SECURITY,
        Settings.KEY_FRUIT,
        Settings.KEY_DETAIL_FEATURED,
        Settings.KEY_UPDATE_HISTORY,
        Settings.KEY_SEARCH_ALSO_VIEW
    )

    /** 二级页「额外净化」里的开关（移植自 XiaomiHelper） */
    private val extraKeys = listOf(
        Settings.KEY_TAB_BADGE,
        Settings.KEY_ENTRANCE,
        Settings.KEY_MINE_AD_GROUP,
        Settings.KEY_DETAIL_EXTRAS,
        Settings.KEY_UPDATE_DIALOG
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 入口自愈：曾被旧版本锁出的设备，覆盖安装后自动恢复
        EntryGuardReceiver.ensureEntryEnabled(this)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        setupRoot(header)

        buildHeader(header)
        buildStatusCard()
        buildMasterSwitch()
        buildCategories()
        buildModuleRow()
        // 首次进入就按已保存的总开关状态刷新一次置灰
        refreshAll()
    }

    // ==================== 顶栏与状态卡 ====================

    /** 固定顶栏：大标题 + 副标题 + 右上角强调色胶囊「关于」入口 */
    private fun buildHeader(header: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL // 整行垂直居中，左侧文字块与右侧按钮整体居中
        }

        // 左侧：标题+副标题 封装成垂直LinearLayout，实现两行文字作为整体和右侧按钮居中对齐
        val leftTextBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleView = TextView(this).apply {
            text = "Mi Market Purify"
            textSize = Ui.HOME_TITLE
            setTypeface(null, Typeface.BOLD)
            setTextColor(Ui.STATE_INACTIVE)
        }
        val subTitleTv = TextView(this).apply {
            text = "小米应用商店净化与增强"
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, dp(2), 0, dp(10))
        }

        leftTextBlock.addView(titleView)
        leftTextBlock.addView(subTitleTv)

        row.addView(leftTextBlock)
        row.addView(TextView(this).apply {
            text = "关于"
            textSize = Ui.CAPTION
            setTypeface(null, Typeface.BOLD)
            setTextColor(Ui.ACCENT)
            // 自带 ripple 的胶囊背景：反馈被裁剪在圆角内，不会溢出成矩形
            setBackgroundResource(R.drawable.bg_pill_accent)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            minimumHeight = dp(40)
            setMinWidth(dp(64))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            }
        })
        header.addView(row)
        header.addView(headerDivider())
    }

    /** 内容区顶部的激活状态卡：把“开关能不能用”讲清楚，而不是把它们调暗 */
    private fun buildStatusCard() {
        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Ui.CARD_PAD_H), dp(Ui.CARD_PAD_V), dp(Ui.CARD_PAD_H), dp(Ui.CARD_PAD_V))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(12) }
            background = softBackground(Ui.STATE_INACTIVE_SOFT)
        }
        statusTitle = TextView(this).apply {
            text = "正在连接框架…"
            textSize = Ui.ROW_TITLE
            setTypeface(null, Typeface.BOLD)
            setTextColor(Ui.STATE_INACTIVE)
        }
        statusBody = TextView(this).apply {
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECTION)
            setLineSpacing(0f, 1.4f)
            setPadding(0, dp(4), 0, 0)
        }
        statusCard.addView(statusTitle)
        statusCard.addView(statusBody)
        content.addView(statusCard)
    }

    /**
     * 用一张**状态卡**说明模块当前是否可用，取代此前把所有开关调暗 45% 的做法。
     *
     * 调暗虽然能表达“暂不生效”，但代价是开关本身几乎看不清。
     * 现在开关始终保持正常对比度，原因改由这张卡片讲清楚。
     */
    private fun applyStatusCard(service: XposedService?) {
        if (service == null) {
            statusTitle.text = "模块未激活"
            statusTitle.setTextColor(Ui.STATE_INACTIVE)
            statusBody.text =
                "以下开关暂时改不动远程偏好：请在 LSPosed / 框架中启用本模块，" +
                    "并在作用域里勾选「应用商店」，然后重启应用商店。"
            statusCard.background = softBackground(Ui.STATE_INACTIVE_SOFT)
            return
        }
        val remote = service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L
        statusTitle.text = "已激活 · ${service.frameworkName} ${service.frameworkVersion}"
        statusTitle.setTextColor(Ui.STATE_ACTIVE)
        statusBody.text = if (remote) {
            "支持远程偏好：开关改动实时生效，一般无需重启应用商店。"
        } else {
            "当前框架不支持远程偏好，开关可能不会立即生效，建议重启一次应用商店。"
        }
        statusCard.background = softBackground(Ui.STATE_ACTIVE_SOFT)
    }

    /** 状态卡的淡色圆角背景 */
    private fun softBackground(color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(16f)
        }

    // ==================== 主页内容 ====================

    private fun buildMasterSwitch() {
        val group = groupCard()
        // 新增：状态卡与总开关卡片之间增加上边距
        group.layoutParams = (group.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = dp(12)
        }
        addSwitchRow(
            group = group,
            title = "总开关",
            summary = "关闭后所有功能均不生效",
            checked = readLocal(Settings.KEY_MASTER, true),
            tag = Settings.KEY_MASTER,
            gated = false
        ) { isChecked ->
            writeRemote(Settings.KEY_MASTER, isChecked)
            updateGateState()
        }
        content.addView(group)
    }

    /**
     * 主页只放**分类入口**，具体开关全部收进二级页。
     *
     * 按功能划分为四大块：广告净化、界面精简、高级功能、模块功能。
     * - 广告净化：纯粹的各类广告、推荐内容移除
     * - 界面精简：首页、底栏、我的页、零散页面的布局与冗余项清理
     * - 高级功能：深度运营内容净化 + 常驻的功能增强开关
     * - 模块功能：本模块自身的设置项
     * 【仅调整主页分组、标题、顺序、section；所有Key、二级页、计数逻辑无改动】
     */
    private fun buildCategories() {
        addSectionHeader("净化设置", "广告与界面冗余内容清理")
        val uiGroup = groupCard()
        addNavRow(
            group = uiGroup,
            title = "广告净化",
            summary = "开屏、首页信息流、榜单、搜索、下载升级、应用详情广告",
            value = { countText(adKeys) }
        ) { openPage(SubSettingsActivity.PAGE_ADS) }
    
        addNavRow(
            group = uiGroup,
            title = "首页与标签栏",
            summary = "管理底部标签、清理首页云控推广位",
            value = { tabsText() }
        ) { openPage(SubSettingsActivity.PAGE_TABS) }
    
        addNavRow(
            group = uiGroup,
            title = "「我的」页精简",
            summary = "我的页应用推荐、官方入口、清理板块",
            value = { countText(mineKeys) }
        ) { openPage(SubSettingsActivity.PAGE_MINE) }
    
        addNavRow(
            group = uiGroup,
            title = "其他界面精简",
            summary = "安全检测、领水果、升级记录、搜索相关推荐等零散页面",
            value = { countText(miscKeys) }
        ) { openPage(SubSettingsActivity.PAGE_MISC) }
        content.addView(uiGroup)
    
        addSectionHeader("高级功能", "深度净化与功能增强")
        val advancedGroup = groupCard()
        addNavRow(
            group = advancedGroup,
            title = "高级净化",
            summary = "运营弹窗、活动入口、角标、详情页附加推广",
            value = { countText(extraKeys) }
        ) { openPage(SubSettingsActivity.PAGE_EXTRA) }
    
        addSwitchRow(
            group = advancedGroup,
            title = "启用下载超级岛",
            summary = "强制让下载进度进入小米超级岛（无视灰度）",
            checked = readLocal(Settings.KEY_ISLAND, true),
            tag = Settings.KEY_ISLAND
        ) { on -> writeRemote(Settings.KEY_ISLAND, on) }
        addSwitchRow(
            group = advancedGroup,
            title = "细节修正",
            summary = "显示非正版 APP、被隐藏更新等细节处理",
            checked = readLocal(Settings.KEY_MISC, true),
            tag = Settings.KEY_MISC
        ) { on -> writeRemote(Settings.KEY_MISC, on) }
        content.addView(advancedGroup)
    }
    
    /** 模块自身：独立成「模块功能」区块 */
    private fun buildModuleRow() {
        addSectionHeader("模块功能", "仅影响本模块的显示与调试")
        val group = groupCard()
        addNavRow(
            group = group,
            title = "模块自身设置",
            summary = "隐藏桌面图标、榜单调试提示",
            gated = false,
            value = { moduleText() }
        ) { openPage(SubSettingsActivity.PAGE_MODULE) }
        content.addView(group)
    
        content.addView(TextView(this).apply {
            text = "开关即时生效，无需重启；若个别 ROM 缓存了远程偏好，重启一次应用商店即可。"
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(dp(4), dp(2), dp(4), dp(16))
        })
    }
    
    // ==================== 入口行摘要 ====================

    /** 「已启用 2/3」：一眼看出二级页里开了几项 */
    private fun countText(keys: List<String>): String =
        "已启用 ${keys.count { readLocal(it, true) }}/${keys.size}"

    private fun tabsText(): String {
        if (!readLocal(Settings.KEY_TAB_FILTER, true)) return "已关闭"
        val hidden = Settings.TAB_ITEMS.size - readLocalTabs().size
        return if (hidden <= 0) "未隐藏" else "已隐藏 $hidden 个"
    }

    private fun moduleText(): String {
        val debug = readLocal(Settings.KEY_RANK_DEBUG, false)
        return if (debug) "调试已开" else "2 项"
    }

    private fun openPage(page: String) {
        startActivity(SubSettingsActivity.intent(this, page))
    }

    // ==================== 刷新 ====================

    override fun onRefresh() {
        // 标题即状态灯：已激活转绿，未激活 / 连接中保持品牌橙红
        titleView.setTextColor(if (service == null) Ui.STATE_INACTIVE else Ui.STATE_ACTIVE)
        applyStatusCard(service)
    }

}
