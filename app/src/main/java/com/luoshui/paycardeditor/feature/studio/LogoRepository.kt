package com.luoshui.paycardeditor.feature.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.roundToInt

internal data class LogoResource(
    val id: String,
    val name: String,
    val category: String,
    val group: String = "",
    val assetPath: String? = null,
    val localPath: String? = null,
    val note: String = "",
) {
    val imageModel: Any get() = localPath?.let(::File) ?: "file:///android_asset/$assetPath"
}

/** No broad storage permission; imported images become private app-owned copies. */
internal class LogoRepository(private val context: Context) {
    private val directory get() = File(context.filesDir, "personal_logos").apply { mkdirs() }

    fun list(): List<LogoResource> {
        val array = JSONArray(context.assets.open("logos/catalog.json").bufferedReader().use { it.readText() })
        val builtIn = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LogoResource(item.getString("id"), item.getString("name"), item.getString("category"),
                item.optString("group"), assetPath = item.getString("path"), note = item.optString("note"))
        }
        val personal = directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { metadata ->
            runCatching {
                val item = JSONObject(metadata.readText())
                val image = File(directory, metadata.nameWithoutExtension + ".png")
                if (!image.isFile) null else LogoResource(metadata.nameWithoutExtension,
                    item.getString("name"), "我的 Logo", localPath = image.absolutePath,
                    note = item.optString("note"))
            }.getOrNull()
        }
        return personal.sortedBy { it.name } + builtIn
    }

    fun load(resource: LogoResource): Bitmap {
        val source = resource.localPath?.let { ImageDecoder.createSource(ByteBuffer.wrap(File(it).readBytes())) }
            ?: ImageDecoder.createSource(context.assets, requireNotNull(resource.assetPath))
        return trimTransparent(decode(source, 1024))
    }

    fun import(uri: Uri): LogoResource {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            input.readNBytes(32 * 1024 * 1024 + 1)
        }
        require(bytes.size <= 32 * 1024 * 1024) { "Image exceeds 32 MiB" }
        val bitmap = trimTransparent(decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), 2048))
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.substringBeforeLast('.')?.trim()?.take(80)?.ifBlank { null } ?: "自定义 Logo"
        val id = UUID.randomUUID().toString()
        val image = File(directory, "$id.png")
        val metadata = File(directory, "$id.json")
        val note = if (!bitmap.hasAlpha() || !hasTransparentPixel(bitmap)) "原图有不透明背景，不会自动去底。" else ""
        try {
            image.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            metadata.writeText(JSONObject().put("name", name).put("note", note).toString())
        } catch (error: Exception) {
            image.delete()
            metadata.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
        return LogoResource(id, name, "我的 Logo", localPath = image.absolutePath, note = note)
    }

    companion object {
        fun decodeFile(file: File, maxEdge: Int): Bitmap = decode(ImageDecoder.createSource(ByteBuffer.wrap(file.readBytes())), maxEdge)

        fun decode(source: ImageDecoder.Source, maxEdge: Int): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val ratio = minOf(1f, maxEdge.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * ratio).roundToInt()), maxOf(1, (info.size.height * ratio).roundToInt()))
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
        }

        private fun hasTransparentPixel(bitmap: Bitmap): Boolean {
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                if (row.any { it ushr 24 != 255 }) return true
            }
            return false
        }

        fun trimTransparent(bitmap: Bitmap): Bitmap {
            var left = bitmap.width
            var top = bitmap.height
            var right = -1
            var bottom = -1
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (x in row.indices) if (row[x] ushr 24 != 0) {
                    left = minOf(left, x); right = maxOf(right, x)
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            if (right < left) { bitmap.recycle(); error("Empty transparent image") }
            if (left == 0 && top == 0 && right == bitmap.width - 1 && bottom == bitmap.height - 1) return bitmap
            return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1).also { bitmap.recycle() }
        }
    }
}
