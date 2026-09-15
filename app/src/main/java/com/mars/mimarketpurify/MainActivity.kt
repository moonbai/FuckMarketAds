package com.mars.mimarketpurify

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mars.mimarketpurify.App.ServiceStateListener
import com.mars.mimarketpurify.Settings.PREFS_GROUP
import com.mars.mimarketpurify.Settings.TAB_ITEMS
import io.github.libxposed.service.XposedService

/**
 * 程序主页：**顶栏固定 + 内容区滚动**，整体遵循 HyperOS 风格的分组卡片布局。
 *
 * 布局要点：
 *  - 根布局为纵向 [LinearLayout]：固定顶栏（标题 / 副标题）+ 下方 ScrollView，
 *    因此标题始终可见，滚动只发生在内容区；
 *  - 功能开关按分组放进 [groupCard()] 容器，组内用 [rowDivider()] 分隔，
 *    而不是每行一张独立卡片——这是 HyperOS 设置的标准形态；
 *  - 每行的「标题 + 摘要 + 开关」整体可点击，点击整行即翻转开关；
 *  - edge-to-edge 的内边距手动分配到顶栏顶部与内容区底部（见 [applySystemBarInsets]）；
 *  - 所有配色、字号、间距、触摸目标尺寸统一取自 [Ui]。
 *
 * 「隐藏桌面图标」不再禁用本 Activity，而是禁用桌面入口 alias，
 * 保证 LSPosed 等框架始终可以打开主页（详见 manifest 注释）。
 */
class MainActivity : Activity(), ServiceStateListener {

    private var service: XposedService? = null
    /** 滚动内容区（顶栏之外） */
    private lateinit var content: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView

    /** 桌面入口 alias 的组件名：隐藏图标时只禁用它 */
    private val launcherAlias: ComponentName by lazy {
        ComponentName(this, "$packageName.LauncherAlias")
    }

    /** 总开关与“筛选底部标签栏”开关的引用，用于级联控制勾选框可用状态 */
    private var masterSwitch: CompoundButton? = null
    private var tabFilterSwitch: CompoundButton? = null
    /** “隐藏桌面图标”开关（独立于远程偏好，直接操作系统组件启用状态） */
    private var hideIconSwitch: CompoundButton? = null
    /** “保留哪些标签”的勾选框列表 */
    private val tabChecks = mutableListOf<CheckBox>()

    /** 按使用场景划分的功能分组 */
    private val categories = listOf(
        Category(
            "广告移除", "拦截商店各处的广告与软件推荐", listOf(
                Feature(Settings.KEY_SPLASH, "移除开屏广告", "屏蔽应用商店启动时的开屏广告", true),
                Feature(Settings.KEY_MAIN_TAB, "禁止前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗", true),
                Feature(Settings.KEY_HOME_FEED, "隐藏信息流广告", "隐藏主页底部视频/应用推荐与热词栏", true),
                Feature(Settings.KEY_SEARCH, "移除搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐", true),
                Feature(Settings.KEY_UPDATE_DL, "移除升级/下载推荐", "应用升级页与下载页的软件推荐", true),
                Feature(Settings.KEY_DETAIL, "移除详情页广告", "应用详情页的广告、评论与推荐位", true),
                Feature(Settings.KEY_RANK, "移除榜单广告", "榜单界面的广告 / 推广卡片", true),
            )
        ),
        Category(
            "界面净化", "清理页面中不需要显示的元素", listOf(
                Feature(Settings.KEY_SECURITY, "隐藏应用安全检测", "隐藏“我的”页中的应用安全检测视图", true),
                Feature(Settings.KEY_FRUIT, "屏蔽领水果入口", "隐藏福利活动 gif 动图入口（entrance_gif）", true),
                Feature(
                    Settings.KEY_TAB_FILTER, "筛选底部标签栏 / 推广位",
                    "勾选要保留的标签；同时清理首页顶栏云控推广位", true
                ),
            )
        ),
        Category(
            "功能增强", "还原被服务端灰度限制的能力", listOf(
                Feature(Settings.KEY_ISLAND, "启用下载超级岛", "强制让下载进度进入小米超级岛（无视灰度）", true),
            )
        ),
        Category(
            "细节修正", "清理之外的体验微调", listOf(
                Feature(Settings.KEY_MISC, "细节修正", "显示非正版 APP、被隐藏更新等细节处理", true),
            )
        ),
    )

    private val allFeatures: List<Feature> = categories.flatMap { it.features }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 入口自愈：曾被旧版本锁出的设备，覆盖安装后自动恢复
        EntryGuardReceiver.ensureEntryEnabled(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        // 固定顶栏：不随内容滚动
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        // height=0 + weight=1：内容区吃掉剩余高度，滚动只发生在这里
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
        applySystemBarInsets(root, header)

        buildHeader(header)
        buildStatusCard()
        buildMasterSwitch()
        buildCategories()
        buildModuleSection()
    }

    /**
     * edge-to-edge：把状态栏高度加到**固定顶栏**，把导航栏高度加到**内容区底部**，
     * 这样既不会被系统栏遮挡，也不会因为整页统一 pad 而让顶栏留白失衡。
     */
    private fun applySystemBarInsets(root: View, header: LinearLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            header.setPadding(dp(Ui.PAGE_H), dp(Ui.PAGE_H) + bars.top, dp(Ui.PAGE_H), dp(12))
            content.setPadding(
                dp(Ui.PAGE_H), dp(6), dp(Ui.PAGE_H), dp(Ui.PAGE_H) + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, true)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        this.service = service
        runOnUiThread {
            // 标题即状态灯：已激活转绿，未激活 / 连接中保持品牌橙红
            titleView.setTextColor(
                if (service == null) Ui.STATE_INACTIVE else Ui.STATE_ACTIVE
            )
            applyStatusCard(service)
            refreshAll()
        }
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

    /** 刷新所有开关的显示状态（框架连接后调用） */
    private fun refreshAll() {
        allFeatures.forEach { f ->
            (content.findViewWithTag<CompoundButton>(f.key))?.isChecked =
                readLocal(f.key, f.default)
        }
        (content.findViewWithTag<CompoundButton>(Settings.KEY_MASTER))?.isChecked =
            readLocal(Settings.KEY_MASTER, true)

        val kept = readLocalTabs()
        tabChecks.forEach { cb ->
            val t = cb.tag
            cb.isChecked = t is String && kept.contains(t)
        }
        updateTabChecksEnabled()
        hideIconSwitch?.isChecked = isLauncherIconHidden()
    }

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

        // 顶栏与滚动区之间的分隔线：明确“这里是固定区域”
        header.addView(View(this).apply {
            setBackgroundColor(Ui.DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            )
        })
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

    private fun buildMasterSwitch() {
        val group = groupCard()
        masterSwitch = addSwitchRow(
            group = group,
            title = "总开关",
            summary = "关闭后所有功能均不生效",
            checked = readLocal(Settings.KEY_MASTER, true),
            tag = Settings.KEY_MASTER
        ) { isChecked ->
            writeRemote(Settings.KEY_MASTER, isChecked)
            updateTabChecksEnabled()
        }
        content.addView(group)
    }

    private fun buildCategories() {
        categories.forEach { category ->
            addSectionHeader(category.title, category.subtitle)
            val group = groupCard()
            category.features.forEachIndexed { index, f ->
                if (index > 0) group.addView(rowDivider())
                addSwitchRow(
                    group = group,
                    title = f.title,
                    summary = f.summary,
                    checked = readLocal(f.key, f.default),
                    tag = f.key
                ) { checked ->
                    writeRemote(f.key, checked)
                    if (f.key == Settings.KEY_TAB_FILTER) updateTabChecksEnabled()
                }
            }
            content.addView(group)
            // 「保留哪些标签」放在整组之后，避免插在行与行之间打断分组的整体感
            if (category.features.any { it.key == Settings.KEY_TAB_FILTER }) buildTabSelectSection()
        }
    }

    /** 模块自身相关的设置（不参与远程偏好） */
    private fun buildModuleSection() {
        addSectionHeader("模块自身", "仅影响本模块的显示方式")
        val group = groupCard()
        hideIconSwitch = addSwitchRow(
            group = group,
            title = "隐藏桌面图标",
            summary = "仅移除桌面抽屉中的图标，仍可从 LSPosed 模块列表进入主页",
            checked = isLauncherIconHidden(),
            tag = "hide_launcher_icon"
        ) { hide -> applyHideIcon(hide) }
        content.addView(group)

        content.addView(TextView(this).apply {
            text = "开关即时生效，无需重启；若个别 ROM 缓存了远程偏好，重启一次应用商店即可。"
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(dp(4), dp(2), dp(4), dp(16))
        })
    }

    /** 区块标题 + 统一的灰小字说明 */
    private fun addSectionHeader(title: String, subtitle: String) {
        content.addView(sectionTitle(title))
        content.addView(TextView(this).apply {
            text = subtitle
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(dp(4), 0, 0, dp(6))
        })
    }

    /**
     * 统一的一行：「标题 + 摘要 + [Switch]」。
     *
     * 开关着色改为 HyperOS 蓝 + 较深的关闭态轨道，解决此前“半透明轨道几乎看不见、
     * 白滑块与浅灰轨道糊在一起”的问题；整行可点，不必再去戳那颗小开关。
     */
    private fun addSwitchRow(
        group: LinearLayout,
        title: String,
        summary: String,
        checked: Boolean,
        tag: String,
        onChanged: (Boolean) -> Unit
    ): CompoundButton {
        val row = row()
        val textWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = dp(12) }
        }
        textWrap.addView(rowTitle(title))
        textWrap.addView(rowSummary(summary))

        val sw = Switch(this).apply {
            this.tag = tag
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            // 用自定义的 track / thumb：28dp 高的轨道把 24dp 的白色滑块完整包住，
            // 系统默认 drawable 的滑块会比轨道高，视觉上像“戳出轨道外”。
            getDrawable(R.drawable.switch_track)
                ?.let { trackDrawable = it.tinted(Ui.ACCENT, Ui.SWITCH_TRACK_OFF) }
            getDrawable(R.drawable.switch_thumb)
                ?.let { thumbDrawable = it }
            // 与轨道等宽，保证滑块滑到两端时左右留白对称
            switchMinWidth = dp(48)
        }

        row.addView(textWrap)
        row.addView(sw)
        row.tappable(this, borderless = false)
        row.setOnClickListener { sw.toggle() }
        group.addView(row)

        if (tag == Settings.KEY_TAB_FILTER) tabFilterSwitch = sw
        return sw
    }

    /** 在筛选开关下方构建“保留哪些标签”的多选列表 */
    private fun buildTabSelectSection() {
        val card = card()
        card.addView(TextView(this).apply {
            text = "保留哪些底部标签（取消勾选 = 隐藏该标签）"
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, 0, 0, dp(6))
        })

        TAB_ITEMS.forEach { (tag, label) ->
            val cb = CheckBox(this).apply {
                text = label
                textSize = Ui.ROW_TITLE
                setTextColor(Ui.TEXT_PRIMARY)
                this.tag = tag
                isChecked = readLocalTabs().contains(tag)
                setPadding(dp(10), dp(4), dp(4), dp(4))
                compoundDrawablePadding = dp(10)
                minimumHeight = dp(Ui.TOUCH_MIN)
                // 原生 CheckBox 用系统 accent 色，在白底分组里几乎分辨不清，
                // 这里统一成强调蓝 / 明确的灰
                buttonDrawable?.let { buttonDrawable = it.tinted(Ui.ACCENT, Ui.CHECK_OFF) }
                setOnCheckedChangeListener { _, _ -> writeTabSelection() }
            }
            tabChecks.add(cb)
            card.addView(cb)
        }
        content.addView(card)
    }

    /** 根据勾选框状态，把保留标签写回远程偏好（逗号分隔） */
    private fun writeTabSelection() {
        val kept = tabChecks.filter { it.isChecked }.map { it.tag as String }.toSet()
        writeRemoteString(Settings.KEY_TAB_KEEP, kept.joinToString(","))
    }

    /** 根据总开关与“筛选底部标签栏”开关，级联控制勾选框是否可操作 */
    private fun updateTabChecksEnabled() {
        val enabled =
            readLocal(Settings.KEY_MASTER, true) && readLocal(Settings.KEY_TAB_FILTER, true)
        tabChecks.forEach { it.isEnabled = enabled }
    }

    /** 读远程偏好；服务未连接时回落到默认值 */
    private fun readLocal(key: String, def: Boolean): Boolean {
        return service?.getRemotePreferences(PREFS_GROUP)?.getBoolean(key, def) ?: def
    }

    /** 写远程偏好；写入失败时给出明确反馈 */
    private fun writeRemote(key: String, value: Boolean) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) {
            Toast.makeText(this, "模块未激活，无法保存", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            prefs.edit()?.putBoolean(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 读取“保留哪些标签”的集合（逗号分隔字符串解析为 tag 集合） */
    private fun readLocalTabs(): Set<String> {
        val raw = service?.getRemotePreferences(PREFS_GROUP)
            ?.getString(Settings.KEY_TAB_KEEP, Settings.DEFAULT_TAB_KEEP)
            ?: Settings.DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** 写字符串类型远程偏好（用于保存逗号分隔的标签集合） */
    private fun writeRemoteString(key: String, value: String) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) {
            Toast.makeText(this, "模块未激活，无法保存", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            prefs.edit()?.putString(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 当前桌面图标是否已被隐藏（即桌面入口 alias 被禁用） */
    private fun isLauncherIconHidden(): Boolean {
        return runCatching {
            packageManager.getComponentEnabledSetting(launcherAlias) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    /** 隐藏 / 恢复桌面图标：只切换 alias 组件，MainActivity 始终保持启用 */
    private fun applyHideIcon(hide: Boolean) {
        runCatching {
            EntryGuardReceiver.ensureEntryEnabled(this)
            val state = if (hide) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            packageManager.setComponentEnabledSetting(
                launcherAlias, state, PackageManager.DONT_KILL_APP
            )
            Toast.makeText(
                this,
                if (hide) "已隐藏桌面图标，可在 LSPosed 模块列表中进入主页"
                else "已恢复桌面图标",
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(this, "操作失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private data class Feature(
        val key: String,
        val title: String,
        val summary: String,
        val default: Boolean
    )

    private data class Category(
        val title: String,
        val subtitle: String,
        val features: List<Feature>
    )
}
