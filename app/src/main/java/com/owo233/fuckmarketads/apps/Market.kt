package com.owo233.fuckmarketads.apps

import com.owo233.fuckmarketads.hooks.market.DetailAds
import com.owo233.fuckmarketads.hooks.market.HideSecurityView
import com.owo233.fuckmarketads.hooks.market.HideFruitEntry
import com.owo233.fuckmarketads.hooks.market.HomeFeed
import com.owo233.fuckmarketads.hooks.market.MainTabAds
import com.owo233.fuckmarketads.hooks.market.MiscApply
import com.owo233.fuckmarketads.hooks.market.SearchAds
import com.owo233.fuckmarketads.hooks.market.SplashAds
import com.owo233.fuckmarketads.hooks.market.TabFilter
import com.owo233.fuckmarketads.hooks.market.UpdateDownloadAds
import com.owo233.fuckmarketads.init.AppPackage
import com.owo233.fuckmarketads.init.AppRegister
import io.github.libxposed.api.XposedModuleInterface

object Market : AppRegister() {

    override val packageName: String
        get() = AppPackage.MARKET

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        autoInitHooks(
            param,
            SplashAds,
            MainTabAds,
            HomeFeed,
            SearchAds,
            UpdateDownloadAds,
            DetailAds,
            HideSecurityView,
            HideFruitEntry,
            TabFilter,
            MiscApply
        )
    }
}
