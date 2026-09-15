package com.mars.mimarketpurify

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 「关于」页：只保留关键信息 —— 应用卡片（版本 / 包名 / 源码入口）、
 * 一句话简介、主要功能清单、遗漏广告的反馈方式，页脚放致谢与免责。
 *
 * 与主页保持同一套结构（固定顶栏 + 内容区滚动）与同一套 [Ui] 令牌。
 * 顶部返回键不再是纤细的文本符号「‹ 返回」，而是 48dp 圆形图标按钮
 * （矢量箭头 + 圆形 ripple），与标题垂直居中，视觉重心和主页顶栏一致。
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

        addSection("简介")
        addCardBody(
            "小米应用商店净化模块。通过 Hook 商店的渲染逻辑移除广告与推荐，并提供可勾选的界面精简选项。" +
                "无联网、无上报，仅在应用商店进程内生效。"
        )

        addSection("主要功能")
        addCardBody(
            listOf(
                "广告移除：开屏、前台推荐、信息流、搜索、升级 / 下载页、详情页、榜单、顶栏推广位",
                "界面精简：安全检测、领水果、我的页、详情页精选、底部标签栏",
                "功能增强：下载超级岛（无视服务端灰度）",
                "稳定性：崩溃自毁拦截、回滚死锁解除、配置备份恢复、全局容错",
                "模块自身：隐藏桌面图标（仍可从框架进入主页）"
            ).joinToString("\n") { "· $it" }
        )

        addSection("反馈遗漏的广告")
        addCardBody(
            "榜单各分类（含游戏榜）的列表项由服务端下发，不同商店版本差异很大。若仍有漏网广告，" +
                "打开主页「模块自身 → 榜单调试提示」，重启商店后抓取 logcat 中 " +
                "MiMarketPurify 的 `[rank-tree]` 视图树，连同商店版本号反馈即可。"
        )

        content.addView(TextView(this).apply {
            text = "上游：callng/NewFuckMarketAds、lisrain/NewFuckMarketAds_Fork\n" +
                "开关实时生效，个别 ROM 缓存偏好时重启一次商店即可。\n" +
                "仅供技术研究，使用风险由使用者自行承担。"
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setLineSpacing(0f, 1.5f)
            setPadding(dp(4), dp(4), dp(4), dp(16))
        })
    }

    /** 固定顶栏：圆形图标返回键 + 页标题，二者垂直居中 */
    private fun buildTopBar(header: LinearLayout) {
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
            text = "关于"
            textSize = Ui.PAGE_TITLE
            setTypeface(null, Typeface.BOLD)
            setTextColor(Ui.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = -dp(8) }
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
            textSize = Ui.ROW_SUMMARY
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

        // 与卡片同圆角的 ripple：明确整张卡片是一个点击目标，且高亮不溢出圆角
        card.tappable(this, R.drawable.bg_card_ripple)
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
