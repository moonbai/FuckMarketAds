package com.mars.mimarketpurify

import com.mars.mimarketpurify.apps.Market
import com.mars.mimarketpurify.init.AppRegister
import com.mars.mimarketpurify.init.EasyXposedInit

/** logcat 标签：过滤时用 `adb logcat -s MiMarketPurify:*` */
const val TAG = "MiMarketPurify"

class MainHook : EasyXposedInit() {

    override val registerApp: Set<AppRegister>
        get() = setOf(
            Market
        )
}
