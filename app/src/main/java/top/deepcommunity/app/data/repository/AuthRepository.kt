package top.deepcommunity.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.core.network.PersistentCookieJar
import top.deepcommunity.app.core.network.TokenManager
import top.deepcommunity.app.data.api.AuthApi
import top.deepcommunity.app.data.model.ErrorResponse
import top.deepcommunity.app.data.model.LoginRequest
import top.deepcommunity.app.data.model.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    private val cookieJar: PersistentCookieJar,
    private val json: Json
) {
    val isLoggedIn = tokenManager.isLoggedIn

    fun getSavedUser(): UserDto? {
        val userJson = tokenManager.getCurrentUserJson() ?: return null
        return try {
            json.decodeFromString<UserDto>(userJson)
        } catch (e: Exception) {
            null
        }
    }

    fun login(usernameOrEmail: String, password: String): Flow<Resource<UserDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = authApi.login(
                LoginRequest(usernameOrEmail = usernameOrEmail, password = password)
            )

            if (response.isSuccessful && response.body() != null) {
                val token = response.body()!!.accessToken
                tokenManager.saveAccessToken(token)

                // 登录成功后紧接着获取当前个人资料
                val userResponse = authApi.getCurrentUser()
                if (userResponse.isSuccessful && userResponse.body() != null) {
                    val user = userResponse.body()!!
                    tokenManager.saveCurrentUserJson(json.encodeToString(user))
                    emit(Resource.Success(user))
                } else {
                    emit(Resource.Error("获取用户信息失败"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val message = try {
                    if (!errorBody.isNullOrBlank()) {
                        json.decodeFromString<ErrorResponse>(errorBody).detail ?: "登录失败 (${response.code()})"
                    } else "登录失败 (${response.code()})"
                } catch (e: Exception) {
                    "账号或密码错误"
                }
                emit(Resource.Error(message, response.code()))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络连接异常，请重试"))
        }
    }.flowOn(Dispatchers.IO)

    fun fetchCurrentUser(): Flow<Resource<UserDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = authApi.getCurrentUser()
            if (response.isSuccessful && response.body() != null) {
                val user = userResponseOrDefault(response.body()!!)
                tokenManager.saveCurrentUserJson(json.encodeToString(user))
                emit(Resource.Success(user))
            } else {
                emit(Resource.Error("获取个人资料失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    private fun userResponseOrDefault(user: UserDto): UserDto = user

    fun logout(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            authApi.logout()
        } catch (_: Exception) {
            // 忽略网络断开时的注销报错，直接清理本地
        } finally {
            tokenManager.clearAll()
            cookieJar.clearAllCookies()
            emit(Resource.Success(Unit))
        }
    }.flowOn(Dispatchers.IO)
}
