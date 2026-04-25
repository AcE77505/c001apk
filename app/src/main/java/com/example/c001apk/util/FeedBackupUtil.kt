package com.example.c001apk.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.c001apk.logic.model.FeedContentResponse
import com.example.c001apk.logic.model.HomeFeedResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
        data.picArr?.filter { it.isNotBlank() }?.forEach { list.add(it) }
        data.replyRows?.forEach { reply ->
            reply.picArr?.filter { it.isNotBlank() }?.forEach { list.add(it) }
            reply.pic?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        }
        data.messageCover?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        data.coverPic?.takeIf { it.isNotBlank() }?.let { list.add(it) }
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
            val zipFile = root.createFile(ZIP_MIME, zipName)
                ?: throw IllegalStateException("创建图片备份压缩包失败")
            context.contentResolver.openOutputStream(zipFile.uri)?.use { output ->
                ZipOutputStream(output).use { zos ->
                    imageUrls.forEachIndexed { index, url ->
                        val bytes = downloadBytes(url)
                        val ext = guessExt(url)
                        val entry = ZipEntry("image_${index + 1}.$ext")
                        entry.method = ZipEntry.STORED
                        entry.size = bytes.size.toLong()
                        entry.compressedSize = bytes.size.toLong()
                        val crc32 = CRC32().apply { update(bytes) }
                        entry.crc = crc32.value
                        zos.putNextEntry(entry)
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("写入图片备份压缩包失败")
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        return URL(url).openStream().use { input ->
            val baos = ByteArrayOutputStream()
            input.copyTo(baos)
            baos.toByteArray()
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
}
