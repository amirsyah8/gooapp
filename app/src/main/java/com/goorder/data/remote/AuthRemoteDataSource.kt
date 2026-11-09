package com.goorder.data.remote

import android.content.Context
import com.goorder.BuildConfig
import com.goorder.data.PersistentCookieStorage
import com.goorder.data.local.SessionManager
import com.goorder.data.remote.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

class AuthRemoteDataSource(
    context: Context,
    private val sessionManager: SessionManager = SessionManager(context)
) {
    private val cookieStorage = PersistentCookieStorage(context)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpCookies) {
            storage = cookieStorage
        }
        if (BuildConfig.DEBUG) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.BODY
            }
        }
    }

    private suspend fun HttpRequestBuilder.addAuthHeader() {
        val token = sessionManager.getToken()
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun login(email: String, pass: String): Result<LoginResultDto> {
        val query = """
            mutation Login(${'$'}input: LoginInput!) {
              login(input: ${'$'}input) {
                token
                message
              }
            }
        """.trimIndent()

        val variables = mapOf(
            "input" to json.encodeToJsonElement(mapOf("email" to email, "password" to pass))
        )

        return try {
            val response: GraphQLResponse<LoginResponse> = client.post(BuildConfig.BASE_URL) {
                contentType(ContentType.Application.Json)
                setBody(GraphQLRequest(query, variables))
            }.body()

            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(Exception(response.errors.first().message))
            } else {
                val loginResult = response.data?.login
                if (loginResult?.token != null) {
                    Result.success(loginResult)
                } else {
                    Result.failure(Exception(loginResult?.message ?: "Login gagal"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<UserDto> {
        val query = """
            query WhoAmI {
              whoAmI {
                id, name, fname, phone, anual, chatId, numberExists,
                e164, national, country, address, email, createdAt,
                updatedAt, status, profilePicPath, profilePicId,
                role { id, type, option, desc }
              }
            }
        """.trimIndent()

        return try {
            val response: GraphQLResponse<WhoAmIResponse> = client.post(BuildConfig.BASE_URL) {
                addAuthHeader()
                contentType(ContentType.Application.Json)
                setBody(GraphQLRequest(query))
            }.body()

            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(Exception(response.errors.first().message))
            } else {
                val user = response.data?.whoAmI
                if (user != null) Result.success(user)
                else Result.failure(Exception("Not authenticated"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Boolean> {
        val query = "mutation Logout { logout }"
        return try {
            val response: GraphQLResponse<Map<String, Boolean>> = client.post(BuildConfig.BASE_URL) {
                addAuthHeader()
                contentType(ContentType.Application.Json)
                setBody(GraphQLRequest(query))
            }.body()

            cookieStorage.clear()

            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(Exception(response.errors.first().message))
            } else {
                Result.success(response.data?.get("logout") ?: true)
            }
        } catch (e: Exception) {
            cookieStorage.clear()
            Result.failure(e)
        }
    }

    suspend fun updateFname(fname: String): Result<UserDto> {
        val query = "mutation UpdateFname(${'$'}fname: String!) { updateFname(fname: ${'$'}fname) { id fname } }"
        return performMutation<UpdateFnameResponse>(query, mapOf("fname" to json.encodeToJsonElement(fname))) { it.updateFname }
    }

    suspend fun updateAddress(address: String): Result<UserDto> {
        val query = "mutation UpdateAddress(${'$'}address: String!) { updateAddress(address: ${'$'}address) { id address } }"
        return performMutation<UpdateAddressResponse>(query, mapOf("address" to json.encodeToJsonElement(address))) { it.updateAddress }
    }

    suspend fun updateBio(bio: String): Result<UserDto> {
        val query = "mutation UpdateBio(${'$'}bio: String!) { updateBio(bio: ${'$'}bio) { id bio } }"
        return performMutation<UpdateBioResponse>(query, mapOf("bio" to json.encodeToJsonElement(bio))) { it.updateBio }
    }

    suspend fun updateLanguage(language: Int): Result<UserDto> {
        val query = "mutation UpdateLanguage(${'$'}language: Int!) { updateLanguage(language: ${'$'}language) { id language name message } }"
        return performMutation<UpdateLanguageResponse>(query, mapOf("language" to json.encodeToJsonElement(language))) { it.updateLanguage }
    }

    suspend fun updateAvatar(avatarUuid: String): Result<UserDto> {
        val query = "mutation UpdateAvatar(${'$'}avatar: String!) { updateAvatar(avatar: ${'$'}avatar) { id avatar } }"
        return performMutation<UpdateAvatarResponse>(query, mapOf("avatar" to json.encodeToJsonElement(avatarUuid))) { it.updateAvatar }
    }

    suspend fun uploadTempFile(bytes: ByteArray, fileName: String): Result<String> {
        return try {
            val baseUrl = BuildConfig.BASE_URL.replace("/graphql", "")
            val response: UploadResponse = client.post("${baseUrl}/upload/temp") {
                addAuthHeader()
                setBody(MultiPartFormDataContent(
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        })
                    }
                ))
            }.body()
            Result.success(response.uuid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend inline fun <reified T> performMutation(
        query: String,
        variables: Map<String, JsonElement>,
        crossinline selector: (T) -> UserDto?
    ): Result<UserDto> {
        return try {
            val response: GraphQLResponse<T> = client.post(BuildConfig.BASE_URL) {
                addAuthHeader()
                contentType(ContentType.Application.Json)
                setBody(GraphQLRequest(query, variables))
            }.body()

            if (response.errors != null && response.errors.isNotEmpty()) {
                Result.failure(Exception(response.errors.first().message))
            } else {
                val user = response.data?.let { selector(it) }
                if (user != null) Result.success(user)
                else Result.failure(Exception("Operation failed: Data is null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
