package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Photos are stored inside Firestore (a small thumbnail on the bottle, a larger copy in
 * photos/{id}) so they sync to every phone without needing Firebase Storage or a paid plan.
 */
object PhotoUtils {
    private const val TAG = "PhotoUtils"
    private const val FULL_MAX_PX = 1280
    private const val THUMB_MAX_PX = 200
    private const val FULL_MAX_BASE64 = 700_000

    /** Reads a photo from the camera or gallery, fixes its rotation and compresses it. Run off the main thread. */
    fun fromUri(context: Context, uri: Uri): PhotoData? = try {
        loadBitmap(context, uri, FULL_MAX_PX)?.let { fromBitmap(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't process photo $uri", e)
        null
    }

    /** Loads a photo at up to [maxPx] on its longest side, the right way up. Run off the main thread. */
    fun loadBitmap(context: Context, uri: Uri, maxPx: Int): Bitmap? = try {
        decodeSampled(context, uri, maxPx)?.let { rotateIfNeeded(context, uri, it) }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't load photo $uri", e)
        null
    }

    /** Used by the importer for photos saved by version 1 of the app. */
    fun fromFile(file: File): PhotoData? = try {
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) null else fromBitmap(bitmap)
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't process photo file $file", e)
        null
    }

    /** Downloads a photo from a web link (e.g. a Firebase Storage URL) and processes it. */
    fun fromUrl(url: String): PhotoData? = try {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        val bytes = try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= FULL_MAX_PX) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bitmap == null) null else fromBitmap(bitmap)
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't download photo", e)
        null
    }

    fun fromBitmap(src: Bitmap): PhotoData {
        val full = scaleDown(src, FULL_MAX_PX)
        val thumb = scaleDown(src, THUMB_MAX_PX)
        var quality = 82
        var fullBase64 = encode(full, quality)
        while (fullBase64.length > FULL_MAX_BASE64 && quality > 40) {
            quality -= 12
            fullBase64 = encode(full, quality)
        }
        return PhotoData(thumb = encode(thumb, 72), full = fullBase64)
    }

    fun decode(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    private fun encode(bitmap: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleDown(src: Bitmap, maxPx: Int): Bitmap {
        val largest = maxOf(src.width, src.height)
        if (largest <= maxPx) return src
        val scale = maxPx.toFloat() / largest
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun decodeSampled(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun rotateIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
