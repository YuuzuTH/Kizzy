package com.my.kizzy.preference.translate

import android.content.res.Resources
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable
import java.util.IllegalFormatException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Wraps the app's (English-resolved, since that's our fallback for every language we
 * don't ship values-<lang> for) [Resources] and machine-translates string lookups into
 * [targetLangTag] on the fly via on-device ML Kit, so someone whose system language
 * isn't English/Thai/Japanese still sees the app in their own language instead of
 * silently falling back to English.
 *
 * Translation is inherently async (on-device model download + inference), but
 * Resources.getString() must return synchronously, so the first call for a given
 * string returns the English original immediately and kicks off a background
 * translation; once it lands it's cached (see [AutoTranslateCache], plus an in-memory
 * layer here so repeat lookups in the same process skip the MMKV read too) and
 * [onTranslated] is notified so the caller can trigger a recomposition to pick it up.
 * Every call after that (including the very next app launch) is a synchronous cache
 * hit — no repeated translation, no repeated model inference.
 *
 * Only getText()/getString() are overridden — and only for Compose screens reached
 * through the Activity this wraps (see MainActivity.getResources()). Foreground-service
 * notifications (RPC connection status, media/custom RPC) read strings through their
 * own Service Context and are NOT covered by this wrapper; they stay in English. Fixing
 * that would mean wrapping every Service's Context the same way, which is a larger,
 * separate change. Plurals (getQuantityString) and string arrays are also left in
 * English here: ML Kit doesn't translate plural-rule-aware, and it isn't worth the
 * added complexity for the relatively few plural strings in this app.
 */
@Suppress("DEPRECATION") // Resources(AssetManager, DisplayMetrics, Configuration) ctor
class AutoTranslateResources(
    base: Resources,
    private val targetLangTag: String,
    private val onTranslated: () -> Unit,
) : Resources(base.assets, base.displayMetrics, base.configuration), Closeable {

    private val targetLocale = Locale.forLanguageTag(targetLangTag)

    // Null when ML Kit has no model for this tag at all (e.g. an unsupported/regional
    // tag) — in that case every lookup below just falls back to the English original,
    // same as before this feature existed.
    private val translator = TranslateLanguage.fromLanguageTag(targetLangTag)?.let { target ->
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(target)
                .build()
        )
    }

    // In-memory tier in front of AutoTranslateCache's MMKV-backed store: once a string
    // has been read this process, don't pay a disk-mapped decode for every subsequent
    // getString() of the same id (Compose calls this a lot, and a full-tree
    // recomposition re-reads every on-screen string at once — see AutoTranslateEpoch).
    private val memoryCache = ConcurrentHashMap<Int, String>()

    // Resource ids with a translation already in flight, so a screen reading dozens of
    // strings at once doesn't kick off a redundant duplicate translate() call per string.
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()

    // Ids whose translation attempt failed (offline, model download error, ...) and
    // when that happened — without this, every recomposition that reads a still-failed
    // id would retry the model download/translate immediately, forever.
    private val lastFailureAt = ConcurrentHashMap<Int, Long>()
    private val retryCooldownMs = 60_000L

    override fun getText(id: Int): CharSequence = translatedOrNull(id) ?: super.getText(id)

    override fun getString(id: Int): String = translatedOrNull(id) ?: super.getString(id)

    override fun getString(id: Int, vararg formatArgs: Any): String {
        val template = translatedOrNull(id) ?: super.getString(id)
        return try {
            String.format(targetLocale, template, *formatArgs)
        } catch (_: IllegalFormatException) {
            // The machine-translated template can come back with a mangled/reordered
            // %1$s-style placeholder — ML Kit translates it as opaque text with no
            // awareness it's a format specifier. Rather than crash (and keep crashing,
            // since the bad translation is cached permanently), fall back to formatting
            // the original English template instead.
            String.format(targetLocale, super.getString(id), *formatArgs)
        }
    }

    /** The cached translation for [id], or null if none exists yet (in which case a
     * background translation attempt is kicked off, unless one is already in flight or
     * still in its post-failure cooldown). Callers fall back to the original resource
     * (English text, with any Spanned/HTML markup intact) while this returns null. */
    private fun translatedOrNull(id: Int): String? {
        if (translator == null) return null
        memoryCache[id]?.let { return it }

        val cached = AutoTranslateCache.get(id, targetLangTag)
        if (cached != null) {
            memoryCache[id] = cached
            return cached
        }

        val failedAt = lastFailureAt[id]
        if (failedAt != null && System.currentTimeMillis() - failedAt < retryCooldownMs) return null
        if (!inFlight.add(id)) return null

        val english = super.getString(id)
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(english)
                    .addOnSuccessListener { translated ->
                        AutoTranslateCache.put(id, targetLangTag, translated)
                        memoryCache[id] = translated
                        inFlight.remove(id)
                        onTranslated()
                    }
                    .addOnFailureListener {
                        lastFailureAt[id] = System.currentTimeMillis()
                        inFlight.remove(id)
                    }
            }
            .addOnFailureListener {
                lastFailureAt[id] = System.currentTimeMillis()
                inFlight.remove(id)
            }

        return null
    }

    /** Releases the on-device translator model. Call when the owning Activity is
     * destroyed — otherwise every Activity recreation (rotation, etc.) leaks the
     * previous instance's loaded model. */
    override fun close() {
        translator?.close()
    }
}
