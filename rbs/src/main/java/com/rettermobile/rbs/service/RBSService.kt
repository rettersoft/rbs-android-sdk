package com.rettermobile.rbs.service

import com.rettermobile.rbs.service.model.RBSTokenResponse
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

/**
 * Created by semihozkoroglu on 22.11.2020.
 */
interface RBSService {

    @GET("public/anonymous-auth")
    suspend fun anonymousAuth(@Query("projectId") projectId: String): RBSTokenResponse

    @GET("public/auth-refresh")
    suspend fun authRefresh(@Query("refreshToken") refreshToken: String): RBSTokenResponse

    @GET("public/auth")
    suspend fun auth(@Query("customToken") customToken: String): RBSTokenResponse

    @GET("user/action/{projectId}/{action}")
    suspend fun getAction(
        @HeaderMap headers: Map<String, String>,
        @Path("projectId") projectId: String, @Path("action") action: String,
        @Query("auth") auth: String, @Query(value = "data") data: String,
        @Query(value = "culture") culture: String? = null
    ): ResponseBody

    @POST("user/action/{projectId}/{action}")
    suspend fun postAction(
        @HeaderMap headers: Map<String, String>,
        @Path("projectId") projectId: String, @Path("action") action: String,
        @Query("auth") auth: String, @Body params: RequestBody,
        @Query(value = "culture") culture: String? = null
    ): ResponseBody
}