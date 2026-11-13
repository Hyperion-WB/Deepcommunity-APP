package top.deepcommunity.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.data.api.UserApi
import top.deepcommunity.app.data.model.AchievementDto
import top.deepcommunity.app.data.model.BlockedUserDto
import top.deepcommunity.app.data.model.CheckinStatusDetailDto
import top.deepcommunity.app.data.model.FollowUserItemDto
import top.deepcommunity.app.data.model.PostItemDto
import top.deepcommunity.app.data.model.PublicUserProfileDto
import top.deepcommunity.app.data.model.UpdateDndRequest
import top.deepcommunity.app.data.model.UpdatePrivacyRequest
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

    fun getCheckinStatus(): Flow<Resource<CheckinStatusDetailDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.getCheckinStatus()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("获取签到数据失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun makeupCheckin(): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.makeupCheckin()
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "补签成功！"))
            } else {
                emit(Resource.Error("补签失败或次数已用尽"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getMyAchievements(): Flow<Resource<List<AchievementDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.getMyAchievements()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("获取勋章成就失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 粉丝与关注列表
    fun getFollowers(username: String): Flow<Resource<List<FollowUserItemDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.getFollowers(username)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载粉丝列表失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun getFollowing(username: String): Flow<Resource<List<FollowUserItemDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.getFollowing(username)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载关注列表失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 他人公开个人主页
    fun getUserPublicProfile(username: String): Flow<Resource<PublicUserProfileDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.getUserPublicProfile(username)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("加载用户主页失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun followUser(username: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.followUser(username)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "已关注"))
            } else {
                emit(Resource.Error("关注失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun unfollowUser(username: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.unfollowUser(username)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "已取消关注"))
            } else {
                emit(Resource.Error("取消关注失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    // 隐私与免打扰
    fun updatePrivacy(request: UpdatePrivacyRequest): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.updatePrivacy(request)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "隐私设置已更新"))
            } else {
                emit(Resource.Error("更新失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun updateDnd(request: UpdateDndRequest): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.updateDnd(request)
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "免打扰设置已更新"))
            } else {
                emit(Resource.Error("更新失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun updatePrivacy(hidePosts: Boolean, hideCollections: Boolean): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.updatePrivacy(UpdatePrivacyRequest(hidePosts, hideCollections))
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "隐私设置已更新"))
            } else {
                emit(Resource.Error("更新失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    fun updateDnd(dndEnabled: Boolean, dndStart: String, dndEnd: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = userApi.updateDnd(UpdateDndRequest(dndEnabled, dndStart, dndEnd))
            if (response.isSuccessful) {
                emit(Resource.Success(response.body()?.message ?: "免打扰设置已更新"))
            } else {
                emit(Resource.Error("更新失败"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)
}
