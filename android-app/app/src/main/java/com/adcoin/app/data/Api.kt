package com.adcoin.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AdCoinApi {

    @POST("api/auth/register")
    suspend fun register(@Body body: Map<String, String>): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): AuthResponse

    @GET("api/me")
    suspend fun me(@Header("Authorization") auth: String): MeResponse

    @POST("api/ad/claim")
    suspend fun claim(@Header("Authorization") auth: String, @Body body: Map<String, Any?>): ClaimResponse

    @POST("api/link/bind")
    suspend fun bind(@Header("Authorization") auth: String, @Body body: Map<String, String>): BindResponse

    @POST("api/link/unbind")
    suspend fun unbind(@Header("Authorization") auth: String): GenericResponse

    @GET("api/friend/search")
    suspend fun search(@Header("Authorization") auth: String, @Query("q") q: String): SearchResponse

    @POST("api/friend/{action}")
    suspend fun friend(
        @Header("Authorization") auth: String,
        @Path("action") action: String,
        @Body body: Map<String, Any?>,
    ): FriendActionResponse

    @GET("api/leaderboard")
    suspend fun leaderboard(@Header("Authorization") auth: String): LeaderboardResponse

    @GET("api/transactions")
    suspend fun transactions(@Header("Authorization") auth: String): TransactionsResponse

    @POST("api/transfer")
    suspend fun transfer(
        @Header("Authorization") auth: String,
        @Body body: Map<String, Any?>,
    ): TransferResponse
}
