package com.owo233.fuckmarketads

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 初始化开关设置（使用 Activity 自身的 Context）
        HookSettings.init(this)

        // 设置 Toolbar 返回按钮
        val toolbar = findViewById<android.widget.Toolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener { finish() }

        // 绑定5个功能开关
        setupSwitch(R.id.switch_hide_security, HookSettings.KEY_HIDE_SECURITY)
        setupSwitch(R.id.switch_misc_apply, HookSettings.KEY_MISC_APPLY)
        setupSwitch(R.id.switch_remove_ads, HookSettings.KEY_REMOVE_ADS)
        setupSwitch(R.id.switch_tab_filter, HookSettings.KEY_TAB_FILTER)
        setupSwitch(R.id.switch_bypass_ota, HookSettings.KEY_BYPASS_OTA)

        // 显示版本号
        val versionText = findViewById<TextView>(R.id.tv_version)
        try {
            val pkgInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "v${pkgInfo.versionName}"
        } catch (e: Exception) {
            versionText.text = "v1.0.0"
        }
    }

    private fun setupSwitch(switchId: Int, key: String) {
        val switch = findViewById<Switch>(switchId)
        switch.isChecked = HookSettings.isEnabled(this, key)
        switch.setOnCheckedChangeListener { _, isChecked ->
            HookSettings.setEnabled(this, key, isChecked)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
