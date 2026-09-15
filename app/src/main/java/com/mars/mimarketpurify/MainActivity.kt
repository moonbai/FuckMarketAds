package com.mars.mimarketpurify

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mars.mimarketpurify.App.ServiceStateListener
import com.mars.mimarketpurify.Settings.PREFS_GROUP
import com.mars.mimarketpurify.Settings.TAB_ITEMS
import io.github.libxposed.service.XposedService

/**
 * 程序主页：**顶栏固定 + 内容区滚动**。
 *
 * 布局要点：
 *  - 根布局为纵向 [LinearLayout]：固定顶栏（标题 / 副标题 / 框架状态）+ 下方 ScrollView，
 *    因此标题与状态行始终可见，滚动只发生在内容区；
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
    private lateinit var statusView: TextView

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
            // 未激活时写入远程偏好注定失败，用弱化透明度表达“这些开关尚未生效”
            applyUnactivatedDim(service == null)
            refreshAll()
        }
    }

    /** 未激活时把所有依赖远程偏好的开关视觉弱化；恢复激活后还原 */
    private fun applyUnactivatedDim(unactivated: Boolean) {
        val alpha = if (unactivated) Ui.DIM_ALPHA else 1f
        listOfNotNull(masterSwitch, tabFilterSwitch).forEach { it.alpha = alpha }
        allFeatures.forEach { f ->
            content.findViewWithTag<CompoundButton>(f.key)?.alpha = alpha
        }
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

    /** 固定顶栏：标题（含“关于”入口）+ 副标题 + 框架状态 */
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
            setTextColor(Ui.ACCENT)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            tappable(this@MainActivity)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            }
        })
        header.addView(row)

        header.addView(TextView(this).apply {
            text = "小米应用商店净化与增强"
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, 0, 0, dp(6))
        })
        statusView = TextView(this).apply {
            text = "正在连接框架…"
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
            setLineSpacing(0f, 1.4f)
        }
        header.addView(statusView)

        // 顶栏与滚动区之间的分隔线：明确“这里是固定区域”
        header.addView(View(this).apply {
            setBackgroundColor(Ui.DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            )
        })
    }

    private fun buildMasterSwitch() {
        masterSwitch = addSwitchRow(
            title = "总开关",
            summary = "关闭后所有功能均不生效",
            checked = readLocal(Settings.KEY_MASTER, true),
            tag = Settings.KEY_MASTER
        ) { isChecked ->
            writeRemote(Settings.KEY_MASTER, isChecked)
            updateTabChecksEnabled()
        }
    }

    private fun buildCategories() {
        categories.forEach { category ->
            addSectionHeader(category.title, category.subtitle)
            category.features.forEach { f ->
                addSwitchRow(f.title, f.summary, readLocal(f.key, f.default), f.key) { checked ->
                    writeRemote(f.key, checked)
                    if (f.key == Settings.KEY_TAB_FILTER) updateTabChecksEnabled()
                }
                if (f.key == Settings.KEY_TAB_FILTER) buildTabSelectSection()
            }
        }
    }

    /** 模块自身相关的设置（不参与远程偏好） */
    private fun buildModuleSection() {
        addSectionHeader("模块自身", "仅影响本模块的显示方式")
        hideIconSwitch = addSwitchRow(
            title = "隐藏桌面图标",
            summary = "仅移除桌面抽屉中的图标，仍可从 LSPosed 模块列表进入主页",
            checked = isLauncherIconHidden(),
            tag = "hide_launcher_icon"
        ) { hide -> applyHideIcon(hide) }
    }

    /** 区块标题 + 统一的灰小字说明 */
    private fun addSectionHeader(title: String, subtitle: String) {
        content.addView(sectionTitle(title))
        content.addView(TextView(this).apply {
            text = subtitle
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(dp(4), 0, 0, dp(4))
        })
    }

    /**
     * 统一的开关行：[card()] 白底圆角容器内放置标题 + 摘要 + [Switch]。
     * 开关着色统一为“开启 = [Ui.STATE_ACTIVE]”，与标题状态灯呼应。
     */
    private fun addSwitchRow(
        title: String,
        summary: String,
        checked: Boolean,
        tag: String,
        onChanged: (Boolean) -> Unit
    ): CompoundButton {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = dp(12) }
        }
        textWrap.addView(cardTitle(title))
        textWrap.addView(TextView(this).apply {
            text = summary
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, dp(3), 0, 0)
        })

        val sw = Switch(this).apply {
            this.tag = tag
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            tintSwitch(this)
        }

        row.addView(textWrap)
        row.addView(sw)
        card.addView(row)
        content.addView(card)

        if (tag == Settings.KEY_TAB_FILTER) tabFilterSwitch = sw
        return sw
    }

    /** 开关配色：轨道选中为半透明状态绿、未选中为浅灰；滑块恒为白色 */
    private fun tintSwitch(sw: Switch) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
        val trackColors = intArrayOf(0x4D34C759.toInt(), Ui.SWITCH_TRACK_OFF)
        sw.thumbDrawable?.let { sw.thumbDrawable = it.tinted(states, thumbColors) }
        sw.trackDrawable?.let { sw.trackDrawable = it.tinted(states, trackColors) }
    }

    private fun Drawable.tinted(states: Array<IntArray>, colors: IntArray): Drawable {
        val wrapped = DrawableCompat.wrap(this).mutate()
        DrawableCompat.setTintList(wrapped, ColorStateList(states, colors))
        return wrapped
    }

    /** 在筛选开关下方构建“保留哪些标签”的多选列表（整体包进一张卡片） */
    private fun buildTabSelectSection() {
        val card = card()
        card.addView(TextView(this).apply {
            text = "保留哪些底部标签（取消勾选 = 隐藏该标签）"
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, 0, 0, dp(6))
        })

        TAB_ITEMS.forEach { (tag, label) ->
            val cb = CheckBox(this).apply {
                text = label
                textSize = Ui.BODY
                setTextColor(Ui.TEXT_PRIMARY)
                this.tag = tag
                isChecked = readLocalTabs().contains(tag)
                setPadding(dp(12), dp(4), dp(4), dp(4))
                compoundDrawablePadding = dp(10)
                minHeight = dp(Ui.TOUCH_MIN)
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
