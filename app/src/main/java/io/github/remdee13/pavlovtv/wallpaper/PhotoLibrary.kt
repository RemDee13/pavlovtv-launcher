package io.github.remdee13.pavlovtv.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/** "My photos": images uploaded from a phone or imported from TV storage / USB. */
class PhotoLibrary(private val context: Context) {
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val _photos = MutableStateFlow(scan())
    val photos: StateFlow<List<File>> = _photos

    private fun scan(): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "jpg" }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun delete(file: File) {
        if (file.parentFile == dir) file.delete()
        _photos.value = scan()
    }

    /** Decodes, shrinks to at most 1920 px on the long side and stores as JPEG. */
    suspend fun import(rotationDegrees: Int = 0, open: () -> InputStream?): File? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@withContext null
        val scale = min(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
        }
        val output = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (output !== decoded) output.recycle()
        decoded.recycle()
        _photos.value = scan()
        file
    }

    suspend fun importUri(uri: Uri, rotationDegrees: Int = 0): File? =
        import(rotationDegrees) { context.contentResolver.openInputStream(uri) }

    suspend fun importBytes(bytes: ByteArray): File? = import { bytes.inputStream() }

    companion object {
        const val MAX_SIDE = 1920
    }
}
