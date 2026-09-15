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
 * 「关于」页
 * 卡片1：应用信息卡片（版本、包名、简介，整卡点击跳转源码仓库）
 * 卡片2：功能清单
 * 卡片3：反馈遗漏广告说明
 * 页脚：上游致谢 + 免责声明
 * 顶栏：48dp圆形返回图标按钮，和主页视觉统一
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
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // ❌ 删除 spaceBetween，原生LinearLayout不支持
        }
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

        // 系统栏Insets适配
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            header.setPadding(dp(Ui.PAGE_H), dp(Ui.PAGE_H) + bars.top, dp(Ui.PAGE_H), dp(12))
            content.setPadding(
                dp(Ui.PAGE_H), dp(8), dp(Ui.PAGE_H), dp(Ui.PAGE_H) + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)

        buildTopBar(header)
        buildAppCard()

        addSection("功能")
        addCardBody(
            listOf(
                "广告移除：开屏、前台推荐、信息流、搜索、升级 / 下载页、详情页、榜单、顶栏推广位",
                "界面精简：安全检测、领水果、我的页、详情页精选、底部标签栏",
                "功能增强：下载超级岛（无视服务端灰度）",
                "稳定性：崩溃自毁拦截、配置备份恢复、全局容错",
                "模块自身：隐藏桌面图标（仍可从框架进入主页）"
            ).joinToString("\n") { "· $it" }
        )

        addSection("反馈遗漏的广告")
        addCardBody(
            "榜单各分类（含游戏榜）的列表项由服务端下发，版本差异大。\n" +
            "若仍有漏网广告：开启主页「榜单调试提示」，重启商店后抓取 logcat 中 MiMarketPurify 的 `[rank-tree]` 视图树，连同商店版本号反馈即可。"
        )

        // 页脚致谢免责
        content.addView(TextView(this).apply {
            text = "上游：callng/NewFuckMarketAds、lisrain/NewFuckMarketAds_Fork\n" +
                    "仅供技术研究，使用风险由使用者自行承担。"
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setLineSpacing(0f, 1.5f)
            setPadding(dp(8), dp(20), dp(8), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        })
    }

    /** 顶栏：圆形返回图标 + 标题 */
    private fun buildTopBar(header: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundResource(R.drawable.bg_icon_ripple)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "返回"
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(Ui.TOUCH_MIN), dp(Ui.TOUCH_MIN)).also {
                it.marginStart = -dp(8)
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
            ).also { it.marginStart = dp(4) }
        })
        header.addView(row)

        // 分割线
        header.addView(View(this).apply {
            setBackgroundColor(Ui.DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            )
        })
    }

    /**
     * 应用信息卡片
     * 布局：左侧预留图标位 + 右侧文本信息 + 末尾跳转箭头
     * 整张卡片点击打开源码仓库
     */
    private fun buildAppCard() {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // 图标占位（后续可以放模块logo）
        val iconPlaceHolder = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = dp(12)
                it.marginEnd = dp(8)
            }
        }

        info.addView(cardTitle("Mi Market Purify"))
        info.addView(TextView(this).apply {
            text = "版本 ${BuildConfig.VERSION_NAME}）"
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, dp(4), 0, 0)
        })
        info.addView(TextView(this).apply {
            text = packageName
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(0, dp(4), 0, 0)
        })
        info.addView(TextView(this).apply {
            text = "移除小米应用商店各处广告与推荐，并提供可勾选的界面精简选项。无联网、无上报，仅在商店进程内生效。"
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECONDARY)
            setLineSpacing(0f, 1.4f)
            setPadding(0, dp(8), 0, 0)
        })

        // 跳转箭头
        val arrowTv = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(Ui.TEXT_TERTIARY)
        }

        row.addView(iconPlaceHolder)
        row.addView(info)
        row.addView(arrowTv)

        card.addView(row)
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
        val titleView = sectionTitle(title)
        // 给section标题增加上边距，模拟原来spaceBetween的间距
        titleView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.topMargin = dp(16)
        }
        content.addView(titleView)
    }

    private fun addCardBody(text: String) {
        val card = card()
        card.addView(cardText(text).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
        })
        // 卡片增加上边距，实现区块间距
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.topMargin = dp(12)
        }
        content.addView(card)
    }
}
