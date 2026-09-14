package com.owo233.fuckmarketads

import android.content.Context

object HookSettings {
    private const val PREF_NAME = "fuck_market_ads_settings"

    const val KEY_HIDE_SECURITY = "hide_security"
    const val KEY_MISC_APPLY = "misc_apply"
    const val KEY_TAB_FILTER = "tab_filter"
    const val KEY_BYPASS_OTA = "bypass_ota"
    const val KEY_ADS_SPLASH = "ads_splash"
    const val KEY_ADS_SEARCH = "ads_search"
    const val KEY_ADS_SEARCH_RESULT = "ads_search_result"
    const val KEY_ADS_UPDATE = "ads_update"
    const val KEY_ADS_DETAIL = "ads_detail"
    const val KEY_ADS_VIDEO = "ads_video"
    const val KEY_ADS_HOTWORD = "ads_hotword"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_HIDE_SECURITY)) {
            prefs.edit()
                .putBoolean(KEY_HIDE_SECURITY, true)
                .putBoolean(KEY_MISC_APPLY, true)
                .putBoolean(KEY_TAB_FILTER, true)
                .putBoolean(KEY_BYPASS_OTA, true)
                .putBoolean(KEY_ADS_SPLASH, true)
                .putBoolean(KEY_ADS_SEARCH, true)
                .putBoolean(KEY_ADS_SEARCH_RESULT, true)
                .putBoolean(KEY_ADS_UPDATE, true)
                .putBoolean(KEY_ADS_DETAIL, true)
                .putBoolean(KEY_ADS_VIDEO, true)
                .putBoolean(KEY_ADS_HOTWORD, true)
                .apply()
        }
    }

    fun isEnabled(context: Context, key: String): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(key, true)
    }

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(key, enabled).apply()
    }
}