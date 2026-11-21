package top.deepcommunity.app.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import top.deepcommunity.app.data.model.BattleListDto
import top.deepcommunity.app.data.model.BattleMatchDto
import top.deepcommunity.app.data.model.BattleMoveRequest
import top.deepcommunity.app.data.model.CreateBattleRequest
import top.deepcommunity.app.data.model.MessageResponse

interface BattleApi {

    @GET("api/battles/me")
    suspend fun getMyBattles(): Response<BattleListDto>

    @POST("api/battles")
    suspend fun createBattle(@Body request: CreateBattleRequest): Response<BattleMatchDto>

    @GET("api/battles/{id}")
    suspend fun getBattleDetail(@Path("id") matchId: String): Response<BattleMatchDto>

    @POST("api/battles/{id}/accept")
    suspend fun acceptBattle(@Path("id") matchId: String): Response<BattleMatchDto>

    @POST("api/battles/{id}/reject")
    suspend fun rejectBattle(@Path("id") matchId: String): Response<MessageResponse>

    @POST("api/battles/{id}/move")
    suspend fun move(
        @Path("id") matchId: String,
        @Body request: BattleMoveRequest
    ): Response<BattleMatchDto>

    @POST("api/battles/{id}/surrender")
    suspend fun surrender(@Path("id") matchId: String): Response<BattleMatchDto>
}
