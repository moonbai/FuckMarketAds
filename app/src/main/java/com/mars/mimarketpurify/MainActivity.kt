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

    /** 主页直接展示的广告移除开关：这是最常用的核心功能，不再藏进二级页 */
    private val adFeatures = listOf(
        Feature(Settings.KEY_SPLASH, "移除开屏广告", "屏蔽应用商店启动时的开屏广告", true),
        Feature(Settings.KEY_MAIN_TAB, "禁止前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗", true),
        Feature(Settings.KEY_HOME_FEED, "隐藏信息流广告", "隐藏主页底部视频/应用推荐与热词栏", true),
        Feature(Settings.KEY_SEARCH, "移除搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐", true),
        Feature(Settings.KEY_UPDATE_DL, "移除升级/下载推荐", "应用升级页与下载页的软件推荐", true),
        Feature(Settings.KEY_DETAIL, "移除详情页广告", "应用详情页的广告、评论与推荐位", true),
        Feature(Settings.KEY_RANK, "移除榜单广告", "榜单界面的广告 / 推广卡片", true),
    )

    /** 二级页「「我的」页」里的三个开关，用于在主页入口行显示启用数量 */
    private val mineKeys = listOf(
        Settings.KEY_MINE_RECOMMEND, Settings.KEY_MINE_OFFICIAL_TAB, Settings.KEY_MINE_CLEANUP
    )

    /** 二级页「其他界面净化」里的开关 */
    private val miscKeys = listOf(
        Settings.KEY_SECURITY, Settings.KEY_FRUIT, Settings.KEY_DETAIL_FEATURED
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
            gravity = Gravity.CENTER_VERTICAL
        }
        titleView = TextView(this).apply {
            text = "Mi Market Purify"
            textSize = Ui.HOME_TITLE
            setTypeface(null, Typeface.BOLD)
            setTextColor(Ui.STATE_INACTIVE)
            // weight=1 且 width=0：标题吃掉剩余空间，避免“关于”被推出屏幕
            layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(titleView)
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

        header.addView(TextView(this).apply {
            text = "小米应用商店净化与增强"
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, dp(2), 0, dp(10))
        })
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

    private fun buildCategories() {
        // 广告移除：核心功能，直接放在主页
        addSectionHeader("广告移除", "拦截商店各处的广告与软件推荐")
        val adGroup = groupCard()
        adFeatures.forEach { f ->
            addSwitchRow(
                group = adGroup,
                title = f.title,
                summary = f.summary,
                checked = readLocal(f.key, f.default),
                tag = f.key,
                default = f.default
            ) { checked -> writeRemote(f.key, checked) }
        }
        content.addView(adGroup)

        // 界面净化：按页面拆成三张二级页
        addSectionHeader("界面净化", "按页面拆分，点进去单独调整")
        val uiGroup = groupCard()
        addNavRow(
            group = uiGroup,
            title = "「我的」页",
            summary = "应用推荐、官方入口、清理与卸载",
            value = { countText(mineKeys) }
        ) { openPage(SubSettingsActivity.PAGE_MINE) }
        addNavRow(
            group = uiGroup,
            title = "底部标签栏",
            summary = "隐藏不需要的标签，并清理首页顶栏云控推广位",
            value = { tabsText() }
        ) { openPage(SubSettingsActivity.PAGE_TABS) }
        addNavRow(
            group = uiGroup,
            title = "其他界面净化",
            summary = "应用安全检测、领水果入口、详情页「精选」",
            value = { countText(miscKeys) }
        ) { openPage(SubSettingsActivity.PAGE_MISC) }
        content.addView(uiGroup)

        // 功能增强与细节修正：各只有一项，合成一组，不再各自占一个区块
        addSectionHeader("功能增强", "还原被灰度限制的能力，并做细节修正")
        val extraGroup = groupCard()
        addSwitchRow(
            group = extraGroup,
            title = "启用下载超级岛",
            summary = "强制让下载进度进入小米超级岛（无视灰度）",
            checked = readLocal(Settings.KEY_ISLAND, true),
            tag = Settings.KEY_ISLAND
        ) { on -> writeRemote(Settings.KEY_ISLAND, on) }
        addSwitchRow(
            group = extraGroup,
            title = "细节修正",
            summary = "显示非正版 APP、被隐藏更新等细节处理",
            checked = readLocal(Settings.KEY_MISC, true),
            tag = Settings.KEY_MISC
        ) { on -> writeRemote(Settings.KEY_MISC, on) }
        content.addView(extraGroup)
    }

    /** 模块自身：两项都是低频操作，收进二级页，主页只留一个入口 */
    private fun buildModuleRow() {
        addSectionHeader("模块自身", "仅影响本模块的显示方式")
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

    private data class Feature(
        val key: String,
        val title: String,
        val summary: String,
        val default: Boolean
    )
}
