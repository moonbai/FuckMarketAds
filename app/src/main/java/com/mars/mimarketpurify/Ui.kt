package com.mars.mimarketpurify

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat

/**
 * 统一的视觉与交互令牌（design tokens），整体向 HyperOS / MiuiX 的观感靠拢：
 * 分组卡片 + 行式布局 + 蓝色强调色 + 清晰的分隔线。
 *
 * 主页与关于页**共用**这一套色值 / 字号 / 间距 / 触摸目标尺寸，避免出现
 * “同一个产品里两种卡片圆角、两种正文大小、两套开关颜色”的情况。
 * 新增界面时应优先复用这里的常量与辅助函数，而不是就地写魔法数字。
 */
object Ui {

    // ===== 颜色：页面底色与文本层级 =====
    /** 页面底色：HyperOS 浅色主题的浅灰 */
    val BG = 0xFFF5F5F5.toInt()
    /** 卡片 / 分组容器底色 */
    val CARD = 0xFFFFFFFF.toInt()
    val TEXT_PRIMARY = 0xFF1C1C1E.toInt()
    val TEXT_SECTION = 0xFF48484A.toInt()
    val TEXT_SECONDARY = 0xFF8E8E93.toInt()
    val TEXT_TERTIARY = 0xFFA0A0A5.toInt()
    /** 分组内的行分隔线：比 iOS 灰略深，保证白底上看得清 */
    val DIVIDER = 0xFFD1D1D6.toInt()

    // ===== 颜色：交互与状态 =====
    /** 强调色：HyperOS 蓝，用于开关开启态、链接与可点元素 */
    val ACCENT = 0xFF0A84FF.toInt()
    /** 强调色的低透明度底，用于胶囊按钮 / 状态卡背景 */
    val ACCENT_SOFT = 0x140A84FF.toInt()
    /** 已激活 / 成功 */
    val STATE_ACTIVE = 0xFF34C759.toInt()
    val STATE_ACTIVE_SOFT = 0x1434C759.toInt()
    /** 未激活 / 未生效（同时是品牌色） */
    val STATE_INACTIVE = 0xFFFF6B35.toInt()
    val STATE_INACTIVE_SOFT = 0x14FF6B35.toInt()

    // ===== 开关 / 勾选框 =====
    /**
     * 开关轨道未选中色：明确的灰，避免白滑块与浅灰轨道糊在一起。
     * 轨道 / 滑块的尺寸与形状见 `switch_track.xml` / `switch_thumb.xml`
     * （28dp 轨道包住 24dp 滑块，系统默认的滑块会戳出轨道外）。
     */
    val SWITCH_TRACK_OFF = 0xFFD1D1D6.toInt()
    val CHECK_OFF = 0xFFC7C7CC.toInt()

    // ===== 字号层级（sp）=====
    const val HOME_TITLE = 26f
    const val PAGE_TITLE = 20f
    const val SECTION = 13f
    const val ROW_TITLE = 16f
    const val ROW_SUMMARY = 12.5f
    const val CAPTION = 13f
    const val MICRO = 11.5f

    // ===== 间距与尺寸（dp）=====
    const val PAGE_H = 16
    const val CARD_PAD_H = 16
    const val CARD_PAD_V = 14
    const val CARD_GAP = 12
    const val SECTION_TOP = 18
    /** 组内行高：遵循 HyperOS 行高，通透但不松散 */
    const val ROW_MIN_HEIGHT = 52
    const val ROW_PAD_H = 16
    const val ROW_PAD_V = 12
    /** 组内行间距：不画分隔线，改用少量留白区分相邻两行 */
    const val ROW_GAP = 4
    /** 最小触摸目标，遵循 Material 48dp 建议 */
    const val TOUCH_MIN = 48

    const val LINE_SPACING = 1.45f

    const val REPO_URL = "https://github.com/moonbai/MiMarketPurify"
}

/** dp -> px */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** dp -> px（浮点）：给 cornerRadius 这类需要 float 的场合用 */
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

/**
 * 分组容器：白底圆角，内部按“行 + 分隔线”堆叠。
 * 这是 HyperOS 设置里最常见的形态——一个分组一整块容器，而不是每行一张卡片。
 */
fun Context.groupCard(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundResource(R.drawable.bg_card)
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).also { it.bottomMargin = dp(Ui.CARD_GAP) }
}

/** 普通卡片（带内边距），用于正文段落等整块内容 */
fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundResource(R.drawable.bg_card)
    setPadding(dp(Ui.CARD_PAD_H), dp(Ui.CARD_PAD_V), dp(Ui.CARD_PAD_H), dp(Ui.CARD_PAD_V))
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).also { it.bottomMargin = dp(Ui.CARD_GAP) }
}

/** 组内的行：横向排列，统一内边距与最小高度 */
fun Context.row(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = android.view.Gravity.CENTER_VERTICAL
    setPadding(dp(Ui.ROW_PAD_H), dp(Ui.ROW_PAD_V), dp(Ui.ROW_PAD_H), dp(Ui.ROW_PAD_V))
    minimumHeight = dp(Ui.ROW_MIN_HEIGHT)
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}

/** 行标题 */
fun Context.rowTitle(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = Ui.ROW_TITLE
    setTextColor(Ui.TEXT_PRIMARY)
}

/** 行摘要 */
fun Context.rowSummary(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = Ui.ROW_SUMMARY
    setTextColor(Ui.TEXT_SECONDARY)
    setLineSpacing(0f, 1.35f)
    setPadding(0, dp(3), 0, 0)
}

/** 分组 / 区块标题 */
fun Context.sectionTitle(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = Ui.SECTION
    setTypeface(null, Typeface.BOLD)
    setTextColor(Ui.TEXT_SECTION)
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).also {
        it.topMargin = dp(Ui.SECTION_TOP)
        it.bottomMargin = dp(6)
        it.marginStart = dp(4)
    }
}

/** 卡片正文：统一行距，避免各处临时 setLineSpacing 造成疏密不一 */
fun Context.cardText(
    text: String,
    size: Float = 13.5f,
    color: Int = Ui.TEXT_SECTION,
    lineSpacing: Float = Ui.LINE_SPACING
): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(color)
    setLineSpacing(0f, lineSpacing)
}

/** 卡片标题 */
fun Context.cardTitle(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = Ui.ROW_TITLE
    setTypeface(null, Typeface.BOLD)
    setTextColor(Ui.TEXT_PRIMARY)
}

/** 给 drawable 着色：选中态与未选中态两种颜色 */
fun Drawable.tinted(on: Int, off: Int): Drawable {
    val states = arrayOf(
        intArrayOf(android.R.attr.state_checked),
        intArrayOf(-android.R.attr.state_checked)
    )
    return DrawableCompat.wrap(this).mutate().also {
        DrawableCompat.setTintList(it, ColorStateList(states, intArrayOf(on, off)))
    }
}

/**
 * 可点击元素的统一反馈：ripple + 可获焦，并把高度补到至少 48dp
 * 以满足推荐触摸目标尺寸。
 *
 * [rippleRes] 建议显式传入圆角 mask 的 drawable（`bg_row_ripple` / `bg_card_ripple`）：
 * 系统自带的 `?selectableItemBackground` 是**矩形** ripple，叠在圆角卡片上时
 * 按压高亮会从圆角处溢出成一个方角。
 *
 * 注意：不能直接用 [View.setBackgroundResource] 覆盖背景——那样会把容器已有的
 * 圆角背景连同 padding 一起冲掉。这里用 [LayerDrawable] 把 ripple 叠在
 * 原有背景之上，并在换背景后还原 padding。
 */
fun View.tappable(context: Context, rippleRes: Int = 0) {
    val resId = if (rippleRes != 0) {
        rippleRes
    } else {
        val ta = context.obtainStyledAttributes(
            intArrayOf(android.R.attr.selectableItemBackground)
        )
        val r = ta.getResourceId(0, 0)
        ta.recycle()
        r
    }

    if (resId != 0) {
        val ripple = context.getDrawable(resId)
        if (ripple != null) {
            val pl = paddingLeft
            val pt = paddingTop
            val pr = paddingRight
            val pb = paddingBottom
            val prev = background
            background = if (prev != null) LayerDrawable(arrayOf(prev, ripple)) else ripple
            setPadding(pl, pt, pr, pb)
        }
    }

    isClickable = true
    isFocusable = true
    // View 上没有 minHeight 这个可写属性（不存在 setMinHeight(int)），只有 minimumHeight
    minimumHeight = context.dp(Ui.TOUCH_MIN)
}
