package com.owo233.fuckmarketads.hooks.market

import android.util.Log
import android.view.View
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder

/**
 * 屏蔽「领水果」活动入口。
 *
 * 该入口是一个 [android.widget.ImageView]（资源名 `entrance_gif`，Layout Inspector 可见其 id 即 `entrance_gif`，
 * 本身无子控件，属正常的叶子 View）。模块无法静态引用应用商店的 R 类，故运行时通过
 * [android.content.res.Resources.getIdentifier] 按资源名解析其 id。
 *
 * 实现要点（针对“出现时机不稳定 / 多个界面都有”）：
 *  - hook [View.onAttachedToWindow]：视图一挂到窗口就按 id 命中并 [View.GONE]，
 *    覆盖“随布局异步出现 / 默认就可见”的情形（解决“等很久才消失”）；
 *  - hook [View.setVisibility]：若目标被重新设为 [View.VISIBLE]
 *    （数据刷新、界面切换后再次显示），直接拦截为 [View.GONE]，避免延迟闪现；
 *  - 资源 id 仅在首次触发时解析一次并缓存，避免每次挂接都查表。
 *
 * 注意：本 hook 只在 `com.xiaomi.market` 进程内安装（见 [com.owo233.fuckmarketads.apps.Market]），
 * 不影响其他应用；多界面（多个 Activity / Fragment）均会被覆盖，因为底层 hook 的是 View 自身。
 */
object HideFruitEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_FRUIT

    override val name: String
        get() = "屏蔽领水果入口"

    private const val RES_NAME = "entrance_gif"
    private const val RES_TYPE = "id"

    @Volatile
    private var fruitId: Int = -1

    override fun init() {
        // 1) 视图挂接到窗口时立即隐藏（覆盖异步 / 布局内默认可见）
        runCatching {
            View::class.java.methodFinder()
                .filterByName("onAttachedToWindow")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val view = thisObject as? View ?: return@hooked null
                    ensureId(view)
                    if (fruitId != -1 && view.id == fruitId) {
                        view.visibility = View.GONE
                    }
                    return@hooked null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow 安装失败", it)
        }

        // 2) 目标被重新设为 VISIBLE 时强制拦截为 GONE（覆盖刷新 / 切换后再次显示）
        runCatching {
            View::class.java.methodFinder()
                .filterByName("setVisibility")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val view = thisObject as? View
                    if (view != null && fruitId != -1 && view.id == fruitId) {
                        val vis = (args[0] as? Int) ?: return@hooked proceed()
                        if (vis == View.VISIBLE) {
                            // 重新进入本 hook，GONE 分支会正常 proceed，不会死循环
                            view.visibility = View.GONE
                            return@hooked null
                        }
                    }
                    return@hooked proceed()
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility 安装失败", it)
        }
    }

    private fun ensureId(view: View) {
        if (fruitId != -1) return
        synchronized(this) {
            if (fruitId == -1) {
                fruitId = runCatching {
                    view.resources.getIdentifier(RES_NAME, RES_TYPE, view.context.packageName)
                }.getOrDefault(-1)
            }
        }
    }
}
