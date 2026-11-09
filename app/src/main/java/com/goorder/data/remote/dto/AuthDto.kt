package com.goorder.data.remote.dto

import com.goorder.domain.model.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, JsonElement>? = null
)

@Serializable
data class GraphQLResponse<T>(
    val data: T? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GraphQLError(
    val message: String
)

@Serializable
data class LoginResponse(
    val login: UserDto? = null
)

@Serializable
data class WhoAmIResponse(
    val whoAmI: UserDto? = null
)

@Serializable
data class UpdateFnameResponse(val updateFname: UserDto? = null)

@Serializable
data class UpdateAddressResponse(val updateAddress: UserDto? = null)

@Serializable
data class UpdateBioResponse(val updateBio: UserDto? = null)

@Serializable
data class UpdateLanguageResponse(val updateLanguage: UserDto? = null)

@Serializable
data class UpdateAvatarResponse(val updateAvatar: UserDto? = null)

@Serializable
data class UploadResponse(val uuid: String)

@Serializable
data class RoleDto(
    val id: Int? = null,
    val type: String? = null,
    val option: String? = null,
    val desc: String? = null
)

fun RoleDto.toDomain() = com.goorder.domain.model.Role(id, type, option, desc)
fun com.goorder.domain.model.Role.toDto() = RoleDto(id, type, option, desc)

@Serializable
data class UserDto(
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
    
    // Previous fields for backward compatibility mapping
    val avatar: String? = null,
    val bio: String? = null,
    val role: RoleDto? = null,
    val language: Int? = null
)

fun UserDto.toDomain(): User {
    return User(
        id = id, name = name, fname = fname, phone = phone,
        anual = anual, chatId = chatId, numberExists = numberExists,
        e164 = e164, national = national, country = country,
        address = address, email = email, createdAt = createdAt,
        updatedAt = updatedAt, status = status,
        profilePicPath = profilePicPath, profilePicId = profilePicId,
        avatar = avatar, bio = bio, role = role?.toDomain(), language = language
    )
}

fun User.toDto(): UserDto {
    return UserDto(
        id = id, name = name, fname = fname, phone = phone,
        anual = anual, chatId = chatId, numberExists = numberExists,
        e164 = e164, national = national, country = country,
        address = address, email = email, createdAt = createdAt,
        updatedAt = updatedAt, status = status,
        profilePicPath = profilePicPath, profilePicId = profilePicId,
        avatar = avatar, bio = bio, role = role?.toDto(), language = language
    )
}
