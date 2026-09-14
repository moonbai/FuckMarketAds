package com.mars.mimarketpurify

import com.mars.mimarketpurify.apps.Market
import com.mars.mimarketpurify.init.AppRegister
import com.mars.mimarketpurify.init.EasyXposedInit

const val TAG = "FuckMarketAds"

class MainHook : EasyXposedInit() {

    override val registerApp: Set<AppRegister>
        get() = setOf(
            Market
        )
}
