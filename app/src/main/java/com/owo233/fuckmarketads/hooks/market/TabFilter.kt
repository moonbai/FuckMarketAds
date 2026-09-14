package com.owo233.fuckmarketads.hooks.market

import com.owo233.fuckmarketads.HookEnv
import com.owo233.fuckmarketads.Settings
import com.owo233.fuckmarketads.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object TabFilter : BaseHook() {

    override val prefKey: String = Settings.KEY_TAB_FILTER

    override val name: String
        get() = "筛选底部TAB标签"

    override fun init() {
        ClassUtil.loadClass("com.xiaomi.market.model.TabInfo").also {
            val tabField = it.fieldFinder()
                .filterByName("tag")
                .filterByType(String::class.java)
                .first()

            it.methodFinder()
                .filterByName("fromJSON")
                .filterByParamCount(1)
                .first()
                .hooked {
                    // 用户勾选要保留的标签集合（按 tag 精确匹配），空集合 = 保留全部
                    val kept = Settings.getKeptTabs()
                    if (kept.isEmpty()) return@hooked proceed()

                    val result = proceed()
                    val list = (result as List<*>).toMutableList()
                    list.removeAll { item ->
                        val tag = runCatching { tabField.get(item) as? String }.getOrNull()
                        // 取不到 tag 的项一律移除，避免残留无法识别的标签
                        tag == null || tag !in kept
                    }
                    return@hooked list
                }
        }
    }
}
