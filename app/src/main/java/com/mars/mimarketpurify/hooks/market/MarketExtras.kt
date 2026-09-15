package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 从 XiaomiHelper（HowieHChen/XiaomiHelper）的应用商店规则里补移植过来的几项。
 *
 * 分成五个独立对象而不是一个大对象，是因为它们各自挂在**不同的开关**上：
 * [BaseHook.hooked] 每次调用都会重新读取 [BaseHook.prefKey]，
 * 这样用户在主页切换其中任何一项都不用重启应用商店。
 * 合成一个对象的话 prefKey 只能有一个，就没法分别控制了。
 *
 * 所有 hook 都包在 runCatching 里：商店换个版本类名就没了，
 * 缺一个类不该影响其它功能（这条原则与项目里其它 hook 一致）。
 */

/** 底部 Tab 的数字角标与「新」字红点 */
object TabBadge : BaseHook() {

    override val prefKey: String = Settings.KEY_MAIN_TAB

    override val name: String
        get() = "隐藏底栏角标"

    override fun init() {
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.widget.TabView")
                .methodFinder()
                .filter { name in setOf("setNumber", "showNewMessageTag") }
                .forEach { it.hooked { null } }
        }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 TabView，跳过", null) }
    }
}

/** 首页搜索框左侧的动态活动入口（云控下发的小图标 / 动图） */
object EntranceAds : BaseHook() {

    override val prefKey: String = Settings.KEY_MAIN_TAB

    override val name: String
        get() = "屏蔽首页活动入口"

    override fun init() {
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.util.EntranceManager")
                .methodFinder()
                .filterByName("getEntranceConfig")
                .forEach { it.hooked { null } }
        }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 EntranceManager，跳过", null) }
    }
}

/** 「我的」页底部推广应用列表（数据层直接返回 0 组） */
object MineAdGroup : BaseHook() {

    override val prefKey: String = Settings.KEY_MINE_RECOMMEND

    override val name: String
        get() = "屏蔽「我的」页推广组"

    override fun init() {
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.common.analytics.onetrack.ExperimentManager\$Companion"
            ).methodFinder()
                .filterByName("getMineAdRecommendGroup")
                .forEach { it.hooked { 0 } }
        }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 ExperimentManager，跳过", null) }
    }
}

/**
 * 详情页与「浏览器下载」弹窗里剩下的广告位：
 * 拼装推荐、多按钮底栏（多出来的那个通常是推广）、下载弹窗内的广告列表。
 */
object DetailExtras : BaseHook() {

    override val prefKey: String = Settings.KEY_DETAIL

    override val name: String
        get() = "详情页附加净化"

    override fun init() {
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.common.network.retrofit.response.bean.AppDetailV3"
            ).apply {
                methodFinder()
                    .filter { name in setOf("showAssemble", "getShowAssemble") }
                    .forEach { it.hooked { false } }

                // 底部多按钮那一栏多出来的按钮通常是推广位，压回单按钮
                methodFinder()
                    .filterByName("getLayoutType")
                    .forEach { m ->
                        m.hooked {
                            val original = proceed()
                            if (original == "bottomMultiButton") "bottomSingleButton" else original
                        }
                    }
            }
        }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 AppDetailV3，跳过", null) }

        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.directmail.BottomMiniSourceFileFragment"
            ).apply {
                methodFinder()
                    .filterByName("needShowAdList")
                    .forEach { it.hooked { false } }
                methodFinder()
                    .filterByName("ensureRiskLayout")
                    .forEach { it.hooked { null } }
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 BottomMiniSourceFileFragment，跳过", null)
        }
    }
}

/** 阻止商店弹出的「升级提醒」对话框 */
object UpdateDialogBlock : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_DL

    override val name: String
        get() = "阻止升级提醒弹窗"

    override fun init() {
        listOf(
            "com.xiaomi.market.ui.UpdateListFragment",
            "com.xiaomi.market.ui.update.UpdatePushDialogManager"
        ).forEach { owner ->
            runCatching {
                ClassUtil.loadClass(owner)
                    .methodFinder()
                    .filterByName("tryShowDialog")
                    .forEach { it.hooked { null } }
            }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $owner，跳过", null) }
        }
    }
}
