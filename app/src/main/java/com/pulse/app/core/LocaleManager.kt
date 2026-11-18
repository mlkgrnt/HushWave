package com.pulse.app.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用内语言。
 *
 * 选择存在 SharedPreferences 里；没存过 = 跟随系统（wrap 原样返回）。
 * 这里不用 AppCompatDelegate：少一个依赖，而且我们只需要中英两套资源，
 * attachBaseContext 包一层 configuration context 就够了。
 */
object LocaleManager {

    private const val PREF = "locale"
    private const val KEY = "tag"

    /** null = 跟随系统 */
    private fun raw(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)

    fun selected(ctx: Context): String? = raw(ctx)?.ifBlank { null }

    fun set(ctx: Context, tag: String?) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, tag ?: "")
            .apply()
    }

    fun localeOf(tag: String): Locale = when (tag) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "en" -> Locale.ENGLISH
        else -> Locale.forLanguageTag(tag)
    }

    /** 把 context 包成目标语言；跟随系统时原样返回 */
    fun wrap(base: Context): Context {
        val tag = selected(base) ?: return base
        val locale = localeOf(tag)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }

    /** 给 ViewModel / 控制器这类拿不到 Activity 的地方用 */
    fun localized(ctx: Context): Context = wrap(ctx)

    /** 当前是否英文（没选过就看系统） */
    fun isEnglish(ctx: Context): Boolean {
        val tag = selected(ctx) ?: Locale.getDefault().language
        return !tag.startsWith("zh")
    }
}
