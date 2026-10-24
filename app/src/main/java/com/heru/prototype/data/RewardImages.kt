package com.heru.prototype.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Bounds are read first: a full camera image must never be decoded on the UI thread. */
fun readRewardImage(context: Context, uri: String, maxEdge: Int = 1024): Bitmap? {
    val parsed = Uri.parse(uri)
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxEdge) sample *= 2
    val bitmap = resolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val orientation = runCatching {
        resolver.openInputStream(parsed)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } ?: 1
    }.getOrDefault(1)
    val matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(270f); postScale(-1f, 1f) }
            8 -> setRotate(270f)
        }
    }
    return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { bitmap.recycle() }
}

suspend fun importRewardImage(context: Context, uri: String): String = withContext(Dispatchers.IO) {
    val bitmap = requireNotNull(readRewardImage(context, uri)) { "사진을 열 수 없어요" }
    val directory = File(context.filesDir, "reward-images").apply { mkdirs() }
    val destination = File(directory, "${UUID.randomUUID()}.jpg")
    try { destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) } }
    finally { bitmap.recycle() }
    Uri.fromFile(destination).toString()
}
