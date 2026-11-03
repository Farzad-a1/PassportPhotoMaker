package com.passportphoto.worldwide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.passportphoto.worldwide.data.*
import com.passportphoto.worldwide.imaging.PhotoEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageExportTest {
    @Test
    fun roomPersistsCropAndDeletesProject() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).build()
        try {
            val p = Project("test", "private", "US", "Passport", 50.8f, 50.8f, zoom = 2f, x = .1f)
            db.projects().save(p)
            assertEquals(p, db.projects().get("test"))
            db.projects().delete(p)
            assertNull(db.projects().get("test"))
        } finally {
            db.close()
        }
    }

    @Test
    fun jpegAndPdfAreRealDecodableFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "test-original.jpg")
        val bitmap =
            Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.LTGRAY)
            }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        val p = Project("export-test", source.path, "US", "Passport", 50.8f, 50.8f)
        try {
            val jpg = PhotoEngine.jpeg(context, p, 95)
            val result = BitmapFactory.decodeFile(jpg.path)
            assertEquals(600, result.width)
            assertEquals(600, result.height)
            result.recycle()
            val pdf = PhotoEngine.pdf(context, p, "A4")
            assertEquals(
                "%PDF",
                pdf.inputStream().use {
                    val bytes = ByteArray(4)
                    it.read(bytes)
                    String(bytes)
                },
            )
            jpg.delete()
            pdf.delete()
        } finally {
            source.delete()
        }
    }
}
