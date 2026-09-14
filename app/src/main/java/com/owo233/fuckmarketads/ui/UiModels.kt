package com.owo233.fuckmarketads.ui

import com.owo233.fuckmarketads.Settings

/**
 * 主页上的一个功能开关。
 *
 * 注意：这里的定义只用于「展示 + 写入远程偏好」，
 * 真正的生效逻辑在 hook 侧（每个 hook 每次调用时实时读取远程偏好）。
 */
data class Feature(
    /** 远程偏好中的键，与 Settings.KEY_* 一致 */
    val key: String,
    /** 标题 */
    val title: String,
    /** 说明文字 */
    val summary: String,
    /** 默认值：读不到偏好（框架未激活）时的兜底展示 */
    val default: Boolean = true,
    /** 该开关下方是否附带「底部标签多选」区块 */
    val withTabSelector: Boolean = false,
)

/** 按使用场景划分的功能分组 */
data class Category(
    val title: String,
    val subtitle: String,
    val features: List<Feature>,
)

/** 首页按场景排列的分组顺序 */
val CATEGORIES: List<Category> = listOf(
    Category(
        "广告移除", "拦截商店各处的广告与软件推荐", listOf(
            Feature(Settings.KEY_SPLASH, "移除开屏广告", "屏蔽应用商店启动时的开屏广告"),
            Feature(Settings.KEY_MAIN_TAB, "禁止前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗"),
            Feature(Settings.KEY_HOME_FEED, "隐藏信息流广告低栏", "隐藏主页底部视频/应用推荐与热词栏"),
            Feature(Settings.KEY_SEARCH, "移除搜索推荐", "搜索建议、搜索页、搜索结果中的软件推荐"),
            Feature(Settings.KEY_UPDATE_DL, "移除升级/下载推荐", "应用升级页与下载页的软件推荐"),
            Feature(Settings.KEY_DETAIL, "移除详情页广告", "应用详情页的广告、评论与推荐位"),
            Feature(Settings.KEY_RANK, "移除榜单广告", "榜单界面的广告 / 推广卡片"),
        )
    ),
    Category(
        "界面净化", "清理页面中不需要显示的元素", listOf(
            Feature(Settings.KEY_SECURITY, "隐藏应用安全检测", "隐藏「我的」页中的应用安全检测视图"),
            Feature(Settings.KEY_FRUIT, "屏蔽领水果入口", "隐藏福利活动 gif 动图入口（entrance_gif）"),
            Feature(
                Settings.KEY_TAB_FILTER, "筛选底部标签栏 / 推广位",
                "勾选要保留的标签；同时清理首页顶栏云控推广位",
                withTabSelector = true
            ),
        )
    ),
    Category(
        "功能增强", "还原被服务端灰度限制的能力", listOf(
            Feature(Settings.KEY_ISLAND, "启用下载超级岛", "强制让下载进度进入小米超级岛（无视灰度）"),
        )
    ),
    Category(
        "细节修正", "清理之外的体验微调", listOf(
            Feature(Settings.KEY_MISC, "细节修正", "显示非正版 APP、被隐藏更新等细节处理"),
        )
    ),
)

/** 平铺后的全部开关，便于统一读写 */
val ALL_FEATURES: List<Feature> = CATEGORIES.flatMap { it.features }
