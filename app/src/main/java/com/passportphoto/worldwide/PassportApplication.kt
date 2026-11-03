package com.passportphoto.worldwide

import android.app.Application
import androidx.room.Room
import com.passportphoto.worldwide.data.*

class PassportApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, PhotoDatabase::class.java, "photos.db").build()
    }
    val preferences by lazy { Preferences(this) }

    override fun onCreate() {
        super.onCreate()
        // Only short-lived externally shared files are in cache. Originals are never exposed.
        listOf("capture", "exports").forEach { name ->
            java.io
                .File(cacheDir, name)
                .listFiles()
                ?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }
                ?.forEach { it.delete() }
        }
    }
}
