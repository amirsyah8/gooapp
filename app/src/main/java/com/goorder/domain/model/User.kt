package com.goorder.domain.model

data class User(
    val id: Int? = null,
    val name: String? = null,
    val fname: String? = null,
    val phone: String? = null,
    val anual: Int? = null,
    val chatId: String? = null,
    val numberExists: Boolean? = null,
    val e164: String? = null,
    val national: String? = null,
    val country: String? = null,
    val address: String? = null,
    val email: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val status: Boolean? = null,
    val profilePicPath: String? = null,
    val profilePicId: String? = null,
    
    // Kept from previous for compatibility with existing UI if needed
    // or you can remove these if you are sure they are no longer used.
    val avatar: String? = null,
    val bio: String? = null,
    val role: Role? = null,
    val language: Int? = null
)
