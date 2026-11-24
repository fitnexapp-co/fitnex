package com.fitnex.core.datastore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Optional profile photo, kept only on this device in the app's private storage (never synced,
 * excluded from Android backups like everything else). Picked with the system photo picker, which
 * needs no storage permission.
 */
@Singleton
class ProfilePhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesDataSource,
) {
    fun path(userId: String): Flow<String?> = preferences.profilePhotoPath(userId)

    /** Decodes [uri], fixes its rotation, scales it to at most 1024 px and saves it. Returns false if unreadable. */
    suspend fun save(userId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri, MAX_SIDE) ?: return@withContext false
        val dir = File(context.filesDir, "profile").apply { mkdirs() }
        // A new name per save, so every image cache sees a different file.
        val file = File(dir, "${userId.hashCode().toUInt()}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val previous = preferences.profilePhotoPath(userId).firstOrNull()
        preferences.setProfilePhotoPath(userId, file.absolutePath)
        previous?.let { File(it).takeIf { f -> f != file }?.delete() }
        true
    }

    suspend fun remove(userId: String) = withContext(Dispatchers.IO) {
        preferences.profilePhotoPath(userId).firstOrNull()?.let { File(it).delete() }
        preferences.setProfilePhotoPath(userId, null)
    }

    /** For display: a square-ish bitmap no larger than [maxSide]; null when the file is gone. */
    suspend fun load(path: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds, maxSide) })
    }

    /** Decodes a picked image for the preview / save, honouring its EXIF orientation. */
    suspend fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val raw = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds, maxSide) })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val scale = minOf(1f, maxSide.toFloat() / maxOf(raw.width, raw.height))
            val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }.getOrNull()
    }

    private fun sampleSize(bounds: BitmapFactory.Options, maxSide: Int): Int {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_SIDE = 1024
    }
}
