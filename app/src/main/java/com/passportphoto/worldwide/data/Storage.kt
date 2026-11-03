package com.passportphoto.worldwide.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String,
    val original: String,
    val country: String,
    val document: String,
    val widthMm: Float,
    val heightMm: Float,
    val created: Long = System.currentTimeMillis(),
    val zoom: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY created DESC") fun observe(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id") suspend fun get(id: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(project: Project)

    @Delete suspend fun delete(project: Project)

    @Query("DELETE FROM projects") suspend fun clear()
}

@Database(entities = [Project::class], version = 1, exportSchema = false)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
}

private val Context.store by preferencesDataStore("settings")

data class Settings(
    val country: String = "US",
    val document: String = "Passport",
    val theme: String = "System",
    val quality: Int = 95,
    val format: String = "JPEG",
)

class Preferences(private val context: Context) {
    val settings =
        context.store.data.map {
            Settings(
                it[stringPreferencesKey("country")] ?: "US",
                it[stringPreferencesKey("document")] ?: "Passport",
                it[stringPreferencesKey("theme")] ?: "System",
                it[intPreferencesKey("quality")] ?: 95,
                it[stringPreferencesKey("format")] ?: "JPEG",
            )
        }

    suspend fun text(key: String, value: String) {
        context.store.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun quality(value: Int) {
        context.store.edit { it[intPreferencesKey("quality")] = value.coerceIn(70, 100) }
    }
}
