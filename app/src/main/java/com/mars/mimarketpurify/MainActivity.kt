package com.mars.mimarketpurify

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mars.mimarketpurify.App.ServiceStateListener
import com.mars.mimarketpurify.Settings.PREFS_GROUP
import com.mars.mimarketpurify.Settings.TAB_ITEMS
import io.github.libxposed.service.XposedService

/**
 * 程序主页：展示模块状态，并提供分类整理后的各功能开关。
 *
 * 布局要点：
 *  - 采用edge-to-edge + [WindowInsetsCompat] 手动处理系统栏内边距，
 *    避免在 targetSdk 36 下内容（总开关行）被状态栏/标题区域遮挡；
 *  - 不使用系统 ActionBar，标题由本界面自行绘制（见 styles.xml AppTheme）。
 *
 * 「隐藏桌面图标」不再禁用本 Activity，而是禁用桌面入口 alias，
 * 保证 LSPosed 等框架始终可以打开主页（详见 manifest 注释）。
 */
class MainActivity : Activity(), ServiceStateListener {

    private var service: XposedService? = null
    private lateinit var container: LinearLayout
    private lateinit var statusView: TextView
    /** 主页标题：颜色随模块激活状态变化（已激活绿 / 未激活橙红） */
    private lateinit var titleView: TextView

    /** 标题色：未激活（含连接中）用品牌橙红，已激活用成功绿 */
    private val colorTitleInactive = 0xFFFF6B35.toInt()
    private val colorTitleActive = 0xFF34C759.toInt()

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
                Feature(Settings.KEY_SEARCH, "移除搜索推荐", "搜索建议、搜索页、搜索结果中的软件推荐", true),
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

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFF2F2F7.toInt())
            // 让内容可以延伸到系统栏下方，由容器自身的内边距控制视觉留白
            clipToPadding = false
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(container)
        setContentView(scroll)
        applySystemBarInsets(scroll)

        buildHeader()
        buildMasterSwitch()
        buildCategories()
        buildModuleSection()
    }

    /**
     * 处理 edge-to-edge：把系统栏 / 刘海的高度加到滚动容器上，
     * 修复 targetSdk 36 下顶部内容被状态栏遮挡的问题。
     */
    private fun applySystemBarInsets(scroll: ScrollView) {
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
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
                if (service == null) colorTitleInactive else colorTitleActive
            )
            if (service == null) {
                statusView.text = "模块未激活：请在 LSPosed / 框架中启用本模块并勾选作用域"
            } else {
                val caps = mutableListOf<String>()
                val props = service.frameworkProperties
                if (props and XposedService.PROP_CAP_REMOTE != 0L) caps += "远程偏好"
                if (props and XposedService.PROP_CAP_SYSTEM != 0L) caps += "系统域"
                statusView.text = buildString {
                    append("已激活 · ${service.frameworkName} ${service.frameworkVersion}")
                    if (caps.isNotEmpty()) append("\n支持：${caps.joinToString("、")}")
                    else append("\n注意：当前框架不支持远程偏好，开关可能不生效")
                }
            }
            refreshAll()
        }
    }

    /** 刷新所有开关的显示状态（框架连接后调用） */
    private fun refreshAll() {
        allFeatures.forEach { f ->
            (container.findViewWithTag<CompoundButton>(f.key))?.isChecked =
                readLocal(f.key, f.default)
        }
        (container.findViewWithTag<CompoundButton>(Settings.KEY_MASTER))?.isChecked =
            readLocal(Settings.KEY_MASTER, true)

        val kept = readLocalTabs()
        tabChecks.forEach { cb ->
            val t = cb.tag
            cb.isChecked = t is String && kept.contains(t)
        }
        updateTabChecksEnabled()
        hideIconSwitch?.isChecked = isLauncherIconHidden()
    }

    private fun buildHeader() {
        // 标题与“关于”入口同一行：标题占满剩余宽度并把按钮挤到右侧
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        titleView = TextView(this).apply {
            text = "Mi Market Purify"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorTitleInactive)
            // weight=1 且 width=0：让标题吃掉剩余空间，避免“关于”被推到屏幕外
            layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleView)

        headerRow.addView(TextView(this).apply {
            text = "关于"
            textSize = 13f
            setTextColor(0xFF007AFF.toInt())
            setPadding(dp(12), dp(6), dp(4), dp(6))
            isClickable = true
            isFocusable = true
            val ta = this@MainActivity.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            )
            setBackgroundResource(ta.getResourceId(0, 0))
            ta.recycle()
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            }
        })
        container.addView(headerRow)

        container.addView(TextView(this).apply {
            text = "小米应用商店净化与增强"
            textSize = 13f
            setTextColor(0xFF8E8E93.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        statusView = TextView(this).apply {
            text = "正在连接框架…"
            textSize = 12f
            setTextColor(0xFF8E8E93.toInt())
            setPadding(0, 0, 0, dp(14))
            setLineSpacing(0f, 1.3f)
        }
        container.addView(statusView)
    }

    private fun buildMasterSwitch() {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_switch, container, false)
        row.findViewById<TextView>(R.id.title).text = "总开关"
        row.findViewById<TextView>(R.id.summary).text = "关闭后所有功能均不生效"
        val sw = row.findViewById<CompoundButton>(R.id.switch_view)
        sw.tag = Settings.KEY_MASTER
        sw.isChecked = readLocal(Settings.KEY_MASTER, true)
        sw.setOnCheckedChangeListener { _, isChecked ->
            writeRemote(Settings.KEY_MASTER, isChecked)
            updateTabChecksEnabled()
        }
        masterSwitch = sw
        container.addView(row)
    }

    private fun buildCategories() {
        categories.forEach { category ->
            addSectionHeader(category.title, category.subtitle)
            category.features.forEach { f ->
                buildFeatureRow(f)
                if (f.key == Settings.KEY_TAB_FILTER) buildTabSelectSection()
            }
        }
    }

    /** 模块自身相关的设置（不参与远程偏好） */
    private fun buildModuleSection() {
        addSectionHeader("模块自身", "仅影响本模块的显示方式")
        buildHideIconRow()
    }

    private fun addSectionHeader(title: String, subtitle: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { lp ->
                lp.topMargin = dp(14)
                lp.bottomMargin = dp(6)
                lp.marginStart = dp(4)
            }
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF48484A.toInt())
        })
        box.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(0xFFA0A0A5.toInt())
        })
        container.addView(box)
    }

    private fun buildFeatureRow(f: Feature) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_switch, container, false)
        row.findViewById<TextView>(R.id.title).text = f.title
        row.findViewById<TextView>(R.id.summary).text = f.summary
        val sw = row.findViewById<CompoundButton>(R.id.switch_view)
        sw.tag = f.key
        sw.isChecked = readLocal(f.key, f.default)
        sw.setOnCheckedChangeListener { _, isChecked ->
            writeRemote(f.key, isChecked)
            if (f.key == Settings.KEY_TAB_FILTER) updateTabChecksEnabled()
        }
        if (f.key == Settings.KEY_TAB_FILTER) tabFilterSwitch = sw
        container.addView(row)
    }

    /** 在筛选开关下方构建“保留哪些标签”的多选列表（整体包进一张卡片） */
    private fun buildTabSelectSection() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val cardLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardLp.bottomMargin = dp(10)
        card.layoutParams = cardLp

        val hint = TextView(this).apply {
            text = "保留哪些底部标签（取消勾选 = 隐藏该标签）"
            textSize = 12f
            setTextColor(0xFF8E8E93.toInt())
            setPadding(0, 0, 0, dp(6))
        }
        card.addView(hint)

        TAB_ITEMS.forEach { (tag, label) ->
            val cb = CheckBox(this).apply {
                text = label
                this.tag = tag
                isChecked = readLocalTabs().contains(tag)
                setPadding(dp(8), dp(4), 0, dp(4))
                setOnCheckedChangeListener { _, _ -> writeTabSelection() }
            }
            tabChecks.add(cb)
            card.addView(cb)
        }
        container.addView(card)
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

    /** 写远程偏好；返回是否写入成功 */
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

    /** 构建“隐藏桌面图标”独立开关（不经过远程偏好，直接操作系统 alias 组件状态） */
    private fun buildHideIconRow() {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_switch, container, false)
        row.findViewById<TextView>(R.id.title).text = "隐藏桌面图标"
        row.findViewById<TextView>(R.id.summary).text =
            "仅移除桌面抽屉中的图标，仍可从 LSPosed 模块列表进入主页"
        val sw = row.findViewById<CompoundButton>(R.id.switch_view)
        sw.isChecked = isLauncherIconHidden()
        sw.setOnCheckedChangeListener { _, isChecked ->
            applyHideIcon(isChecked)
        }
        hideIconSwitch = sw
        container.addView(row)
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
