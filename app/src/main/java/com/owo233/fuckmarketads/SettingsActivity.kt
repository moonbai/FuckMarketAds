package com.owo233.fuckmarketads

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HookSettings.init(this)
        fun root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#FAFAFA") }
        fun header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(48), dp(16), dp(16)); setBackgroundColor(Color.parseColor("#1565C0")) }
        header.addView(TextView(this).apply { text = "Fuck Market Ads"; textSize = 22f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        header.addView(TextView(this).apply { text = "去他妈的小米应用商店广告"; textSize = 14f; setTextColor(Color.parseColor("#BBDEFB")); setPadding(0, dp(4), 0, 0) })
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LLinearLayout.LayoutParams.WRAP_CONTENT))
        val scroll = ScrollView(this)
        fun content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        content.addView(makeCard(listOf("说明卡片" to "开关状态立即生效，无需重启应用商店")))
        content.addView(spacer())
        content.addView(TextView(this).apply { text = "功能开关"; textSize = 14f; setTextColor(Color.parseColor("#1565C0")); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, dp(4), dp(8)) })
        val features = listOf(Triple("i��藏应用安全检查", "隐藏应用安装时的安全检查弹窗", HookSettings.KEY_HIDE_SECURITY), Triple("i��分细节处理", "非正版APP重新显示到更新列表", HookSettings.KEY_MISC_APPLY), Triple("g��除广告", "开屏广告、底部栏、搜索推荐等", HookSettings.KEY_REMOVE_ADS), Triple("f��示被隐藏的更新", "解锁商店隐藏的Tab和更新", HookSettings.KEY_TAB_FILTER), Triple("g��用OTA验证", "禁用系统更新验证", HookSettings.KEY_BYPASS_OTA))
        features.forEachIndexed { index, (title, desck, key) -> content.addView(makeSwitchRow(title, desc, key)); if (index < features.size - 1) content.addView(divider()) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LLinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
    private fun makeCard(items: List<Pair<String, String>>): LinearLayout { return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); setBackgroundColor(Color.WHITE); setElevation(dp(2f)); items.forEach { (k, v) -> addView(TextView(this@SettingsActivity).apply { text = "$k: $v2; textSize = 13f; setTextColor(Color.parseColor("#666666")); setPadding(0, dp(2), 0, dp(2)) }) } }
    private fun makeSwitchRow(title: String, desc: String, key: String): LinearLayout { return LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundColor(Color.WHITE); setMinimumHeight(dp(56)); addView(LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LLinearLayout.LayoutParams(0, LLinearLayout.LayoutParams.WRAP_CONTENT, 1f); addView(TextView(this@SettingsActivity).apply { text = title; textSize = 16f; setTextColor(Color.parseColor("#212121") }); addView(TextView(this@SettingsActivity).apply { text = desc; textSize = 12f; setTextColor(Color.parseColor("#999999")); setPadding(0, dp(2), 0, 0) } }); addView(Switch(this@SettingsActivity).apply { isChecked = HookSettings.isEnabled(this@SettingsActivity, key); setOnCheckedChangeListener { _, isChecked -> HookSettings.setEnabled(this@SettingsActivity, key, isChecked) } }) }
    private fun divider(): View { return View(this).apply { setBackgroundColor(Color.parseColor("#F0F0F0"); layoutParams = LinearLayout.LayoutParams(LLinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(dp(16), 0, dp(16)) } } }
    private fun spacer(): View { return View(this).apply { layoutParams = LLinearLayout.LayoutParams(LLinearLayout.LayoutParams.MATCH_PARENT, dp(16)) } }
    private fun dp(value: Int): Int { return (value * resources.displayMetrics.density).toInt() }
    private fun dp(value: Float): Int { return (value * resources.displayMetrics.density).toInt() }
}
