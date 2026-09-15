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
 * 1. 应用信息卡：顶部左侧引用应用图标
 * 2. 功能卡：功能列表改为卡片式展示
 * 3. 移除“反馈遗漏的广告”模块
 * 4. 新增参考项目卡，分栏展示参考项目
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
        buildFeatureCards()

        addSection("参考项目")
        buildReferenceProjects()

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

        header.addView(View(this).apply {
            setBackgroundColor(Ui.DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            )
        })
    }

    /**
     * 应用信息卡：左侧引用应用图标，右侧为应用信息
     */
    private fun buildAppCard() {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val appIcon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher) // 引用应用图标
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
            text = "版本 ${BuildConfig.VERSION_NAME}"
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
            text = "移除小米应用商店广告、推荐及部分干扰信息"
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECONDARY)
            setLineSpacing(0f, 1.4f)
            setPadding(0, dp(8), 0, 0)
        })

        val arrowTv = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(Ui.TEXT_TERTIARY)
        }

        row.addView(appIcon)
        row.addView(info)
        row.addView(arrowTv)

        card.addView(row)
        card.tappable(this, R.drawable.bg_card_ripple)
        card.setOnClickListener { openRepo() }
        content.addView(card)
    }

    /**
     * 功能区改为卡片式展示
     * 每一项功能独立成一张小卡
     */
    private fun buildFeatureCards() {
        val features = listOf(
            "广告移除" to "开屏、前台推荐、信息流、搜索、升级/下载页、详情页、榜单、顶栏推广位",
            "界面精简" to "安全检测、领水果、我的页、详情页精选、底部标签栏",
            "功能增强" to "下载超级岛（无视服务端灰度）",
            "稳定性" to "崩溃自毁拦截、配置备份恢复、全局容错",
            "模块自身" to "隐藏桌面图标（仍可从框架进入主页）"
        )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        features.forEachIndexed { index, (title, desc) ->
            val card = card()
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))

                addView(cardTitle(title))
                addView(TextView(this@AboutActivity).apply {
                    text = desc
                    textSize = Ui.ROW_SUMMARY
                    setTextColor(Ui.TEXT_SECONDARY)
                    setLineSpacing(0f, 1.4f)
                    setPadding(0, dp(6), 0, 0)
                })
            })

            if (index > 0) {
                card.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = dp(10)
                }
            }

            container.addView(card)
        }

        content.addView(container)
    }

    /**
     * 参考项目卡片，分栏展示
     * 这里用两列 Grid 思路；原生 View 下用 LinearLayout 嵌套实现
     */
    private fun buildReferenceProjects() {
        val references = listOf(
            "NewFuckMarketAds" to "callng",
            "NewFuckMarketAds_Fork" to "lisrain",
            "MiMarketPurify" to "参考项目"
        )

        val card = card()
        val columnCount = 2

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        for (i in references.indices step columnCount) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            for (j in 0 until columnCount) {
                val position = i + j
                if (position < references.size) {
                    val (name, desc) = references[position]
                    row.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                            if (j > 0) it.marginStart = dp(10)
                        }
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        setBackgroundColor(Ui.BG)

                        addView(TextView(this@AboutActivity).apply {
                            text = name
                            textSize = Ui.ROW_SUMMARY
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Ui.TEXT_PRIMARY)
                        })
                        addView(TextView(this@AboutActivity).apply {
                            text = desc
                            textSize = Ui.MICRO
                            setTextColor(Ui.TEXT_SECONDARY)
                            setLineSpacing(0f, 1.4f)
                            setPadding(0, dp(4), 0, 0)
                        })
                    })
                } else {
                    // ✅修复：必须写 row.addView()，不能直接addView
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                }
            }

            if (i > 0) {
                row.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = dp(10)
                }
            }

            outer.addView(row)
        }

        card.addView(outer)
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
        titleView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.topMargin = dp(16)
        }
        content.addView(titleView)
    }
}
