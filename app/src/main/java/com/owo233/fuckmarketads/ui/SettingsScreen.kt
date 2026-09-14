package com.owo233.fuckmarketads.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.owo233.fuckmarketads.BuildConfig
import com.owo233.fuckmarketads.MainActivity
import com.owo233.fuckmarketads.Settings
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 模块主页（Jetpack Compose + MiuiX 实现）。
 *
 * 结构：
 *  - [Scaffold] 负责框架：[TopAppBar] 顶栏 + 自动处理系统栏 / 刘海的内边距（content 的 PaddingValues），
 *    避免 targetSdk 36 下顶部内容被遮挡；
 *  - 内容区为 [LazyColumn]，按 [CATEGORIES] 的顺序渲染「小标题 + 卡片」结构，
 *    卡片内的开关使用 MiuiX 的 [SwitchPreference] / [CheckboxPreference]；
 *  - 「隐藏桌面图标」单独放在「模块自身」分组：它不写远程偏好，
 *    而是直接操作 activity-alias 的启用状态（见 [MainActivity.setHideIcon]）。
 */
@Composable
fun SettingsRoot(activity: MainActivity) {
    FmaAppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = "Fuck Market Ads",
                    subtitle = "小米应用商店去广告",
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(LayoutDirection.Ltr),
                    end = padding.calculateEndPadding(LayoutDirection.Ltr),
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 12.dp,
                )
            ) {
                item { StatusCard(activity.serviceState.value) }

                CATEGORIES.forEach { category ->
                    item { GroupHeader(category.title, category.subtitle) }
                    item {
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            category.features.forEach { feature ->
                                FeatureRow(activity, feature)
                            }
                        }
                    }
                    if (category.features.any { it.withTabSelector }) {
                        item { GroupHeader("保留的底部标签", "取消勾选即隐藏该标签（全部取消 = 保留全部）") }
                        item { TabKeepCard(activity) }
                    }
                }

                item { GroupHeader("模块自身", "仅影响本模块的显示方式") }
                item { ModuleCard(activity) }
            }
        }
    }
}

/** 深浅色自适应的 MiuiX 主题 */
@Composable
fun FmaAppTheme(content: @Composable () -> Unit) {
    MiuixTheme(
        colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

/** 框架激活状态卡片 */
@Composable
private fun StatusCard(service: XposedService?) {
    val colors = MiuixTheme.colorScheme
    val active = service != null
    val (headline, detailLine) = if (active) describeService(service!!) else {
        "模块未激活" to "请在 LSPosed / 框架中启用本模块并勾选作用域"
    }
    val headlineColor = if (active) colors.onTertiaryContainer else colors.error

    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            color = if (active) colors.tertiaryContainer else colors.errorContainer
        )
    ) {
        Text(text = headline, style = MiuixTheme.textStyles.headline1, color = headlineColor)
        Text(
            text = detailLine,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceVariantSummary
        )
        Text(
            text = "模块版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
            style = MiuixTheme.textStyles.footnote2,
            color = colors.onSurfaceVariantSummary
        )
    }
}

/** 把框架信息整理成一行的标题 + 一行的能力说明 */
private fun describeService(service: XposedService): Pair<String, String> {
    val caps = mutableListOf<String>()
    val props = service.frameworkProperties
    if (props and XposedService.PROP_CAP_REMOTE != 0L) caps += "远程偏好"
    if (props and XposedService.PROP_CAP_SYSTEM != 0L) caps += "系统域"

    val headline = buildString {
        append("已激活 · ${service.frameworkName} ${service.frameworkVersion}")
    }
    val detail = if (caps.isNotEmpty()) {
        "支持：${caps.joinToString("、")}"
    } else {
        "注意：当前框架不支持远程偏好，开关可能不生效"
    }
    return headline to detail
}

/** 分组小标题 */
@Composable
private fun GroupHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        SmallTitle(text = title, insideMargin = PaddingValues(28.dp, 6.dp))
        Text(
            modifier = Modifier.padding(start = 28.dp),
            text = subtitle,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 单个功能开关行 */
@Composable
private fun FeatureRow(activity: MainActivity, feature: Feature) {
    SwitchPreference(
        title = feature.title,
        summary = feature.summary,
        checked = activity.switches[feature.key] ?: feature.default,
        onCheckedChange = { activity.setSwitch(feature.key, it) }
    )
}

/** 「保留哪些底部标签」的多选列表 */
@Composable
private fun TabKeepCard(activity: MainActivity) {
    val enabled = (activity.switches[Settings.KEY_MASTER] ?: true) &&
        (activity.switches[Settings.KEY_TAB_FILTER] ?: true)

        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Settings.TAB_ITEMS.forEach { (tag, label) ->
            CheckboxPreference(
                title = label,
                checked = activity.tabKeep[tag] ?: true,
                onCheckedChange = { activity.setTab(tag, it) },
                enabled = enabled
            )
        }
    }
}

/** 模块自身：隐藏桌面图标（不走远程偏好） */
@Composable
private fun ModuleCard(activity: MainActivity) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        SwitchPreference(
            title = "隐藏桌面图标",
            summary = "仅移除桌面抽屉中的图标，仍可从 LSPosed 模块列表进入主页",
            checked = activity.hideIcon,
            onCheckedChange = { activity.setHideIcon(it) }
        )
    }
}
