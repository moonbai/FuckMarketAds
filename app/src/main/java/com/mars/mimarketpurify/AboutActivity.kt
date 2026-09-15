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
import android.util.Pair

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
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            header.setPadding(
                dp(Ui.PAGE_H),
                dp(Ui.PAGE_H) + bars.top,
                dp(Ui.PAGE_H),
                dp(12)
            )
            content.setPadding(
                dp(Ui.PAGE_H),
                dp(8),
                dp(Ui.PAGE_H),
                dp(Ui.PAGE_H) + bars.bottom
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

        // 底部文字
        content.addView(TextView(this).apply {
            text = "不乱拉屎的应用商店才是好的应用商店@Mars"
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setLineSpacing(0f, 1.5f)
            setPadding(dp(8), dp(16), dp(8), dp(8))
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
            layoutParams = LinearLayout.LayoutParams(
                dp(Ui.TOUCH_MIN),
                dp(Ui.TOUCH_MIN)
            ).also { it.marginStart = -dp(8) }
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

    private fun buildAppCard() {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val appIcon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).also {
                it.marginStart = dp(12)
                it.marginEnd = dp(8)
            }
        }

        info.addView(cardTitle("Mi Market Purify"))
        info.addView(TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            textSize = Ui.ROW_SUMMARY
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, dp(3), 0, 0)
        })
        info.addView(TextView(this).apply {
            text = "移除小米应用商店广告与推荐信息"
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(0, dp(3), 0, 0)
        })

        val arrowTv = TextView(this).apply {
            text = "›"
            textSize = 20f
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
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(cardTitle(title))
                addView(TextView(this@AboutActivity).apply {
                    text = desc
                    textSize = Ui.ROW_SUMMARY
                    setTextColor(Ui.TEXT_SECONDARY)
                    setLineSpacing(0f, 1.4f)
                    setPadding(0, dp(4), 0, 0)
                })
            })

            if (index > 0) {
                card.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = dp(8)
                }
            }

            container.addView(card)
        }

        content.addView(container)
    }

    private fun buildReferenceProjects() {
        val references = listOf(
            "NewFuckMarketAds" to Pair("callng", "https://github.com/callng/NewFuckMarketAds"),
            "NewFuckMarketAds_Fork" to Pair("lisrain", "https://github.com/lisrain/NewFuckMarketAds_Fork")
        )

        val card = card()
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        references.forEachIndexed { index, (name, pair) ->
            val (author, url) = pair
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.bg_card_ripple)
                setOnClickListener {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure {
                        Toast.makeText(
                            this@AboutActivity,
                            "无法打开链接：${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val textLayout = LinearLayout(this@AboutActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                textLayout.addView(TextView(this@AboutActivity).apply {
                    text = name
                    textSize = Ui.ROW_SUMMARY
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Ui.TEXT_PRIMARY)
                })
                textLayout.addView(TextView(this@AboutActivity).apply {
                    text = author
                    textSize = Ui.MICRO
                    setTextColor(Ui.TEXT_SECONDARY)
                    setPadding(0, dp(3), 0, 0)
                })

                val arrow = TextView(this@AboutActivity).apply {
                    text = "›"
                    textSize = 20f
                    setTextColor(Ui.TEXT_TERTIARY)
                }

                addView(textLayout)
                addView(arrow)
            }

            listLayout.addView(itemRow)

            if (index < references.lastIndex) {
                listLayout.addView(View(this).apply {
                    setBackgroundColor(Ui.DIVIDER)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(0.5f).coerceAtLeast(1)
                    ).also { it.marginStart = dp(14) }
                })
            }
        }

        card.addView(listLayout)
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
            it.topMargin = dp(10)
            it.bottomMargin = dp(8)
        }
        content.addView(titleView)
    }
}
