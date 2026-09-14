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
     * 统一安装一组 hook，并在安装前根据开关决定是否跳过。
     *
     * 修复点：
     *  - 总开关 [Settings.KEY_MASTER] 关闭时，直接全部跳过；
     *  - 每个 hook 通过 [BaseHook.prefKey] 对应的开关控制，关闭则跳过该 hook；
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
                if (hook.prefKey != null &&
                    !Settings.isEnabled(hook.prefKey!!, hook.defaultEnabled)
                ) {
                    HookEnv.base.log(Log.INFO, TAG, "功能开关关闭，跳过: ${hook.name}", null)
                    return@runCatching
                }
                hook.setParam(param)
                hook.init()
                hook.isInit = true
            }.onFailure { e ->
                HookEnv.base.log(Log.ERROR, TAG, "Failed to Hook: ${hook.name}", e)
            }
        }
    }
}
