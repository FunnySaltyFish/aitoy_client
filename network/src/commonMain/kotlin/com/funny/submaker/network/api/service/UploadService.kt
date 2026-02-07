package com.funny.submaker.network.api.service

import com.funny.submaker.network.DynamicTimeout
import com.funny.submaker.network.api.ApiException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.HttpException
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Url

interface UploadService {
    @Multipart
    @POST
    @DynamicTimeout(readTimeout = 180, writeTimeout = 180)
    suspend fun uploadFile(
        @Url uploadUrl: String,
        @PartMap formFields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part file: MultipartBody.Part,
    ): Response<Unit>
}

suspend fun UploadService.uploadFileToTicket(
    uploadHost: String,
    formFields: Map<String, String>,
    fileName: String,
    contentType: String?,
    bytes: ByteArray,
) {
    val formBodies = formFields.mapValues { (_, value) ->
        value.toRequestBody("text/plain".toMediaType())
    }
    val filePart = MultipartBody.Part.createFormData(
        name = "file",
        filename = fileName,
        body = bytes.toRequestBody((contentType ?: "application/octet-stream").toMediaType()),
    )

    val response = try {
        uploadFile(
            uploadUrl = uploadHost,
            formFields = formBodies,
            file = filePart,
        )
    } catch (e: HttpException) {
        throw ApiException(9501, "上传音频失败（${e.code()}）", e)
    } catch (e: Throwable) {
        throw ApiException(9501, e.message ?: "上传音频失败", e)
    }

    if (!response.isSuccessful) {
        val message = response.errorBody()?.string().orEmpty()
        throw ApiException(9501, "上传音频失败（${response.code()}）：$message")
    }
}
