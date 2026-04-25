package com.example.c001apk.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.c001apk.logic.model.FeedContentResponse
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.TotalReplyResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FeedBackupUtil {

    private const val JSON_MIME = "application/json"
    private const val ZIP_MIME = "application/zip"

    fun hasBackupPath(): Boolean = PrefManager.backupTreeUri.isNotEmpty()

    fun collectImageUrls(data: HomeFeedResponse.Data?): List<String> {
        if (data == null) return emptyList()
        val list = linkedSetOf<String>()

        data.picArr.orEmpty().forEach { addUrl(list, it) }
        addUrl(list, data.pic)
        addUrl(list, data.messageCover)
        addUrl(list, data.coverPic)
        addUrl(list, data.userAvatar)
        addUrl(list, data.userInfo?.userAvatar)
        addUrl(list, data.fUserInfo?.userAvatar)

        data.replyRows.orEmpty().forEach { reply ->
            reply.picArr.orEmpty().forEach { addUrl(list, it) }
            addUrl(list, reply.pic)
            addUrl(list, reply.userInfo?.userAvatar)
        }

        data.topReplyRows.orEmpty().forEach { collectTotalReplyUrls(list, it) }
        data.replyMeRows.orEmpty().forEach { collectTotalReplyUrls(list, it) }

        return list.toList()
    }

    fun buildBaseName(fid: String, keepBoth: Boolean): String {
        val prefix = "feed_${fid.trim()}"
        return if (keepBoth) "${prefix}_${System.currentTimeMillis()}" else prefix
    }

    suspend fun backupToSaf(
        context: Context,
        treeUri: Uri,
        baseName: String,
        feedContent: FeedContentResponse,
        imageUrls: List<String>,
        replace: Boolean
    ) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("无法访问备份目录")

        val jsonName = "$baseName.json"
        val zipName = "$baseName.zip"

        if (replace) {
            root.findFile(jsonName)?.delete()
            root.findFile(zipName)?.delete()
        }

        val jsonFile = root.createFile(JSON_MIME, jsonName)
            ?: throw IllegalStateException("创建 JSON 备份文件失败")

        context.contentResolver.openOutputStream(jsonFile.uri)?.use { output ->
            output.writer().use { writer ->
                writer.write(Gson().toJson(feedContent))
            }
        } ?: throw IllegalStateException("写入 JSON 备份失败")

        if (imageUrls.isNotEmpty()) {
            val downloaded = imageUrls.mapIndexed { index, url ->
                val bytes = try {
                    downloadBytes(url)
                } catch (e: Exception) {
                    throw IllegalStateException("下载图片失败，请检查网络或图片链接", e)
                }
                DownloadedImage("image_${index + 1}.${guessExt(url)}", bytes)
            }

            val zipFile = root.createFile(ZIP_MIME, zipName)
                ?: throw IllegalStateException("创建图片备份压缩包失败")
            context.contentResolver.openOutputStream(zipFile.uri)?.use { output ->
                ZipOutputStream(output).use { zos ->
                    downloaded.forEach { image ->
                        val entry = ZipEntry(image.fileName)
                        entry.method = ZipEntry.STORED
                        entry.size = image.bytes.size.toLong()
                        entry.compressedSize = image.bytes.size.toLong()
                        val crc32 = CRC32().apply { update(image.bytes) }
                        entry.crc = crc32.value
                        zos.putNextEntry(entry)
                        zos.write(image.bytes)
                        zos.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("写入图片备份压缩包失败")
        }
    }


    private fun collectTotalReplyUrls(set: MutableSet<String>, reply: TotalReplyResponse.Data?) {
        if (reply == null) return
        reply.picArr.orEmpty().forEach { addUrl(set, it) }
        addUrl(set, reply.pic)
        addUrl(set, reply.userAvatar)
        reply.replyRows.orEmpty().forEach { child ->
            collectTotalReplyUrls(set, child)
        }
    }

    private fun addUrl(set: MutableSet<String>, raw: String?) {
        if (raw.isNullOrBlank()) return
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { part ->
                normalizeUrl(part)?.let { set.add(it) }
            }
    }

    private fun normalizeUrl(raw: String): String? {
        val value = raw.removeSurrounding("\"").trim()
        if (value.isBlank()) return null
        return when {
            value.startsWith("https://") -> value
            value.startsWith("http://") -> value.http2https
            value.startsWith("//") -> "https:$value"
            value.startsWith("image.coolapk.com") -> "https://$value"
            value.startsWith("/") -> "https://image.coolapk.com$value"
            else -> "https://$value"
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", PrefManager.USER_AGENT)
        connection.setRequestProperty("Accept", "image/*,*/*;q=0.8")
        return try {
            connection.inputStream.use { input ->
                val baos = ByteArrayOutputStream()
                input.copyTo(baos)
                baos.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun guessExt(url: String): String {
        val clean = url.substringBefore('?').lowercase()
        return when {
            clean.endsWith(".png") -> "png"
            clean.endsWith(".webp") -> "webp"
            clean.endsWith(".gif") -> "gif"
            clean.endsWith(".bmp") -> "bmp"
            else -> "jpg"
        }
    }

    private data class DownloadedImage(
        val fileName: String,
        val bytes: ByteArray,
    )
}
