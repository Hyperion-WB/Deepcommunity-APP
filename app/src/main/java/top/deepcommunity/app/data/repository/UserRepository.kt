package top.deepcommunity.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.data.api.UserApi
import top.deepcommunity.app.data.model.BlockedUserDto
import top.deepcommunity.app.data.model.PostItemDto
import top.deepcommunity.app.data.model.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userApi: UserApi
) {
    fun getMyPosts(page: Int = 1, size: Int = 20): Flow<Resource<List<PostItemDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.getMyPosts(page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载我的动态失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getMyCollections(page: Int = 1, size: Int = 20): Flow<Resource<List<PostItemDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.getMyCollections(page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载我的收藏失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun checkIn(): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.checkIn()
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "签到成功"))
            } else {
                emit(Resource.Error("今日已签到或签到失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "签到异常"))
        }
    }.flowOn(Dispatchers.IO)

    fun updateProfile(nickname: String, bio: String): Flow<Resource<UserDto>> = flow {
        emit(Resource.Loading)
        try {
            val map = mapOf("nickname" to nickname, "bio" to bio)
            val response = userApi.updateProfile(map)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("修改资料失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun updateAvatar(filePart: MultipartBody.Part): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.updateAvatar(filePart)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.finalUrl))
            } else {
                emit(Resource.Error("上传头像失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "上传失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun blockUser(username: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.blockUser(username)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.isBlocked))
            } else {
                emit(Resource.Error("操作失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun unblockUser(username: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.unblockUser(username)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.isBlocked))
            } else {
                emit(Resource.Error("操作失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getMyBlocks(): Flow<Resource<List<BlockedUserDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.getMyBlocks()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("获取黑名单失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)
}
