package com.mars.mimarketpurify

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 统一的视觉与交互令牌（design tokens）。
 *
 * 主页与关于页**共用**这一套色值 / 字号 / 间距 / 触摸目标尺寸，避免出现
 * “同一个产品里两种卡片圆角、两种正文大小、两套开关颜色”的情况。
 * 新增界面时应优先复用这里的常量与辅助函数，而不是就地写魔法数字。
 */
object Ui {

    // ===== 颜色：页面底色与文本层级 =====
    val BG = 0xFFF2F2F7.toInt()
    val TEXT_PRIMARY = 0xFF1C1C1E.toInt()
    val TEXT_SECTION = 0xFF48484A.toInt()
    val TEXT_SECONDARY = 0xFF8E8E93.toInt()
    val TEXT_TERTIARY = 0xFFA0A0A5.toInt()
    val DIVIDER = 0xFFE5E5EA.toInt()
    val SWITCH_TRACK_OFF = 0xFFE5E5EA.toInt()

    // ===== 颜色：交互与状态 =====
    val ACCENT = 0xFF007AFF.toInt()
    /** 已激活 / 成功 */
    val STATE_ACTIVE = 0xFF34C759.toInt()
    /** 未激活 / 未生效（同时是品牌色） */
    val STATE_INACTIVE = 0xFFFF6B35.toInt()

    // ===== 字号层级（sp）=====
    const val HOME_TITLE = 24f
    const val PAGE_TITLE = 21f
    const val SECTION = 13f
    const val CARD_TITLE = 15f
    const val BODY = 13f
    const val CAPTION = 12f
    const val MICRO = 11f

    // ===== 间距与尺寸（dp）=====
    const val PAGE_H = 16
    const val CARD_PAD_H = 16
    const val CARD_PAD_V = 14
    const val CARD_GAP = 10
    const val SECTION_TOP = 14
    /** 最小触摸目标，遵循 Material 48dp 建议 */
    const val TOUCH_MIN = 48

    /** 未生效状态的视觉弱化透明度 */
    const val DIM_ALPHA = 0.45f
    const val LINE_SPACING = 1.45f

    const val REPO_URL = "https://github.com/moonbai/MiMarketPurify"
}

/** dp -> px */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** 统一的白底圆角卡片 */
fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundResource(R.drawable.bg_card)
    setPadding(dp(Ui.CARD_PAD_H), dp(Ui.CARD_PAD_V), dp(Ui.CARD_PAD_H), dp(Ui.CARD_PAD_V))
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).also { it.bottomMargin = dp(Ui.CARD_GAP) }
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
    size: Float = Ui.BODY,
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
    textSize = Ui.CARD_TITLE
    setTypeface(null, Typeface.BOLD)
    setTextColor(Ui.TEXT_PRIMARY)
}

/**
 * 可点击元素的统一反馈：系统 ripple + 可获焦，并把高度补到至少 48dp
 * 以满足推荐触摸目标尺寸。
 *
 * 注意：不能直接用 [View.setBackgroundResource] 覆盖背景——那样会把卡片已有的
 * 白底圆角背景连同 padding 一起冲掉。这里用 [LayerDrawable] 把 ripple 叠在
 * 原有背景之上，并在换背景后还原 padding，保证「卡片可点击」与「卡片还是卡片」
 * 两件事同时成立。
 */
fun View.tappable(context: Context, borderless: Boolean = true) {
    val attr = if (borderless) {
        android.R.attr.selectableItemBackgroundBorderless
    } else {
        android.R.attr.selectableItemBackground
    }
    val ta = context.obtainStyledAttributes(intArrayOf(attr))
    val resId = ta.getResourceId(0, 0)
    ta.recycle()

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
