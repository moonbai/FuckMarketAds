package com.owo233.fuckmarketads

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.owo233.fuckmarketads.App.ServiceStateListener
import com.owo233.fuckmarketads.Settings.DEFAULT_TAB_KEEP
import com.owo233.fuckmarketads.Settings.KEY_TAB_KEEP
import com.owo233.fuckmarketads.Settings.PREFS_GROUP
import com.owo233.fuckmarketads.Settings.TAB_ITEMS
import com.owo233.fuckmarketads.ui.ALL_FEATURES
import com.owo233.fuckmarketads.ui.SettingsRoot
import io.github.libxposed.service.XposedService

/**
 * 程序主页：以 Jetpack Compose + MiuiX 呈现模块状态与功能开关。
 *
 * 职责划分：
 *  - 本类只负责「数据」：绑定框架服务、读写远程偏好、操作桌面入口 alias；
 *  - UI 渲染全部在 ui/SettingsScreen.kt（[SettingsRoot]）里，通过观察这里的 Compose state 重组。
 *
 * 「隐藏桌面图标」不再禁用本 Activity，而是禁用桌面入口 alias，
 * 保证 LSPosed 等框架始终可以打开主页（详见 manifest 注释）。
 */
class MainActivity : ComponentActivity(), ServiceStateListener {

    /** 桌面入口 alias 的组件名：隐藏图标时只禁用它 */
    private val launcherAlias: ComponentName by lazy {
        ComponentName(this, "$packageName.LauncherAlias")
    }

    /** 框架服务实例；本身作为 Compose state，服务变化时主页自动重组 */
    internal val serviceState = mutableStateOf<XposedService?>(null)

    /** 各功能开关的 UI 状态（key -> 是否开启） */
    internal val switches = mutableStateMapOf<String, Boolean>()

    /** 「保留哪些底部标签」的 UI 状态（tag -> 是否保留） */
    internal val tabKeep = mutableStateMapOf<String, Boolean>()

    /** 「隐藏桌面图标」的 UI 状态（不经过远程偏好） */
    internal var hideIcon by mutableStateOf(false)
        private set

    private val service: XposedService? get() = serviceState.value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 入口自愈：曾被旧版本锁出的设备，覆盖安装后自动恢复
        EntryGuardReceiver.ensureEntryEnabled(this)
        enableEdgeToEdge()

        reloadAll()
        setContent { SettingsRoot(this) }
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, true)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        // 回调不保证在主线程，Compose state 必须在主线程更新
        runOnUiThread {
            serviceState.value = service
            reloadAll()
        }
    }

    // ===== UI 交互入口（由 Compose 调用）=====

    /**
     * 修改一个布尔开关：乐观更新 UI，写入失败则回滚并提示。
     */
    internal fun setSwitch(key: String, value: Boolean) {
        val previous = switches[key] ?: true
        switches[key] = value
        if (!writeRemote(key, value)) {
            switches[key] = previous
            toast("模块未激活，无法保存")
        }
    }

    /**
     * 修改「保留的底部标签」集合（逗号分隔写入远程偏好）。
     */
    internal fun setTab(tag: String, checked: Boolean) {
        tabKeep[tag] = checked
        val kept = tabKeep.filterValues { it }.keys.toMutableSet()
        if (checked) kept += tag else kept -= tag
        if (!writeRemoteString(KEY_TAB_KEEP, kept.joinToString(","))) {
            tabKeep[tag] = !checked
            toast("模块未激活，无法保存")
        }
    }

    /**
     * 隐藏 / 恢复桌面图标：只切换 alias 组件，MainActivity 始终保持启用。
     */
    internal fun setHideIcon(hide: Boolean) {
        val previous = hideIcon
        hideIcon = hide
        runCatching {
            EntryGuardReceiver.ensureEntryEnabled(this)
            val state = if (hide) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            packageManager.setComponentEnabledSetting(
                launcherAlias, state, PackageManager.DONT_KILL_APP
            )
            toast(if (hide) "已隐藏桌面图标，可在 LSPosed 模块列表中进入主页" else "已恢复桌面图标")
        }.onFailure {
            hideIcon = previous
            toast("操作失败：${it.message}")
        }
    }

    // ===== 远程偏好读写 =====

    /** 从远程偏好重新拉取全部 UI 状态 */
    internal fun reloadAll() {
        switches[Settings.KEY_MASTER] = readBool(Settings.KEY_MASTER, true)
        ALL_FEATURES.forEach { switches[it.key] = readBool(it.key, it.default) }
        val kept = readTabs()
        TAB_ITEMS.forEach { (tag, _) -> tabKeep[tag] = kept.contains(tag) }
        hideIcon = isLauncherIconHidden()
    }

    /** 读远程偏好；服务未连接时回落到默认值 */
    private fun readBool(key: String, def: Boolean): Boolean {
        return service?.getRemotePreferences(PREFS_GROUP)?.getBoolean(key, def) ?: def
    }

    /** 写布尔值；返回是否写入成功 */
    private fun writeRemote(key: String, value: Boolean): Boolean {
        return runCatching {
            service?.getRemotePreferences(PREFS_GROUP)?.edit()?.putBoolean(key, value)?.apply()
            service != null
        }.getOrDefault(false)
    }

    /** 写字符串；返回是否写入成功 */
    private fun writeRemoteString(key: String, value: String): Boolean {
        return runCatching {
            service?.getRemotePreferences(PREFS_GROUP)?.edit()?.putString(key, value)?.apply()
            service != null
        }.getOrDefault(false)
    }

    /** 读取「保留哪些标签」的集合（逗号分隔字符串解析为 tag 集合） */
    private fun readTabs(): Set<String> {
        val raw = service?.getRemotePreferences(PREFS_GROUP)
            ?.getString(KEY_TAB_KEEP, DEFAULT_TAB_KEEP)
            ?: DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** 当前桌面图标是否已被隐藏（即桌面入口 alias 被禁用） */
    private fun isLauncherIconHidden(): Boolean {
        return runCatching {
            packageManager.getComponentEnabledSetting(launcherAlias) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
