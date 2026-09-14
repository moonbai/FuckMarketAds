package com.owo233.fuckmarketads

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.owo233.fuckmarketads.App.ServiceStateListener
import com.owo233.fuckmarketads.Settings.PREFS_GROUP
import com.owo233.fuckmarketads.Settings.TAB_ITEMS
import io.github.libxposed.service.XposedService

/**
 * 程序主页：展示模块状态，并提供各功能开关。
 *
 * 开关状态通过 [XposedService.getRemotePreferences] 写入，hook 进程内通过
 * [Settings] 读取同一 group，从而实现跨进程生效。
 */
class MainActivity : Activity(), ServiceStateListener {

    private var service: XposedService? = null
    private lateinit var container: LinearLayout
    private lateinit var statusView: TextView

    /** 总开关与“筛选底部标签栏”开关的引用，用于级联控制勾选框可用状态 */
    private var masterSwitch: android.widget.Switch? = null
    private var tabFilterSwitch: android.widget.Switch? = null
    /** “保留哪些标签”的勾选框列表 */
    private val tabChecks = mutableListOf<CheckBox>()

    /** 每一个开关：key、标题、说明、默认值 */
    private val features = listOf(
        Feature(Settings.KEY_SPLASH, "移除开屏广告", "屏蔽应用商店启动时的开屏广告", true),
        Feature(Settings.KEY_MAIN_TAB, "禁止前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗", true),
        Feature(Settings.KEY_HOME_FEED, "隐藏信息流广告低栏", "隐藏主页底部视频/应用推荐与热词栏", true),
        Feature(Settings.KEY_SEARCH, "移除搜索推荐", "搜索建议、搜索页、搜索结果中的软件推荐", true),
        Feature(Settings.KEY_UPDATE_DL, "移除升级/下载推荐", "应用升级页与下载页的软件推荐", true),
        Feature(Settings.KEY_DETAIL, "移除详情页广告", "应用详情页的广告、评论与推荐位", true),
        Feature(Settings.KEY_SECURITY, "隐藏应用安全检测", "隐藏“我的”页中的应用安全检测视图", true),
        Feature(Settings.KEY_TAB_FILTER, "筛选底部标签栏", "勾选要保留的标签，其余隐藏", true),
        Feature(Settings.KEY_MISC, "细节修正", "显示非正版/被隐藏更新等细节处理", true),
        Feature(Settings.KEY_OTA, "禁用 OTA 验证", "系统更新中禁用 OTA 校验（Updater）", true),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(container)
        setContentView(scroll)

        buildHeader()
        buildMasterSwitch()
        val divider = TextView(this).apply {
            text = "功能开关"
            textSize = 13f
            setPadding(0, dp(12), 0, dp(4))
        }
        container.addView(divider)
        features.forEach { buildFeatureRow(it) }
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
            // 刷新各开关当前值
            features.forEach { f ->
                (container.findViewWithTag<CompoundButton>(f.key))?.isChecked =
                    readLocal(f.key, f.default)
            }
            (container.findViewWithTag<CompoundButton>(Settings.KEY_MASTER))?.isChecked =
                readLocal(Settings.KEY_MASTER, true)

            // 刷新底部标签勾选状态
            val kept = readLocalTabs()
            tabChecks.forEach { cb ->
                val t = cb.tag
                cb.isChecked = t is String && kept.contains(t)
            }
            updateTabChecksEnabled()
        }
    }

    private fun buildHeader() {
        container.addView(TextView(this).apply {
            text = "Fuck Market Ads"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })
        statusView = TextView(this).apply {
            text = "正在连接框架…"
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(statusView)
    }

    private fun buildMasterSwitch() {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_switch, container, false)
        row.findViewById<TextView>(R.id.title).text = "总开关"
        row.findViewById<TextView>(R.id.summary).text = "关闭后所有功能均不生效"
        val sw = row.findViewById<android.widget.Switch>(R.id.switch_view)
        sw.tag = Settings.KEY_MASTER
        sw.isChecked = readLocal(Settings.KEY_MASTER, true)
        sw.setOnCheckedChangeListener { _, isChecked ->
            writeRemote(Settings.KEY_MASTER, isChecked)
            updateTabChecksEnabled()
        }
        masterSwitch = sw
        container.addView(row)
    }

    private fun buildFeatureRow(f: Feature) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_switch, container, false)
        row.findViewById<TextView>(R.id.title).text = f.title
        row.findViewById<TextView>(R.id.summary).text = f.summary
        val sw = row.findViewById<android.widget.Switch>(R.id.switch_view)
        sw.tag = f.key
        sw.isChecked = readLocal(f.key, f.default)
        sw.setOnCheckedChangeListener { _, isChecked ->
            writeRemote(f.key, isChecked)
            // 总开关或筛选开关变动时，需要重新评估勾选框是否可用
            if (f.key == Settings.KEY_TAB_FILTER) updateTabChecksEnabled()
        }
        container.addView(row)

        // 在“筛选底部标签栏”这一行下面，追加“保留哪些标签”的勾选列表
        if (f.key == Settings.KEY_TAB_FILTER) {
            tabFilterSwitch = sw
            buildTabSelectSection()
        }
    }

    /** 在筛选开关下方构建“保留哪些标签”的多选列表 */
    private fun buildTabSelectSection() {
        val hint = TextView(this).apply {
            text = "保留哪些底部标签（取消勾选 = 隐藏该标签）"
            textSize = 12f
            setPadding(dp(8), dp(8), 0, dp(4))
        }
        container.addView(hint)

        TAB_ITEMS.forEach { (tag, label) ->
            val cb = CheckBox(this).apply {
                text = label
                this.tag = tag
                isChecked = readLocalTabs().contains(tag)
                setPadding(dp(28), dp(2), 0, dp(2))
                setOnCheckedChangeListener { _, _ -> writeTabSelection() }
            }
            tabChecks.add(cb)
            container.addView(cb)
        }
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private data class Feature(
        val key: String,
        val title: String,
        val summary: String,
        val default: Boolean
    )
}
