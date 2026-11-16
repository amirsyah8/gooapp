package com.goorder.data.remote.dto

import com.goorder.domain.model.Role
import com.goorder.domain.model.User
import kotlinx.serialization.SerialName
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
data class LoginResultDto(
    val token: String? = null,
    val message: String? = null
)

@Serializable
data class LoginResponse(
    val login: LoginResultDto? = null
)

@Serializable
data class MeResponse(
    val me: UserDto? = null
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

fun RoleDto.toDomain() = Role(id, type, option, desc)
fun Role.toDto() = RoleDto(id, type, option, desc)

@Serializable
data class UserDto(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    @SerialName("role_id") val roleId: Int? = null,
    @SerialName("role_str") val roleStr: String? = null,
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
    val role: RoleDto? = null,
    val language: Int? = null
)

fun UserDto.toDomain(): User {
    val effectiveRole = role?.toDomain() ?: if (roleId != null || roleStr != null) {
        Role(id = roleId, option = roleStr?.lowercase())
    } else null

    return User(
        id = id,
        name = name,
        email = email,
        roleId = roleId,
        roleStr = roleStr,
        phone = phone,
        type = type,
        status = status,
        theme = theme,
        mailvalidation = mailvalidation,
        hasPassword = hasPassword,
        fname = fname,
        anual = anual,
        chatId = chatId,
        numberExists = numberExists,
        e164 = e164,
        national = national,
        country = country,
        address = address,
        createdAt = createdAt,
        updatedAt = updatedAt,
        profilePicPath = profilePicPath,
        profilePicId = profilePicId,
        avatar = avatar,
        bio = bio,
        role = effectiveRole,
        language = language
    )
}

fun User.toDto(): UserDto {
    return UserDto(
        id = id,
        name = name,
        email = email,
        roleId = roleId,
        roleStr = roleStr,
        phone = phone,
        type = type,
        status = status,
        theme = theme,
        mailvalidation = mailvalidation,
        hasPassword = hasPassword,
        fname = fname,
        anual = anual,
        chatId = chatId,
        numberExists = numberExists,
        e164 = e164,
        national = national,
        country = country,
        address = address,
        createdAt = createdAt,
        updatedAt = updatedAt,
        profilePicPath = profilePicPath,
        profilePicId = profilePicId,
        avatar = avatar,
        bio = bio,
        role = role?.toDto(),
        language = language
    )
}
