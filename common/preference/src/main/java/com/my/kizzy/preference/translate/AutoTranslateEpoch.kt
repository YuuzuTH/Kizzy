package com.my.kizzy.preference.translate

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bumped (after a debounce — see [bump]) every time a background translation lands in
 * [AutoTranslateCache]. Compose's `stringResource()` reads straight from
 * `LocalContext.current.resources` and isn't itself observable, so the root composable
 * keys its whole content on this value (see MainActivity) to force a full
 * recomposition — and therefore a fresh, now-cached lookup for every string on screen —
 * once new translations are ready.
 *
 * `key(...)` doesn't just recompose, it tears down and rebuilds the entire keyed
 * subtree (nav back stack, scroll position, open dialogs, all `remember`/
 * `LaunchedEffect` state). A screen showing dozens of strings during a language's
 * one-time warm-up would otherwise trigger that many times in a row. [bump] instead
 * coalesces every call that arrives within [debounceMs] of the last one into a single
 * actual increment once translations for the current screen settle down — one
 * (still non-zero, but bounded) reset instead of dozens.
 */
object AutoTranslateEpoch {
    private const val debounceMs = 800L

    val value = mutableIntStateOf(0)
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var pending: Job? = null

    fun bump() {
        pending?.cancel()
        pending = scope.launch {
            delay(debounceMs)
            value.intValue++
        }
    }
}
