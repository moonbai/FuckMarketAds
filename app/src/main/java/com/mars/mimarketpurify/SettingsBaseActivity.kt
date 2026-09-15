package com.mars.mimarketpurify

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mars.mimarketpurify.App.ServiceStateListener
import com.mars.mimarketpurify.Settings.PREFS_GROUP
import io.github.libxposed.service.XposedService

/**
 * 设置类界面的公共基类：主页与所有二级页共用同一套骨架，避免出现
 * “两个页面两套卡片圆角 / 两套开关颜色 / 两套刷新逻辑”的情况。
 *
 * 这里只放**结构**与**远程偏好读写**这两件所有设置页都必需的事：
 *  - 固定顶栏 + 内容区滚动的根布局（[setupRoot]）；
 *  - 带开关的功能行（[addSwitchRow]）与跳转到二级页的导航行（[addNavRow]）；
 *  - 框架连接状态变化时的统一刷新（[refreshAll]）与总开关门控（[updateGateState]）；
 *  - 读 / 写远程偏好（[readLocal] / [writeRemote]）。
 *
 * 具体有哪些功能行由子类决定，基类不预设任何业务开关。
 */
abstract class SettingsBaseActivity : Activity(), ServiceStateListener {

    protected var service: XposedService? = null

    /** 滚动内容区（顶栏之外），子类往这里堆卡片 */
    protected lateinit var content: LinearLayout

    /** 受总开关门控的功能行：总开关关闭时整行转灰且不可点 */
    protected val gatedRows = mutableListOf<SwitchRow>()

    /** 所有绑定远程偏好的开关：key + 默认值 + 控件，供统一刷新 */
    private val switchEntries = mutableListOf<SwitchEntry>()

    /** 所有二级页入口行：右侧摘要需要随偏好变化重新求值 */
    private val navRows = mutableListOf<NavRow>()

    /** 桌面入口 alias 的组件名：隐藏图标时只禁用它 */
    protected val launcherAlias: ComponentName by lazy {
        ComponentName(this, "$packageName.LauncherAlias")
    }

    // ==================== 生命周期与刷新 ====================

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
        runOnUiThread { refreshAll() }
    }

    /** 子类特有的 UI 随框架状态刷新（如主页的状态卡、标题颜色） */
    protected open fun onRefresh() {}

    /**
     * 统一刷新：所有远程偏好开关回到已保存值、入口行摘要重算、门控状态重新应用。
     * 从二级页返回主页时也会走这里，因此主页上「已启用 x/y」这类摘要始终是最新的。
     */
    protected fun refreshAll() {
        switchEntries.forEach { e -> e.sw.isChecked = readLocal(e.key, e.def) }
        navRows.forEach { n -> n.value.text = n.compute() }
        onRefresh()
        updateGateState()
    }

    // ==================== 布局骨架 ====================

    /**
     * 建立「固定顶栏 + 内容区滚动」的根布局。
     * 顶栏不随内容滚动，因此标题始终可见；滚动只发生在 [content]。
     *
     * [header] 由子类创建并在此后填充内容（返回键 / 大标题 / 副标题等），
     * 内边距由 edge-to-edge 的 insets 回调统一分配。
     */
    protected fun setupRoot(header: LinearLayout) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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

        // 状态栏高度加到固定顶栏，导航栏高度加到内容区底部
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

    /** 二级页顶栏：圆形图标返回键 + 页标题 + 底部分隔线 */
    protected fun buildSubTopBar(header: LinearLayout, title: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            // 圆形 mask ripple：反馈被裁成圆形，不会溢出成矩形
            setBackgroundResource(R.drawable.bg_icon_ripple)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "返回"
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(Ui.TOUCH_MIN), dp(Ui.TOUCH_MIN)).also {
                // 抵消图标自身的视觉留白，让箭头恰好落在页面 16dp 边距线上
                it.marginStart = -dp(12)
            }
            setOnClickListener { finish() }
        })
        row.addView(TextView(this).apply {
            text = title
            textSize = Ui.PAGE_TITLE
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Ui.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = -dp(8) }
        })
        header.addView(row)
        header.addView(headerDivider())
    }

    /** 顶栏与滚动区之间的分隔线：明确“这里是固定区域” */
    protected fun headerDivider(): View = View(this).apply {
        setBackgroundColor(Ui.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1).coerceAtLeast(1)
        )
    }

    /** 区块标题 + 统一的灰小字说明 */
    protected fun addSectionHeader(title: String, subtitle: String) {
        content.addView(sectionTitle(title))
        content.addView(TextView(this).apply {
            text = subtitle
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(dp(4), 0, 0, dp(6))
        })
    }

    // ==================== 功能行 ====================

    /**
     * 统一的一行：「标题 + 摘要 + [Switch]」。
     *
     * 开关着色用 HyperOS 蓝 + 较深的关闭态轨道；整行可点，不必再去戳那颗小开关。
     *
     * @param gated 为 true 的行会登记进 [gatedRows]，随总开关一起转灰 / 恢复。
     * @param remote 为 false 表示该行不对应远程偏好（如「隐藏桌面图标」），
     *               不参与统一刷新，由子类在 [onRefresh] 里自行处理。
     */
    protected fun addSwitchRow(
        group: LinearLayout,
        title: String,
        summary: String,
        checked: Boolean,
        tag: String,
        default: Boolean = true,
        gated: Boolean = true,
        remote: Boolean = true,
        onChanged: (Boolean) -> Unit
    ): CompoundButton {
        val row = row()
        val textWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = dp(12) }
        }
        val titleView = rowTitle(title)
        val summaryView = rowSummary(summary)
        textWrap.addView(titleView)
        textWrap.addView(summaryView)

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
        // 圆角 ripple：系统默认的矩形高亮会从分组卡片的圆角处溢出成方角
        row.tappable(this, R.drawable.bg_row_ripple)
        row.setOnClickListener { sw.toggle() }
        // 组内第二行起留少量间距取代分隔线——不画线，靠留白区分相邻两行
        if (group.childCount > 0) {
            (row.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(Ui.ROW_GAP)
        }
        group.addView(row)

        if (remote) switchEntries += SwitchEntry(tag, default, sw)
        if (gated) gatedRows += SwitchRow(row, sw, titleView, summaryView)
        return sw
    }

    /**
     * 跳转到二级页的入口行：「标题 + 摘要 + 当前状态 + 右箭头」。
     *
     * 右侧的 [value] 是一个求值函数而不是固定字符串——从二级页返回时
     * [refreshAll] 会重新求值，「已启用 2/3」这类摘要才不会停留在旧值上。
     */
    protected fun addNavRow(
        group: LinearLayout,
        title: String,
        summary: String,
        gated: Boolean = true,
        value: () -> String,
        onClick: () -> Unit
    ) {
        val row = row()
        val textWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = dp(8) }
        }
        val titleView = rowTitle(title)
        val summaryView = rowSummary(summary)
        textWrap.addView(titleView)
        textWrap.addView(summaryView)

        val valueView = TextView(this).apply {
            text = value()
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
        }
        val arrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams =
                LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginStart = dp(6) }
        }

        row.addView(textWrap)
        row.addView(valueView)
        row.addView(arrow)
        row.tappable(this, R.drawable.bg_row_ripple)
        row.setOnClickListener { onClick() }
        if (group.childCount > 0) {
            (row.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(Ui.ROW_GAP)
        }
        group.addView(row)

        navRows += NavRow(valueView, value)
        if (gated) gatedRows += SwitchRow(row, null, titleView, summaryView)
    }

    /**
     * 统一的门控刷新：总开关关闭时，所有功能行转灰且不可点，
     * 一眼看出当前是整体关闭状态。子类可重写以追加自己的联动（如隐藏子选项）。
     */
    protected open fun updateGateState() {
        val master = readLocal(Settings.KEY_MASTER, true)
        gatedRows.forEach { r ->
            r.sw?.isEnabled = master
            r.row.isClickable = master
            r.row.isFocusable = master
            // 文字切到次级灰而不是降透明度：既表明「已关」，又不至于糊到看不清
            r.title.setTextColor(if (master) Ui.TEXT_PRIMARY else Ui.TEXT_TERTIARY)
            r.summary.setTextColor(if (master) Ui.TEXT_SECONDARY else Ui.TEXT_TERTIARY)
        }
    }

    // ==================== 远程偏好 ====================

    /** 读远程偏好；服务未连接时回落到默认值 */
    protected fun readLocal(key: String, def: Boolean): Boolean {
        return service?.getRemotePreferences(PREFS_GROUP)?.getBoolean(key, def) ?: def
    }

    /** 写远程偏好；写入失败时给出明确反馈 */
    protected fun writeRemote(key: String, value: Boolean) {
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
    protected fun readLocalTabs(): Set<String> {
        val raw = service?.getRemotePreferences(PREFS_GROUP)
            ?.getString(Settings.KEY_TAB_KEEP, Settings.DEFAULT_TAB_KEEP)
            ?: Settings.DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** 写字符串类型远程偏好（用于保存逗号分隔的标签集合） */
    protected fun writeRemoteString(key: String, value: String) {
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

    // ==================== 模块自身 ====================

    /** 当前桌面图标是否已被隐藏（即桌面入口 alias 被禁用） */
    protected fun isLauncherIconHidden(): Boolean {
        return runCatching {
            packageManager.getComponentEnabledSetting(launcherAlias) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    /** 隐藏 / 恢复桌面图标：只切换 alias 组件，MainActivity 始终保持启用 */
    protected fun applyHideIcon(hide: Boolean) {
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

    // ==================== 数据结构 ====================

    /** 一个绑定远程偏好的开关 */
    private data class SwitchEntry(val key: String, val def: Boolean, val sw: CompoundButton)

    /** 一行功能的组成部件，供总开关统一置灰时直接改各部分 */
    protected data class SwitchRow(
        val row: LinearLayout,
        val sw: CompoundButton?,
        val title: TextView,
        val summary: TextView
    )

    /** 一个二级页入口行的「当前状态」文本及其求值函数 */
    private data class NavRow(val value: TextView, val compute: () -> String)
}
