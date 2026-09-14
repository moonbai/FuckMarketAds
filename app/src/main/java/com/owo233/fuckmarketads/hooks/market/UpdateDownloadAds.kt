package com.owo233.fuckmarketads.hooks.market

import android.util.Log
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.BaseHook
import com.owo233.fuckmarketads.util.getFieldValue
import com.owo233.fuckmarketads.util.setFieldValue
import io.github.kyuubiran.ezxhelper.core.finder.ConstructorFinder.`-Static`.constructorFinder
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 移除应用升级页 / 下载页的软件推荐。
 *
 * 修复点（针对原实现“部分功能不生效”）：
 *  - 原先 [pageCollapseStateExpand] 通过 lazy 在首次访问时加载内部类字段，
 *    若该内部类/字段随商店版本变动而找不到，会直接抛异常，导致整个更新列表适配器
 *    的构造 hook 失败、更新页异常。这里改为失败兜底为 null，并仅在非空时才设置字段。
 */
object UpdateDownloadAds : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_DL

    override val name: String
        get() = "移除升级/下载推荐"

    private val pageCollapseStateExpand by lazy {
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.ui.UpdateListRvAdapter${'$'}PageCollapseState"
            ).getFieldValue("Expand")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "$name: 未找到 PageCollapseState.Expand，跳过展开字段", it)
        }.getOrNull()
    }

    override fun init() {
        // 下载队列页面 / 为你优先
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.DownloadListFragment")
                .methodFinder()
                .filterByName("parseRecommendGroupResult")
                .first()
                .hooked { null }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: DownloadListFragment 拦截失败", it) }

        // 应用升级页面：移除推荐分组 + 默认展开全部
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.UpdateListRvAdapter").apply {
                methodFinder()
                    .filterByName("generateRecommendGroupItems")
                    .first()
                    .hooked { null }

                constructorFinder().forEach { ctor ->
                    ctor.hooked {
                        val result = proceed()

                        pageCollapseStateExpand?.let { expandState ->
                            runCatching {
                                fieldFinder()
                                    .filterByName("forceExpanded")
                                    .first()
                                    .set(thisObject, true)

                                fieldFinder()
                                    .filterByName("foldButtonVisible")
                                    .first()
                                    .set(thisObject, false)

                                fieldFinder()
                                    .filterByName("pageCollapseState")
                                    .first()
                                    .set(thisObject, expandState)
                            }
                        }
                        return@hooked result
                    }
                }
            }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: UpdateListRvAdapter 拦截失败", it) }
    }
}
