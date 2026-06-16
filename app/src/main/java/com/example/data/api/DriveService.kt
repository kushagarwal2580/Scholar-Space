package com.example.data.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Query
import kotlinx.serialization.Serializable

@Serializable
data class DriveAboutResponse(
    val storageQuota: StorageQuota? = null
)

@Serializable
data class StorageQuota(
    val limit: String? = null,
    val usage: String? = null,
    val usageInDrive: String? = null,
    val usageInDriveTrash: String? = null
)

@Serializable
data class DriveFile(
    val id: String? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val parents: List<String>? = null,
    val size: String? = null,
    val createdTime: String? = null
)

@Serializable
data class DriveFileList(
    val files: List<DriveFile>? = null
)

interface DriveService {
    @GET("drive/v3/about")
    suspend fun getAbout(
        @Header("Authorization") authHeader: String,
        @Query("fields") fields: String = "storageQuota"
    ): DriveAboutResponse

    @POST("drive/v3/files")
    suspend fun createFolder(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Body fileMetadata: Map<String, String>
    ): DriveFile

    @POST("drive/v3/files")
    suspend fun createDriveFile(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Body metadata: DriveFile
    ): DriveFile

    @GET("drive/v3/files")
    suspend fun searchFiles(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id, name, mimeType, parents, size, createdTime)"
    ): DriveFileList

    @retrofit2.http.Multipart
    @POST("upload/drive/v3/files?uploadType=multipart")
    suspend fun uploadFile(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Part metadata: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): DriveFile

    @retrofit2.http.Multipart
    @retrofit2.http.PATCH("upload/drive/v3/files/{fileId}?uploadType=multipart")
    suspend fun updateFileContent(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("fileId") fileId: String,
        @retrofit2.http.Part metadata: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): DriveFile

    @retrofit2.http.DELETE("drive/v3/files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("fileId") fileId: String
    ): retrofit2.Response<Unit>

    @retrofit2.http.PATCH("drive/v3/files/{fileId}")
    suspend fun updateDriveFile(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("fileId") fileId: String,
        @Query("addParents") addParents: String? = null,
        @Query("removeParents") removeParents: String? = null,
        @retrofit2.http.Body fileMetadata: Map<String, String>? = null
    ): DriveFile

    @retrofit2.http.Streaming
    @GET("drive/v3/files/{fileId}?alt=media")
    suspend fun downloadFile(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Path("fileId") fileId: String
    ): okhttp3.ResponseBody
}
