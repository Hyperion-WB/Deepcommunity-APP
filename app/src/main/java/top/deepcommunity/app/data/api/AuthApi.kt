package top.deepcommunity.app.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import top.deepcommunity.app.data.model.LoginRequest
import top.deepcommunity.app.data.model.LoginResponse
import top.deepcommunity.app.data.model.MessageResponse
import top.deepcommunity.app.data.model.UserDto

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/refresh")
    suspend fun refresh(): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<MessageResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<UserDto>
}
