package com.canonreader.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import com.canonreader.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The corpus ships inside the APK as assets/canon.db.gz (built by pipeline/build.py).
 * On first launch, and whenever the app's version code changes, it is unpacked into
 * the app's files directory and opened read-only.
 */
@Singleton
class CanonDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    @Volatile
    private var db: SQLiteDatabase? = null

    suspend fun get(): SQLiteDatabase {
        db?.let { return it }
        return mutex.withLock {
            db ?: withContext(Dispatchers.IO) { install() }.also { db = it }
        }
    }

    private fun install(): SQLiteDatabase {
        val file = File(context.filesDir, DB_NAME)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(KEY_VERSION, -1)
        if (!file.exists() || installedVersion != BuildConfig.VERSION_CODE) {
            val tmp = File(context.filesDir, "$DB_NAME.tmp")
            openBundledDatabase().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
            if (file.exists()) file.delete()
            check(tmp.renameTo(file)) { "Couldn't install the bundled library" }
            prefs.edit { putInt(KEY_VERSION, BuildConfig.VERSION_CODE) }
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /**
     * The build tools may un-gzip `.gz` assets while packaging, so the corpus can land
     * in the APK as either canon.db.gz or plain canon.db.
     */
    private fun openBundledDatabase(): InputStream = try {
        GZIPInputStream(context.assets.open(ASSET_NAME).buffered(1 shl 16))
    } catch (e: FileNotFoundException) {
        context.assets.open(DB_NAME).buffered(1 shl 16)
    }

    private companion object {
        const val ASSET_NAME = "canon.db.gz"
        const val DB_NAME = "canon.db"
        const val PREFS = "canon_db"
        const val KEY_VERSION = "installed_version_code"
    }
}
