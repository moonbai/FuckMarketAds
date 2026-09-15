package com.mars.mimarketpurify.apps

import com.mars.mimarketpurify.hooks.market.AntiSelfDestruct
import com.mars.mimarketpurify.hooks.market.ConfigBackupRestore
import com.mars.mimarketpurify.hooks.market.DetailAds
import com.mars.mimarketpurify.hooks.market.EnableSuperIsland
import com.mars.mimarketpurify.hooks.market.HideSecurityView
import com.mars.mimarketpurify.hooks.market.HideFruitEntry
import com.mars.mimarketpurify.hooks.market.HomeFeed
import com.mars.mimarketpurify.hooks.market.RankAds
import com.mars.mimarketpurify.hooks.market.MainTabAds
import com.mars.mimarketpurify.hooks.market.DetailExtras
import com.mars.mimarketpurify.hooks.market.EntranceAds
import com.mars.mimarketpurify.hooks.market.MineAdGroup
import com.mars.mimarketpurify.hooks.market.TabBadge
import com.mars.mimarketpurify.hooks.market.UpdateDialogBlock
import com.mars.mimarketpurify.hooks.market.MiscApply
import com.mars.mimarketpurify.hooks.market.RecommendSections
import com.mars.mimarketpurify.hooks.market.SearchAds
import com.mars.mimarketpurify.hooks.market.SplashAds
import com.mars.mimarketpurify.hooks.market.TabFilter
import com.mars.mimarketpurify.hooks.market.UiCleanup
import com.mars.mimarketpurify.hooks.market.UpdateDownloadAds
import com.mars.mimarketpurify.init.AppPackage
import com.mars.mimarketpurify.init.AppRegister
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
            RankAds,
            UiCleanup,
            RecommendSections,
            // 以下为从 XiaomiHelper 补移植的应用商店规则
            TabBadge,
            EntranceAds,
            MineAdGroup,
            DetailExtras,
            UpdateDialogBlock,
            EnableSuperIsland,
            MiscApply,
            // 以下为稳定性增强（参考 lisrain/NewFuckMarketAds_Fork）：
            // 纯保护性安全网，不受单个功能开关控制，由总开关统一门控。
            AntiSelfDestruct,
            ConfigBackupRestore
        )
    }
}
