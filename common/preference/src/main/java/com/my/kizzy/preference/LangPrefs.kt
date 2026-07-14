/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LangPrefs.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.preference

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.my.kizzy.resources.R

// Languages Index number
// NOTE: THAI/JAPANESE keep their original (non-sequential) index values from
// when 24 languages were supported, instead of being renumbered to 2/3. This
// is deliberate: Prefs.LANGUAGE stores this raw Int, so keeping the same
// numbers means existing installs that already had Thai/Japanese selected
// don't get silently reset to system default after this trim. Do not
// renumber these without a migration.
const val SYSTEM_DEFAULT = 0
private const val ENGLISH = 1
private const val THAI = 18
private const val JAPANESE = 19

val languages: Map<Int, String> =
    mapOf(
        Pair(ENGLISH, "en"),
        Pair(THAI, "th"),
        Pair(JAPANESE, "ja")
    ).toList().sortedBy { (_, value) -> value }.toMap()

fun getLanguageConfig(languageNumber: Int = Prefs[Prefs.LANGUAGE]): String {
    return if (languages.containsKey(languageNumber)) languages[languageNumber].toString() else ""
}

private fun getLanguageNumberByCode(languageCode: String): Int {
    languages.entries.forEach {
        if (it.value == languageCode) return it.key
    }
    return SYSTEM_DEFAULT
}

fun getLanguageNumber(): Int {
    return if (Build.VERSION.SDK_INT >= 33)
        getLanguageNumberByCode(
            LocaleListCompat.getAdjustedDefault()[0]?.toLanguageTag().toString()
        )
    else Prefs[Prefs.LANGUAGE, SYSTEM_DEFAULT]
}

@Composable
fun getLanguageDesc(language: Int = getLanguageNumber()): String {
    return stringResource(
        when (language) {
            ENGLISH -> R.string.locale_en
            THAI -> R.string.locale_th
            JAPANESE -> R.string.locale_ja
            else -> R.string.follow_system
        }
    )
}

// Raw device language tag (e.g. "fr", "vi"), independent of whether we ship static
// values-<lang> resources for it. Used only to decide whether on-device auto-translate
// should kick in — see [autoTranslateTargetTag].
private fun getSystemLanguageTag(): String =
    LocaleListCompat.getAdjustedDefault()[0]?.language ?: languages[ENGLISH]!!

/**
 * The language on-device auto-translate should render the UI in, or null if it isn't
 * needed. Non-null only when the *effective* language (per [getLanguageNumber] — this
 * correctly reflects either an explicit pick in the [Language] screen, or on API 33+ a
 * pick made instead via the OS's own per-app-language setting, which bypasses
 * [Prefs.LANGUAGE] entirely) resolves to SYSTEM_DEFAULT (nothing explicit pinned) *and*
 * the device's actual system language isn't one of the three we ship static strings
 * for. In every other case the existing static resource resolution already does the
 * right thing and this must stay null so translation never overrides an explicit choice.
 */
fun autoTranslateTargetTag(): String? {
    if (getLanguageNumber() != SYSTEM_DEFAULT) return null
    val systemTag = getSystemLanguageTag()
    return if (languages.containsValue(systemTag)) null else systemTag
}