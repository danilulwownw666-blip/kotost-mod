package com.tiktok.webmod.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object VideoDownloader {
    private const val TAG = "VideoDownloader"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun downloadVideo(
        context: Context,
        videoUrl: String,
        userAgent: String,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (videoUrl.isBlank()) {
            val msg = "Ссылка на видео не найдена на текущей странице"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onError(msg)
            return
        }

        val filename = "TikTok_${System.currentTimeMillis()}.mp4"

        try {
            // Attempt standard Android DownloadManager first
            val cookies = CookieManager.getInstance().getCookie(videoUrl) ?: ""
            val request = DownloadManager.Request(Uri.parse(videoUrl)).apply {
                setTitle("TikTok Video")
                setDescription("Скачивание видео через TikTok WebMod")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", userAgent)
                if (cookies.isNotEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }
                addRequestHeader("Referer", "https://www.tiktok.com/")
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                dm.enqueue(request)
                val msg = "Загрузка началась: $filename (в папку Downloads)"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onSuccess(filename)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "DownloadManager failed, trying OkHttp fallback: ${e.message}")
        }

        // Fallback to OkHttp direct download into Downloads directory
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cookies = CookieManager.getInstance().getCookie(videoUrl) ?: ""
                val request = Request.Builder()
                    .url(videoUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", "https://www.tiktok.com/")
                    .apply {
                        if (cookies.isNotEmpty()) header("Cookie", cookies)
                    }
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        val err = "Ошибка сервера: HTTP ${response.code}"
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        onError(err)
                    }
                    return@launch
                }

                val body = response.body ?: return@launch
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val targetFile = File(downloadsDir, filename)
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    val msg = "Видео успешно сохранено: ${targetFile.name}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onSuccess(targetFile.absolutePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "OkHttp download error", e)
                withContext(Dispatchers.Main) {
                    val msg = "Ошибка при загрузке: ${e.localizedMessage ?: "Неизвестная ошибка"}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onError(msg)
                }
            }
        }
    }
}
