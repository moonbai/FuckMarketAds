package com.owo233.fuckmarketads

import android.os.Bundle
import android.view.MenuItem
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Fuck Market Ads"

        val context = HookEnv.base
        HookSettings.init(context)

        setupSwitch(R.id.switch_hide_security, HookSettings.KEY_HIDE_SECURITY, "隐藏应用安全检查", context)
        setupSwitch(R.id.switch_misc_apply, HookSettings.KEY_MISC_APPLY, "部分细节处理", context)
        setupSwitch(R.id.switch_remove_ads, HookSettings.KEY_REMOVE_ADS, "移除广告", context)
        setupSwitch(R.id.switch_tab_filter, HookSettings.KEY_TAB_FILTER, "显示被隐藏的更新", context)
        setupSwitch(R.id.switch_bypass_ota, HookSettings.KEY_BYPASS_OTA, "禁用OTA验证", context)

        val versionText = findViewById<TextView>(R.id.tv_version)
        try {
            val pkgInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "v${pkgInfo.versionName}"
        } catch (e: Exception) {
            versionText.text = "v1.0.0"
        }
    }

    private fun setupSwitch(switchId: Int, key: String, featureName: String, context: android.content.Context) {
        val switch = findViewById<Switch>(switchId)
        switch.isChecked = HookSettings.isEnabled(context, key)
        switch.setOnCheckedChangeListener { _, isChecked ->
            HookSettings.setEnabled(context, key, isChecked)
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
