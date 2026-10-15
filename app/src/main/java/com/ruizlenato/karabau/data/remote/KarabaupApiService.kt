package com.ruizlenato.karabau.data.remote

import com.ruizlenato.karabau.data.model.ExchangeKeyRequest
import com.ruizlenato.karabau.data.model.ExchangeKeyResponse
import com.ruizlenato.karabau.data.model.HealthCheckResponse
import com.ruizlenato.karabau.data.model.CreateBookmarkRequest
import com.ruizlenato.karabau.data.model.CreateListRequest
import com.ruizlenato.karabau.data.model.EditListRequest
import com.ruizlenato.karabau.data.model.ListBookmarkRequest
import com.ruizlenato.karabau.data.model.ListIdRequest
import com.ruizlenato.karabau.data.model.UpdateTagsRequest
import com.ruizlenato.karabau.data.model.DeleteBookmarkRequest
import com.ruizlenato.karabau.data.model.RevokeKeyRequest
import com.ruizlenato.karabau.data.model.UpdateBookmarkRequest
import com.ruizlenato.karabau.data.model.ValidateKeyRequest
import com.ruizlenato.karabau.data.model.ValidateKeyResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

interface KarabauApiService {

    @POST("api/trpc/apiKeys.exchange")
    suspend fun exchangeKey(
        @Body request: TrpcInput<ExchangeKeyRequest>
    ): Response<TrpcResponse<ExchangeKeyResponse>>

    @POST("api/trpc/apiKeys.validate")
    suspend fun validateKey(
        @Body request: TrpcInput<ValidateKeyRequest>
    ): Response<TrpcResponse<ValidateKeyResponse>>

    @POST("api/trpc/apiKeys.revoke")
    suspend fun revokeKey(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<RevokeKeyRequest>
    ): Response<TrpcResponse<Unit>>

    @POST("api/trpc/bookmarks.createBookmark")
    suspend fun createBookmark(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<CreateBookmarkRequest>
    ): Response<ResponseBody>

    @POST("api/trpc/bookmarks.updateBookmark")
    suspend fun updateBookmark(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<UpdateBookmarkRequest>
    ): Response<ResponseBody>

    @POST("api/trpc/bookmarks.deleteBookmark")
    suspend fun deleteBookmark(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<DeleteBookmarkRequest>
    ): Response<ResponseBody>

    @POST("api/trpc/bookmarks.updateTags")
    suspend fun updateBookmarkTags(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<UpdateTagsRequest>
    ): Response<ResponseBody>

    @GET("api/trpc/bookmarks.checkUrl")
    suspend fun checkUrl(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @POST("api/trpc/lists.create")
    suspend fun createList(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<CreateListRequest>
    ): Response<ResponseBody>

    @POST("api/trpc/lists.edit")
    suspend fun editList(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<EditListRequest>
    ): Response<ResponseBody>

    @POST("api/trpc/lists.delete")
    suspend fun deleteList(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<ListIdRequest>
    ): Response<ResponseBody>

    @POST("api/trpc/lists.addToList")
    suspend fun addToList(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<ListBookmarkRequest>
    ): Response<ResponseBody>

    @POST("api/trpc/lists.removeFromList")
    suspend fun removeFromList(
        @Header("Authorization") auth: String,
        @Body request: TrpcInput<ListBookmarkRequest>
    ): Response<ResponseBody>

    @POST("api/trpc/users.updateSettings")
    suspend fun updateUserSettings(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Body request: TrpcBatchInput<Map<String, String>>
    ): Response<ResponseBody>

    @GET("api/trpc/users.settings")
    suspend fun getUserSettings(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/bookmarks.getBookmarks")
    suspend fun getBookmarks(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/users.whoami")
    suspend fun whoAmI(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/tags.list")
    suspend fun listTags(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/tags.get")
    suspend fun getTag(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/lists.list")
    suspend fun listLists(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/trpc/lists.get")
    suspend fun getList(
        @Header("Authorization") auth: String,
        @Query("batch") batch: String,
        @Query("input") input: String
    ): Response<ResponseBody>

    @GET("api/health")
    suspend fun healthCheck(): Response<HealthCheckResponse>

}

data class TrpcInput<T>(
    val json: T
)

data class TrpcBatchInput<T>(
    @SerializedName("0")
    val zero: TrpcInput<T>
)

data class TrpcResponse<T>(
    val result: TrpcResult<T>? = null,
    val error: TrpcError? = null
)

data class TrpcResult<T>(
    val data: TrpcResultData<T>? = null
)

data class TrpcResultData<T>(
    val json: T? = null
)

data class TrpcError(
    val code: String,
    val message: String? = null
)

