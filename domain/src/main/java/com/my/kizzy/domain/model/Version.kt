// https://github.com/Ashinch/ReadYou/blob/main/app/src/main/java/me/ash/reader/data/model/general/Version.kt

package com.my.kizzy.domain.model

import com.my.kizzy.domain.model.release.Release

/**
 * A class that represents a version made of any number of dot-separated numeric
 * components (e.g. `6.2.1.5`). Every component is compared, not just major/minor,
 * so builds that only differ in a later component (Kizzy uses `6.2.1.00X`) are
 * ordered correctly. Missing/non-numeric components are treated as 0.
 *
 * @param numbers The raw version components.
 */
class Version(numbers: List<String>) {

    private val parts: List<Int> = numbers.map { it.trim().toIntOrNull() ?: 0 }

    constructor() : this(listOf())

    // A leading "v"/"V" from a git tag (e.g. "v6.2.1.5") is stripped before parsing.
    constructor(string: String?) : this(
        (string?.trim()?.removePrefix("v")?.removePrefix("V") ?: "")
            .split(".")
            .filter { it.isNotEmpty() }
    )

    override fun toString() = parts.joinToString(".").ifEmpty { "0" }

    /**
     * Compares every component in order; shorter versions are padded with 0, so
     * `6.2` == `6.2.0.0` and `6.2.1.5` > `6.2.1`.
     */
    operator fun compareTo(other: Version): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (i in 0 until size) {
            val a = parts.getOrElse(i) { 0 }
            val b = other.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    /**
     * Returns whether this version is larger than the [current] version.
     */
    fun whetherNeedUpdate(current: Version): Boolean = this > current
}

fun String?.toVersion(): Version = Version(this)

fun Release.toVersion(): Version = Version(tagName)