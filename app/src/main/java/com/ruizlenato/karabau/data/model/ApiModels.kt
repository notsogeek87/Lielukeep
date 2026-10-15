package com.ruizlenato.karabau.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExchangeKeyRequest(
    val email: String,
    val password: String,
    val keyName: String
)

@Serializable
data class ExchangeKeyResponse(
    val id: String,
    val name: String,
    val key: String,
    val createdAt: String
)

@Serializable
data class ValidateKeyRequest(
    val apiKey: String
)

@Serializable
data class ValidateKeyResponse(
    val success: Boolean
)

@Serializable
data class RevokeKeyRequest(
    val id: String
)

@Serializable
data class CreateBookmarkRequest(
    val type: String = "link",
    val url: String,
    val title: String? = null,
    val note: String? = null,
    val tags: List<String>? = null,
    val source: String = "mobile"
)

@Serializable
data class UpdateBookmarkRequest(
    val bookmarkId: String,
    val favourited: Boolean? = null,
    val archived: Boolean? = null,
    val title: String? = null,
    val note: String? = null,
    val url: String? = null
)

@Serializable
data class DeleteBookmarkRequest(
    val bookmarkId: String
)

@Serializable
data class HealthCheckResponse(
    val status: String,
    val version: String? = null
)

@Serializable
data class CreateListRequest(
    val name: String,
    val icon: String,
    val type: String = "manual",
    val parentId: String? = null
)

@Serializable
data class EditListRequest(
    val listId: String,
    val name: String
)

@Serializable
data class ListIdRequest(
    val listId: String
)

@Serializable
data class ListBookmarkRequest(
    val listId: String,
    val bookmarkId: String
)

@Serializable
data class TagRef(
    val tagId: String? = null,
    val tagName: String? = null,
    val attachedBy: String? = null
)

@Serializable
data class UpdateTagsRequest(
    val bookmarkId: String,
    val attach: List<TagRef> = emptyList(),
    val detach: List<TagRef> = emptyList()
)
