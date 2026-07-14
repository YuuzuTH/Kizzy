package com.my.kizzy.preference.translate

import com.my.kizzy.preference.Prefs

/**
 * Persists ML-Kit-translated string *templates* (placeholders like %1$s left intact,
 * not yet formatted with args) so the on-device model only has to translate each
 * resource once per target language, not on every recomposition/getString() call.
 * Backed by the same MMKV store as everything else in [Prefs], one key per
 * (resource id, language) pair — avoids reading/writing one big blob on every miss.
 */
internal object AutoTranslateCache {
    private fun key(resId: Int, targetLangTag: String) = "auto_translate_${targetLangTag}_$resId"

    fun get(resId: Int, targetLangTag: String): String? {
        val cached = Prefs.get(key(resId, targetLangTag), "")
        return cached.ifEmpty { null }
    }

    fun put(resId: Int, targetLangTag: String, translatedTemplate: String) {
        Prefs[key(resId, targetLangTag)] = translatedTemplate
    }
}
