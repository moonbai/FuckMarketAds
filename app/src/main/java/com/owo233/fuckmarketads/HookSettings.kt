package com.owo233.fuckmarketads

import android.content.Context

object HookSettings {
    private const val PREF_NAME = "fuck_market_ads_settings"

    const val KEY_HIDE_SECURITY = "hide_security"
    const val KEY_MISC_APPLY = "misc_apply"
    const val KEY_REMOVE_ADS = "remove_ads"
    const val KEY_TAB_FILTER = "tab_filter"
    const val KEY_BYPASS_OTA = "bypass_ota"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_HIDE_SECURITY)) {
            prefs.edit().putBoolean(KEY_HIDE_SECURITY, true).apply()
            prefs.edit().putBoolean(KEY_MISC_APPLY, true).apply()
            prefs.edit().putBoolean(KEY_REMOVE_ADS, true).apply()
            prefs.edit().putBoolean(KEY_TAB_FILTER, true).apply()
            prefs.edit().putBoolean(KEY_BYPASS_OTA, true).apply()
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

    fun getAll(context: Context): Map<String, Boolean> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return mapOf(
            KEY_HIDE_SECURITY to prefs.getBoolean(KEY_HIDE_SECURITY, true),
            KEY_MISC_APPLY to prefs.getBoolean(KEY_MISC_APPLY, true),
            KEY_REMOVE_ADS to prefs.getBoolean(KEY_REMOVE_ADS, true),
            KEY_TAB_FILTER to prefs.getBoolean(KEY_TAB_FILTER, true),
            KEY_BYPASS_OTA to prefs.getBoolean(KEY_BYPASS_OTA, true)
        )
    }
}
