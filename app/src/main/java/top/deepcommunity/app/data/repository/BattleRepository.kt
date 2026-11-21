package top.deepcommunity.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.data.api.BattleApi
import top.deepcommunity.app.data.model.BattleMatchDto
import top.deepcommunity.app.data.model.BattleMoveRequest
import top.deepcommunity.app.data.model.CreateBattleRequest
import top.deepcommunity.app.data.model.ErrorResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BattleRepository @Inject constructor(
    private val battleApi: BattleApi,
    private val json: Json
) {

    fun getMyBattles(): Flow<Resource<List<BattleMatchDto>>> = flow {
        emit(Resource.Loading)
        try {
            val response = battleApi.getMyBattles()
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.items))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "获取对战列表失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun createBattle(gameCode: String, inviteeId: String): Flow<Resource<BattleMatchDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = battleApi.createBattle(CreateBattleRequest(gameCode, inviteeId))
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "发起对战失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun getBattleDetail(matchId: String): Flow<Resource<BattleMatchDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = battleApi.getBattleDetail(matchId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "获取对局失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun acceptBattle(matchId: String): Flow<Resource<BattleMatchDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = battleApi.acceptBattle(matchId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "接受对局失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun rejectBattle(matchId: String): Flow<Resource<String>> = flow {
        emit(Resource.Loading)
        try {
            val response = battleApi.rejectBattle(matchId)
            if (response.isSuccessful) {
                emit(Resource.Success("已拒绝该对战"))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "拒绝失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun move(matchId: String, expectedSeq: Int, x: Int, y: Int, fromX: Int? = null, fromY: Int? = null): Flow<Resource<BattleMatchDto>> = flow {
        emit(Resource.Loading)
        try {
            val req = BattleMoveRequest(expectedSeq = expectedSeq, x = x, y = y, fromX = fromX, fromY = fromY)
            val response = battleApi.move(matchId, req)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "落子失败"))
        }
    }.flowOn(Dispatchers.IO)

    fun surrender(matchId: String): Flow<Resource<BattleMatchDto>> = flow {
        emit(Resource.Loading)
        try {
            val response = battleApi.surrender(matchId)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "认输失败"))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseError(errorJson: String?): String {
        if (errorJson.isNullOrBlank()) return "请求失败"
        return try {
            val err = json.decodeFromString<ErrorResponse>(errorJson)
            err.detail ?: err.message ?: "操作失败"
        } catch (_: Exception) {
            "服务开小差了，请稍后重试"
        }
    }
}
