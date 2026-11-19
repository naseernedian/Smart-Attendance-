package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface AttendanceApiService {

    @POST("api/v1/auth/register")
    suspend fun registerAdmin(@Body request: AdminRegistrationRequest): Response<AdminRegistrationResponse>

    @POST("api/v1/auth/login")
    suspend fun loginAdmin(@Body request: AdminLoginRequest): Response<AdminLoginResponse>

    @POST("api/v1/auth/verify-face")
    suspend fun verifyAdminFace(@Body request: AdminFaceVerifyRequest): Response<AdminFaceVerifyResponse>

    @POST("api/v1/auth/password-reset/direct")
    suspend fun resetPasswordDirect(@Body request: DirectPasswordResetRequestDto): Response<AdminRegistrationResponse>

    @POST("api/v1/sync/students")
    suspend fun syncStudents(
        @Header("Authorization") bearerToken: String,
        @Body request: StudentBatchSyncRequest
    ): Response<StudentBatchSyncResponse>

    @GET("api/v1/sync/students")
    suspend fun fetchRemoteStudents(
        @Header("Authorization") bearerToken: String
    ): Response<List<StudentSyncItemDto>>

    @POST("api/v1/sync/attendance")
    suspend fun syncAttendance(
        @Header("Authorization") bearerToken: String,
        @Body request: AttendanceBatchSyncRequest
    ): Response<AttendanceBatchSyncResponse>

    @GET("api/v1/sync/attendance")
    suspend fun fetchRemoteAttendance(
        @Header("Authorization") bearerToken: String
    ): Response<List<AttendanceSyncItemDto>>

    companion object {
        fun create(baseUrl: String): AttendanceApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val safeBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return Retrofit.Builder()
                .baseUrl(safeBaseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(AttendanceApiService::class.java)
        }
    }
}
