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

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#FAFAFA"))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(16), dp(48), dp(16), dp(16))
        header.setBackgroundColor(Color.parseColor("#1565C0"))
        val title = TextView(this)
        title.text = "Fuck Market Ads"
        title.textSize = 22f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        header.addView(title)
        val subtitle = TextView(this)
        subtitle.text = "去他妈的小米应用商店广告"
        subtitle.textSize = 14f
        subtitle.setTextColor(Color.parseColor("#BBDEFB"))
        subtitle.setPadding(0, dp(4), 0, 0)
        header.addView(subtitle)
        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(16), dp(16), dp(16))

        val info = TextView(this)
        info.text = "开关状态立即生效，无需重启应用商店"
        info.textSize = 13f
        info.setTextColor(Color.parseColor("#666666"))
        content.addView(info)
        content.addView(spacer())

        // 基础功能
        sectionTitle(content, "基础功能")
        content.addView(switchRow("隐藏应用安全检查", "隐藏应用安装时的安全检查弹窗", HookSettings.KEY_HIDE_SECURITY))
        content.addView(divider())
        content.addView(switchRow("部分细节处理", "非正版APP重新显示到更新列表", HookSettings.KEY_MISC_APPLY))
        content.addView(divider())
        content.addView(switchRow("显示被隐藏的更新", "解锁商店隐藏的Tab和更新", HookSettings.KEY_TAB_FILTER))
        content.addView(divider())
        content.addView(switchRow("禁用OTA验证", "禁用系统更新验证", HookSettings.KEY_BYPASS_OTA))
        content.addView(spacer())

        // 广告拦截
        sectionTitle(content, "广告拦截（逐项控制）")
        content.addView(switchRow("开屏广告", "拦截应用商店开屏广告", HookSettings.KEY_ADS_SPLASH))
        content.addView(divider())
        content.addView(switchRow("搜索页推荐广告", "搜索页底部推荐和搜索建议广告", HookSettings.KEY_ADS_SEARCH))
        content.addView(divider())
        content.addView(switchRow("搜索结果广告", "搜索结果列表中的推荐广告", HookSettings.KEY_ADS_SEARCH_RESULT))
        content.addView(divider())
        content.addView(switchRow("更新页推荐广告", "应用更新页面的软件推荐", HookSettings.KEY_ADS_UPDATE))
        content.addView(divider())
        content.addView(switchRow("详情页广告", "应用详情页的评论推荐和广告", HookSettings.KEY_ADS_DETAIL))
        content.addView(divider())
        content.addView(switchRow("视频列表", "隐藏详情页横向视频推荐列表", HookSettings.KEY_ADS_VIDEO))
        content.addView(divider())
        content.addView(switchRow("热词", "隐藏搜索热词推荐", HookSettings.KEY_ADS_HOTWORD))

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun sectionTitle(parent: LinearLayout, text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.setTextColor(Color.parseColor("#1565C0"))
        t.typeface = Typeface.DEFAULT_BOLD
        t.setPadding(dp(4), 0, dp(4), dp(8))
        parent.addView(t)
    }

    private fun switchRow(title: String, desc: String, key: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(12), dp(16), dp(12))
        row.setBackgroundColor(Color.WHITE)
        val textCol = LinearLayout(this)
        textCol.orientation = LinearLayout.VERTICAL
        textCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTextColor(Color.parseColor("#212121"))
        textCol.addView(t)
        val d = TextView(this)
        d.text = desc
        d.textSize = 12f
        d.setTextColor(Color.parseColor("#999999"))
        d.setPadding(0, dp(2), 0, 0)
        textCol.addView(d)
        row.addView(textCol)
        val sw = Switch(this)
        sw.isChecked = HookSettings.isEnabled(this, key)
        sw.setOnCheckedChangeListener { _, isChecked -> HookSettings.setEnabled(this, key, isChecked) }
        row.addView(sw)
        return row
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(Color.parseColor("#F0F0F0"))
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        return v
    }

    private fun spacer(): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16))
        return v
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}