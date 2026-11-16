package com.goorder.domain.model

data class User(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val roleId: Int? = null,
    val roleStr: String? = null,
    val phone: String? = null,
    val type: String? = null,
    val status: String? = null,
    val theme: Boolean? = null,
    val mailvalidation: Boolean? = null,
    val hasPassword: Boolean? = null,

    // Additional / legacy fields
    val fname: String? = null,
    val anual: Int? = null,
    val chatId: String? = null,
    val numberExists: Boolean? = null,
    val e164: String? = null,
    val national: String? = null,
    val country: String? = null,
    val address: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val profilePicPath: String? = null,
    val profilePicId: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val role: Role? = null,
    val language: Int? = null
)
