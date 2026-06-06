package com.example.portalphotoframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

object ImageManager {

    private const val IMAGES_DIR = "images"
    private const val THUMB_DIR = "thumbnails"
    private const val MAX_DIMENSION = 2048

    private fun getImagesDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), IMAGES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getThumbDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), THUMB_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getImageFiles(context: Context): List<File> {
        val dir = getImagesDir(context)
        return dir.listFiles { file ->
            file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        }?.sortedBy { it.lastModified() } ?: emptyList()
    }

    fun getImageFile(context: Context, name: String): File? {
        val sanitized = File(name).name // prevent path traversal
        val file = File(getImagesDir(context), sanitized)
        return if (file.exists() && file.isFile) file else null
    }

    fun getThumbnail(context: Context, name: String): File? {
        val sanitized = File(name).name
        val thumbFile = File(getThumbDir(context), sanitized)
        if (thumbFile.exists()) return thumbFile

        val original = getImageFile(context, sanitized) ?: return null
        try {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(original.absolutePath, options) ?: return null
            val scaled = scaleBitmap(bitmap, 300)
            FileOutputStream(thumbFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            return thumbFile
        } catch (e: Exception) {
            return null
        }
    }

    fun saveImageFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            saveImageFromStream(context, inputStream)
        } catch (e: Exception) {
            null
        }
    }

    fun saveImageFromStream(context: Context, inputStream: InputStream): String? {
        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val destFile = File(getImagesDir(context), fileName)

            // Save raw bytes to a temp file first to avoid holding everything in memory
            val tempFile = File.createTempFile("upload", null, context.cacheDir)
            tempFile.outputStream().use { out -> inputStream.copyTo(out) }
            inputStream.close()

            // Check for duplicates by file hash
            val hash = hashFile(tempFile)
            if (hash != null && isDuplicate(context, tempFile, hash)) {
                tempFile.delete()
                return "DUPLICATE"
            }

            // Decode bounds only (no memory allocated for pixels)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                tempFile.delete()
                return null
            }

            // Calculate inSampleSize to downsample during decode
            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION)

            // Decode with subsampling
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOpts)
            if (bitmap == null) {
                tempFile.delete()
                return null
            }

            // Read EXIF orientation
            try {
                val exif = ExifInterface(tempFile.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                bitmap = rotateBitmap(bitmap, orientation)
            } catch (_: Exception) {}

            tempFile.delete()

            // Final scale if still too large after subsampling
            if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
                val old = bitmap
                bitmap = scaleBitmap(bitmap, MAX_DIMENSION)
                if (old != bitmap) old.recycle()
            }

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            fileName
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxDim || h / 2 >= maxDim) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    private fun hashFile(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun isDuplicate(context: Context, newFile: File, newHash: String): Boolean {
        val existingFiles = getImageFiles(context)
        val newSize = newFile.length()
        for (existing in existingFiles) {
            // Quick size check first
            if (existing.length() != newSize) continue
            val existingHash = hashFile(existing)
            if (existingHash == newHash) return true
        }
        return false
    }

    fun saveImageBytes(context: Context, bytes: ByteArray, originalName: String?): String? {
        return try {
            val ext = originalName?.substringAfterLast('.', "jpg")?.lowercase() ?: "jpg"
            val safeExt = if (ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")) ext else "jpg"
            val fileName = "${UUID.randomUUID()}.$safeExt"
            val destFile = File(getImagesDir(context), fileName)

            // Write to temp file first, then decode with subsampling
            val tempFile = File.createTempFile("upload_bytes", null, context.cacheDir)
            tempFile.writeBytes(bytes)

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOpts)
            if (boundsOpts.outWidth <= 0) { tempFile.delete(); return null }

            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOpts)
            tempFile.delete()
            if (bitmap == null) return null

            if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
                val old = bitmap
                bitmap = scaleBitmap(bitmap, MAX_DIMENSION)
                if (old != bitmap) old.recycle()
            }

            FileOutputStream(destFile).use { out ->
                val format = if (safeExt == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                bitmap.compress(format, 85, out)
            }
            bitmap.recycle()
            fileName
        } catch (e: Exception) {
            null
        }
    }

    fun deleteImage(context: Context, name: String): Boolean {
        val sanitized = File(name).name
        val file = File(getImagesDir(context), sanitized)
        val thumb = File(getThumbDir(context), sanitized)
        thumb.delete()
        return file.delete()
    }

    fun deleteAllImages(context: Context): Int {
        val files = getImageFiles(context)
        val thumbDir = getThumbDir(context)
        thumbDir.listFiles()?.forEach { it.delete() }
        var count = 0
        files.forEach { if (it.delete()) count++ }
        return count
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        if (ratio >= 1f) return bitmap
        val w = (bitmap.width * ratio).toInt()
        val h = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
