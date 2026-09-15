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
 * 「关于」页：软件介绍、版本与包名、主要功能、致谢、技术说明、免责声明。
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
                "主页 UI：原生 View 手写布局，零 UI 框架依赖，视觉参考 HyperOS / MiuiX",
                "构建环境：AGP 9.1.0 + JDK 21，compileSdk 36 / minSdk 29",
                "配置同步：libxposed 远程偏好（Remote Preferences），固定 group 为 settings",
                "广告识别：命中组件关键字（VideoList / Apps / ad / banner / recommend 等）后隐藏对应容器"
            ).joinToString("\n\n") { "· $it" }
        )

        addSection("反馈遗漏的广告")
        addCardBody(
            "榜单各分类（含游戏榜）的列表项是服务端下发的，不同商店版本差异很大。" +
                "若某个位置仍有广告，可用 adb 抓取 logcat 中 tag 为 MiMarketPurify 的 " +
                "“[rank]” 一行（会打印未被识别的组件类型），连同商店版本号一起反馈，" +
                "即可精确收敛拦截条件。"
        )

        addSection("免责声明")
        addCardBody(
            "本项目仅为技术研究成果，请勿用于商业或违反平台规则的场景。使用本模块产生的一切风险" +
                "（如应用商店功能异常、设备故障等）均由使用者自行承担。"
        )
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
