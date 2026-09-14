package com.mars.mimarketpurify.init

import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Executable

abstract class BaseHook {

    private lateinit var param: XposedModuleInterface.PackageReadyParam

    var isInit: Boolean = false

    abstract val name: String

    abstract fun init()

    /**
     * 该功能对应的开关 key（见 [com.mars.mimarketpurify.Settings]）。
     * 为 null 表示不受开关控制、始终启用（如纯测试 hook）。
     */
    open val prefKey: String? = null

    /** 开关的默认值：绝大多数功能默认开启 */
    open val defaultEnabled: Boolean = true

    fun setParam(param: XposedModuleInterface.PackageReadyParam) {
        this.param = param
    }

    protected fun getParam(): XposedModuleInterface.PackageReadyParam {
        if (!this::param.isInitialized) {
            throw IllegalStateException("param should be initialized.")
        }
        return param
    }

    protected fun getClassLoader(): ClassLoader {
        return getParam().classLoader
    }

    /**
     * 运行时判断是否应执行本 hook：
     *  - 总开关关闭则一律不执行；
     *  - 否则按本功能开关 [prefKey] 决定（未设置 key 视为始终启用）。
     *
     * 与安装期的判断不同，这里在【每次方法被调用时】重新读取远程偏好，
     * 因此用户在模块主页切换子开关后，无需重启被 hook 的应用即可生效。
     */
    protected fun enabled(): Boolean {
        if (!Settings.isMasterEnabled()) return false
        val key = prefKey ?: return true
        return Settings.isEnabled(key, defaultEnabled)
    }

    /**
     * 安装一个“受开关控制”的方法 / 构造 hook。
     *
     * 每次被调用时先检查 [enabled]：
     *  - 开关开启 → 执行 [block]；
     *  - 开关关闭 → 原样放行（[XposedInterface.Chain.proceed]）。
     */
    protected fun Executable.hooked(block: XposedInterface.Chain.() -> Any?) {
        HookEnv.base.hook(this).intercept { param ->
            if (!enabled()) return@intercept param.proceed()
            param.block()
        }
    }
}
