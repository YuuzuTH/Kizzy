/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Release.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.domain.model.release


import kotlinx.serialization.SerialName
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class Release(
    @SerialName("url")
    val url: String? = null,
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("assets_url")
    val assetsUrl: String? = null,
    @SerialName("upload_url")
    val uploadUrl: String? = null,
    @SerialName("tarball_url")
    val tarballUrl: String? = null,
    @SerialName("zipball_url")
    val zipballUrl: String? = null,
    @SerialName("discussion_url")
    val discussionUrl: String? = null,
    @SerialName("id")
    val id: Int? = null,
    @SerialName("node_id")
    val nodeId: String? = null,
    @SerialName("tag_name")
    val tagName: String? = null,
    @SerialName("target_commitish")
    val targetCommitish: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("draft")
    val draft: Boolean? = null,
    @SerialName("prerelease")
    val prerelease: Boolean? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("author")
    val author: Author? = null,
    @SerialName("assets")
    val assets: List<Asset?>? = null
)

// Hidden marker appended to a release body (by kizzy-release.sh --critical) to flag it
// as a mandatory/forced update. GitHub renders release bodies as markdown, so an HTML
// comment is invisible on the releases page but still present in the raw `body` field
// this app fetches from the API — no dedicated API field needed.
private const val CRITICAL_UPDATE_MARKER = "<!--KIZZY_CRITICAL_UPDATE-->"

/** Whether this release is a mandatory/forced update (see [CRITICAL_UPDATE_MARKER]). */
fun Release.isCritical(): Boolean = body?.contains(CRITICAL_UPDATE_MARKER) == true

/** [body] with the hidden critical-update marker stripped, safe to show to the user. */
fun Release.changelogBody(): String = (body ?: "").replace(CRITICAL_UPDATE_MARKER, "").trimEnd()