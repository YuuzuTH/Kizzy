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