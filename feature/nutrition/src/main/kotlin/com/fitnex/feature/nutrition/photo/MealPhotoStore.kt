package com.fitnex.feature.nutrition.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Meal photos stay on the device, in the app's private storage (spec §11: only authorised
 * photos ever leave the phone; uploading for analysis comes with FITNEX AI in phase 8).
 */
@Singleton
class MealPhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    /** A new private file and the content Uri the camera app may write to. */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.mealphotos", file)
    }

    /** Copies a picked gallery image into private storage; returns its path. */
    suspend fun importFromGallery(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.absolutePath
        }.getOrNull()
    }

    /** Deletes a camera file the user cancelled. */
    fun discard(file: File) { file.delete() }

    companion object {
        const val DIR = "meal_photos"

        /** Downsampled for display, so a 12 MP photo does not cost 48 MB of memory. */
        suspend fun loadThumbnail(path: String, maxSide: Int = 720): Bitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
}
