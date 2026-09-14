package com.owo233.fuckmarketads.init

import android.util.Log
import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.TAG
import io.github.libxposed.api.XposedModuleInterface

abstract class AppRegister : XposedModuleInterface {

    abstract val packageName: String

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {}

    /**
     * 统一安装一组 hook。
     *
     * 开关策略（修复“子开关不生效”）：
     *  - 总开关 [Settings.KEY_MASTER] 关闭时，直接全部跳过（性能 + 全局兜底）；
     *  - 各功能开关【不再】在安装期判断，而是在每个拦截点内部通过 [BaseHook.enabled]
     *    实时读取，因此用户在主页切换子开关后无需重启被 hook 的应用即可生效；
     *  - 单个 hook 抛异常只会影响它自己（被 onFailure 捕获并记日志），
     *    不会再像以前那样因为某处失败而中断整段 init，从而避免“一个功能失效拖垮其他功能”。
     */
    protected fun autoInitHooks(
        param: XposedModuleInterface.PackageReadyParam,
        vararg hooks: BaseHook
    ) {
        HookEnv.base.log(Log.INFO, TAG, "Try to Hook: $packageName", null)

        if (!Settings.isMasterEnabled()) {
            HookEnv.base.log(Log.INFO, TAG, "总开关已关闭，跳过 $packageName 的全部 hook", null)
            return
        }

        hooks.forEach { hook ->
            runCatching {
                if (hook.isInit) return@runCatching
                hook.setParam(param)
                hook.init()
                hook.isInit = true
            }.onFailure { e ->
                HookEnv.base.log(Log.ERROR, TAG, "Failed to Hook: ${hook.name}", e)
            }
        }
    }
}
