package com.mars.mimarketpurify

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 「关于」页：展示软件介绍、功能概要、致谢、技术说明与免责声明。
 *
 * 与主主页保持一致的实现约束：
 *  - 纯原生 View 手写布局，不引入任何 UI 框架（避免增大安装包体积）；
 *  - 同样处理 edge-to-edge 的系统栏内边距；
 *  - 不参与 Xposed 无关逻辑，仅为静态信息展示。
 */
class AboutActivity : Activity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFFF2F2F7.toInt())
            clipToPadding = false
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(container)
        setContentView(scroll)
        applySystemBarInsets(scroll)

        buildTopBar()
        buildAppCard()

        addSection("软件介绍")
        addBody(
            "本项目是对原版应用商店去广告模块的深度增强重构版本。在保留 Hook 应用商店渲染逻辑" +
                "实现净化的核心思路之上，补齐可视化控制面板、新增大量净化规则，并彻底修复原版长期" +
                "存在的「开关需要重启」「子开关不生效」的缺陷。\n\n" +
                "模块自身无联网、无后台上报，仅在目标应用商店进程内生效，旨在提供干净、无广告、" +
                "无营销活动且可高度自定义的纯净应用商店体验。"
        )

        addSection("主要功能")
        addBody(
            listOf(
                "广告屏蔽：开屏广告、前台广告与推荐、信息流广告、搜索推荐、" +
                    "升级 / 下载页推荐、详情页广告、榜单广告、首页顶栏云控推广位",
                "界面精简：应用安全检测视图、领水果活动入口、底部标签栏筛选",
                "功能增强：强制启用下载超级岛（无视服务端灰度）",
                "稳定性增强：防止崩溃自毁、打破回滚死锁、配置备份与回滚恢复、全局容错",
                "模块自身：隐藏桌面图标（隐藏后仍可从框架模块列表进入主页）"
            ).joinToString("\n\n") { "· $it" }
        )

        addSection("开关何时生效")
        addBody(
            "每个功能开关都在对应 hook 的每次调用时实时读取远程偏好，因此一般无需手动重启" +
                "目标应用；若个别 ROM / 框架版本对远程偏好做了快照缓存，重启一次应用商店即可确保生效。"
        )

        addSection("致谢")
        addBody(
            listOf(
                "callng/NewFuckMarketAds —— 提供原始代码",
                "lisrain/NewFuckMarketAds_Fork —— 稳定性增强与超级岛",
                "本仓库 miuix 分支曾用 Jetpack Compose + MiuiX 重写主页，" +
                    "因体积原因未并入主线，仅作参考"
            ).joinToString("\n\n") { "· $it" }
        )

        addSection("技术说明")
        addBody(
            listOf(
                "Hook 框架：libxposed 101.0.0 + ezXHelper",
                "主页 UI：原生 View 手写布局，零 UI 框架依赖",
                "构建环境：AGP 9.1.0 + JDK 21，compileSdk 36 / minSdk 29",
                "配置同步：libxposed 远程偏好（Remote Preferences），固定 group 为 settings",
                "广告识别：命中组件关键字（VideoList / Apps / ad / banner / recommend 等）后隐藏对应容器"
            ).joinToString("\n\n") { "· $it" }
        )

        addSection("免责声明")
        addBody(
            "本项目仅为技术研究成果，请勿用于商业或违反平台规则的场景。使用本模块产生的一切风险" +
                "（如应用商店功能异常、设备故障等）均由使用者自行承担。"
        )
    }

    /** 顶部返回入口：布局层面避免依赖系统 ActionBar */
    private fun buildTopBar() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = "‹ 返回"
            textSize = 14f
            setTextColor(0xFF007AFF.toInt())
            setPadding(0, dp(4), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            val ta = this@AboutActivity.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            )
            setBackgroundResource(ta.getResourceId(0, 0))
            ta.recycle()
            setOnClickListener { finish() }
        })
        row.addView(TextView(this).apply {
            text = "关于"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1C1C1E.toInt())
            setPadding(0, 0, 0, dp(12))
        })
        container.addView(row)
    }

    /** 应用名 / 版本 / 包名信息卡片 */
    private fun buildAppCard() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = dp(6) }

        card.addView(TextView(this).apply {
            text = "Mi Market Purify"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1C1C1E.toInt())
        })
        card.addView(TextView(this).apply {
            text = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"
            textSize = 12f
            setTextColor(0xFF8E8E93.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = packageName
            textSize = 11f
            setTextColor(0xFFA0A0A5.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        container.addView(card)
    }

    private fun addSection(title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF48484A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = dp(14)
                it.bottomMargin = dp(6)
                it.marginStart = dp(4)
            }
        })
    }

    private fun addBody(text: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        card.addView(TextView(this).apply {
            this.text = text
            textSize = 12.5f
            setTextColor(0xFF48484A.toInt())
            setLineSpacing(0f, 1.45f)
        })
        container.addView(card)
    }

    /** 与主页一致：把系统栏 / 刘海高度回填到滚动容器 */
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
