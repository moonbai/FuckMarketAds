package com.owo233.fuckmarketads.init

import io.github.libxposed.api.XposedModuleInterface

abstract class BaseHook {

    private lateinit var param: XposedModuleInterface.PackageReadyParam

    var isInit: Boolean = false

    abstract val name: String

    abstract fun init()

    /**
     * 该功能对应的开关 key（见 [com.owo233.fuckmarketads.Settings]）。
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
}
