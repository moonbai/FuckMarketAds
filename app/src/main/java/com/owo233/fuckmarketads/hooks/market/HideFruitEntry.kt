package com.owo233.fuckmarketads.hooks.market

import android.app.Activity
import android.util.Log
import android.view.View
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import com.owo233.fuckmarketads.init.AppPackage
import com.owo233.fuckmarketads.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder

/**
 * 屏蔽「领水果」活动入口。
 *
 * 该入口是一个 [android.widget.ImageView]（资源名 `entrance_gif`，推测为福利 / 活动 gif 动图入口）。
 * 由于模块无法静态引用应用商店的 R 类，这里在运行时通过
 * [android.content.res.Resources.getIdentifier] 按资源名解析其 id，再在视图树中隐藏。
 *
 * 实现思路（鲁棒性优先，避免依赖具体 Activity 类名）：
 *  hook 应用商店内 [Activity.onResume]，视图就绪后遍历 decorView，
 *  找到 id 命中 `entrance_gif` 的 View 并设为 [View.GONE]。
 *
 * 注意（需实机复核）：
 *  - 本 hook 仅在 `com.xiaomi.market` 进程内安装，不影响其他应用；
 *  - 请用 Layout Inspector 确认该 ImageView 确实出现于某个 market Activity、
 *    且资源名确为 `entrance_gif`（不同 ROM / 商店版本可能存在差异）。
 */
object HideFruitEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_FRUIT

    override val name: String
        get() = "屏蔽领水果入口"

    private const val RES_NAME = "entrance_gif"
    private const val RES_TYPE = "id"

    override fun init() {
        runCatching {
            Activity::class.java.methodFinder()
                .filterByName("onResume")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val activity = thisObject as? Activity ?: return@hooked null
                    // 双保险：只处理小米应用商店自身的 Activity
                    if (!activity.javaClass.name.startsWith(AppPackage.MARKET)) {
                        return@hooked null
                    }
                    val id = runCatching {
                        activity.resources.getIdentifier(RES_NAME, RES_TYPE, activity.packageName)
                    }.getOrNull() ?: return@hooked null
                    if (id == 0) return@hooked null

                    activity.window?.decorView?.post {
                        runCatching {
                            val target = activity.findViewById<View>(id)
                            target?.visibility = View.GONE
                        }
                    }
                    return@hooked null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: 安装失败", it)
        }
    }
}
