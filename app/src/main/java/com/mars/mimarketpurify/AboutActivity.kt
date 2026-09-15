package com.mars.mimarketpurify

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 「关于」页：软件介绍、版本与包名、主要功能、致谢、技术说明、免责声明。
 *
 * 与主主页保持同一套结构（固定顶栏 + 内容区滚动）与同一套 [Ui] 令牌，
 * 让用户在不同页之间来回时不会遇到两套不同的标题高度 / 分隔线 / 反馈方式。
 * 顶部应用卡片整体可点击，直达源码仓库 [Ui.REPO_URL]。
 */
class AboutActivity : Activity() {

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        val header = LinearLayout(this).apply {
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
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
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

        buildTopBar(header)
        buildAppCard()

        addSection("软件介绍")
        addCardBody(
            "本项目是对原版应用商店去广告模块的深度增强重构版本。在保留 Hook 应用商店渲染逻辑" +
                "实现净化的核心思路之上，补齐可视化控制面板、新增大量净化规则，并彻底修复原版长期" +
                "存在的「开关需要重启」「子开关不生效」的缺陷。\n\n" +
                "模块自身无联网、无后台上报，仅在目标应用商店进程内生效，旨在提供干净、无广告、" +
                "无营销活动且可高度自定义的纯净应用商店体验。"
        )

        addSection("主要功能")
        addCardBody(
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
        addCardBody(
            "每个功能开关都在对应 hook 的每次调用时实时读取远程偏好，因此一般无需手动重启" +
                "目标应用；若个别 ROM / 框架版本对远程偏好做了快照缓存，重启一次应用商店即可确保生效。"
        )

        addSection("致谢")
        addCardBody(
            listOf(
                "callng/NewFuckMarketAds —— 提供原始代码",
                "lisrain/NewFuckMarketAds_Fork —— 稳定性增强与超级岛",
                "本仓库 miuix 分支曾用 Jetpack Compose + MiuiX 重写主页，" +
                    "因体积原因未并入主线，仅作参考"
            ).joinToString("\n\n") { "· $it" }
        )

        addSection("技术说明")
        addCardBody(
            listOf(
                "Hook 框架：libxposed 101.0.0 + ezXHelper",
                "主页 UI：原生 View 手写布局，零 UI 框架依赖",
                "构建环境：AGP 9.1.0 + JDK 21，compileSdk 36 / minSdk 29",
                "配置同步：libxposed 远程偏好（Remote Preferences），固定 group 为 settings",
                "广告识别：命中组件关键字（VideoList / Apps / ad / banner / recommend 等）后隐藏对应容器"
            ).joinToString("\n\n") { "· $it" }
        )

        addSection("免责声明")
        addCardBody(
            "本项目仅为技术研究成果，请勿用于商业或违反平台规则的场景。使用本模块产生的一切风险" +
                "（如应用商店功能异常、设备故障等）均由使用者自行承担。"
        )
    }

    /** 固定顶栏：返回 + 页标题，与主页保持同样的内边距与分隔线 */
    private fun buildTopBar(header: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = "‹ 返回"
            textSize = Ui.CAPTION
            setTextColor(Ui.ACCENT)
            setPadding(0, dp(4), dp(14), dp(4))
            tappable(this@AboutActivity)
            setOnClickListener { finish() }
        })
        row.addView(TextView(this).apply {
            text = "关于"
            textSize = Ui.PAGE_TITLE
            setTypeface(null, Typeface.BOLD)
            setTextColor(Ui.TEXT_PRIMARY)
        })
        header.addView(row)

        header.addView(View(this).apply {
            setBackgroundColor(Ui.DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            )
        })
    }

    /** 应用名 / 版本 / 包名卡片：整体可点击并跳转源码仓库 */
    private fun buildAppCard() {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = dp(10) }
        }
        info.addView(cardTitle("Mi Market Purify"))
        info.addView(TextView(this).apply {
            text = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, dp(3), 0, 0)
        })
        info.addView(TextView(this).apply {
            text = packageName
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(0, dp(3), 0, 0)
        })

        row.addView(info)
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(Ui.TEXT_TERTIARY)
        })
        card.addView(row)
        card.addView(TextView(this).apply {
            text = "点击查看源码仓库"
            textSize = Ui.MICRO
            setTextColor(Ui.ACCENT)
            setPadding(0, dp(10), 0, 0)
        })

        // 矩形 ripple：明确整张卡片是一个点击目标
        card.tappable(this, borderless = false)
        card.setOnClickListener { openRepo() }
        content.addView(card)
    }

    private fun openRepo() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Ui.REPO_URL)))
        }.onFailure {
            Toast.makeText(this, "无法打开链接：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSection(title: String) {
        content.addView(sectionTitle(title))
    }

    private fun addCardBody(text: String) {
        val card = card()
        card.addView(cardText(text))
        content.addView(card)
    }
}
