package com.aura.app.assistant

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class SupabaseAuthSession(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long? = null,
    val expires_at: Long? = null,
    val token_type: String? = null,
    val user: SupabaseAuthUser? = null
) {
    internal fun requireTokens(): SupabaseAuthSession {
        require(access_token.isNotBlank() && refresh_token.isNotBlank()) {
            "Supabase returned an incomplete session."
        }
        return this
    }
}

data class SupabaseAuthUser(
    val id: String? = null,
    val email: String? = null,
    @SerializedName("user_metadata") val userMetadata: SupabaseUserMetadata? = null
)

data class SupabaseUserMetadata(
    val name: String? = null,
    @SerializedName("full_name") val fullName: String? = null
)

data class SupabaseGoogleIdTokenRequest(
    val provider: String = "google",
    val id_token: String,
    val nonce: String? = null
)

data class SupabasePasswordRequest(val email: String, val password: String)

data class SupabaseSignupRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

internal interface SupabaseAuthApi {
    @POST("auth/v1/token?grant_type=id_token")
    suspend fun exchangeGoogleIdToken(
        @Header("apikey") publishableKey: String,
        @Body request: SupabaseGoogleIdTokenRequest
    ): SupabaseAuthSession

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(
        @Header("apikey") publishableKey: String,
        @Body request: SupabasePasswordRequest
    ): SupabaseAuthSession

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") publishableKey: String,
        @Body request: SupabaseSignupRequest
    ): SupabaseAuthSession

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refresh(
        @Header("apikey") publishableKey: String,
        @Body request: Map<String, String>
    ): SupabaseAuthSession

    @POST("auth/v1/logout?scope=local")
    suspend fun logout(
        @Header("apikey") publishableKey: String,
        @Header("Authorization") authorization: String
    )
}

internal interface SupabaseAuthTransport {
    suspend fun exchangeGoogleIdToken(idToken: String, nonce: String): SupabaseAuthSession
    suspend fun signInWithPassword(email: String, password: String): SupabaseAuthSession
    suspend fun signUp(email: String, password: String, name: String?): SupabaseAuthSession
    suspend fun refresh(refreshToken: String): SupabaseAuthSession
    suspend fun logout(accessToken: String)
}

internal class SupabaseAuthClient private constructor(
    private val api: SupabaseAuthApi,
    private val publishableKey: String
) : SupabaseAuthTransport {
    companion object {
        fun create(configuration: StalkyCloudConfiguration): SupabaseAuthClient {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return SupabaseAuthClient(
                api = Retrofit.Builder()
                    .baseUrl(configuration.supabaseUrl.trimEnd('/') + "/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(SupabaseAuthApi::class.java),
                publishableKey = configuration.publishableKey
            )
        }
    }

    override suspend fun exchangeGoogleIdToken(
        idToken: String,
        nonce: String
    ): SupabaseAuthSession = api.exchangeGoogleIdToken(
        publishableKey,
        SupabaseGoogleIdTokenRequest(id_token = idToken, nonce = nonce)
    ).requireTokens()

    override suspend fun signInWithPassword(
        email: String,
        password: String
    ): SupabaseAuthSession = api.signInWithPassword(
        publishableKey,
        SupabasePasswordRequest(email = email, password = password)
    ).requireTokens()

    override suspend fun signUp(
        email: String,
        password: String,
        name: String?
    ): SupabaseAuthSession = api.signUp(
        publishableKey,
        SupabaseSignupRequest(
            email = email,
            password = password,
            data = name?.takeIf { it.isNotBlank() }?.let { mapOf("full_name" to it) }
        )
    ).requireTokens()

    override suspend fun refresh(refreshToken: String): SupabaseAuthSession = api.refresh(
        publishableKey,
        mapOf("refresh_token" to refreshToken)
    ).requireTokens()

    override suspend fun logout(accessToken: String) {
        api.logout(publishableKey, "Bearer $accessToken")
    }
}
