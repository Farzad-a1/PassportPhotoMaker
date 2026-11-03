package com.passportphoto.worldwide.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.passportphoto.worldwide.PassportApplication
import com.passportphoto.worldwide.data.*
import com.passportphoto.worldwide.imaging.PhotoEngine
import com.passportphoto.worldwide.requirements.Requirements
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PhotoViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val app = application as PassportApplication
    val settings =
        app.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val projects =
        app.database
            .projects()
            .observe()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val active = MutableStateFlow<Project?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val pendingCapture = saved.getStateFlow<String?>("capture", null)
    private val undo = ArrayDeque<Project>()
    private var saveJob: Job? = null

    init {
        saved.get<String>("project")?.let { open(it) }
    }

    fun preference(key: String, value: String) {
        viewModelScope.launch { app.preferences.text(key, value) }
    }

    fun quality(value: Int) {
        viewModelScope.launch { app.preferences.quality(value) }
    }

    fun captureUri(): Uri {
        val file =
            File(File(app.cacheDir, "capture").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        saved["capture"] = file.path
        return androidx.core.content.FileProvider.getUriForFile(
            app,
            "${app.packageName}.files",
            file,
        )
    }

    fun captureResult(ok: Boolean, done: () -> Unit) {
        val path = pendingCapture.value ?: return
        if (ok)
            import(Uri.fromFile(File(path))) {
                File(path).delete()
                saved["capture"] = null
                done()
            }
        else {
            File(path).delete()
            saved["capture"] = null
            message.value = "Camera cancelled. No photo was saved."
        }
    }

    fun import(uri: Uri, done: () -> Unit) = work {
        val id = UUID.randomUUID().toString()
        val file = File(File(app.filesDir, "originals").apply { mkdirs() }, "$id.photo")
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 100L * 1024 * 1024) {
                            "Photo exceeds 100 MB. Choose a smaller original."
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Cannot access the selected photo")
            PhotoEngine.load(file, 1200).recycle()
            val prefs = settings.value
            val req = Requirements.get(prefs.country, prefs.document)
            val p = Project(id, file.path, prefs.country, prefs.document, req.widthMm, req.heightMm)
            app.database.projects().save(p)
            withContext(Dispatchers.Main) {
                active.value = p
                undo.clear()
                saved["project"] = id
                done()
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    fun open(id: String) = work {
        val p = app.database.projects().get(id)
        withContext(Dispatchers.Main) {
            active.value = p
            saved["project"] = id
            undo.clear()
        }
    }

    fun adjust(zoom: Float, x: Float, y: Float, record: Boolean = true) {
        val p = active.value ?: return
        if (record) {
            if (undo.size >= 40) undo.removeFirst()
            undo.addLast(p)
        }
        active.value =
            p.copy(zoom = zoom.coerceIn(1f, 6f), x = x.coerceIn(-3f, 3f), y = y.coerceIn(-3f, 3f))
        save()
    }

    fun undo() {
        if (undo.isNotEmpty()) {
            active.value = undo.removeLast()
            save()
        }
    }

    fun dimensions(w: Float, h: Float) {
        if (w in 10f..100f && h in 10f..150f) {
            active.value = active.value?.copy(widthMm = w, heightMm = h, zoom = 1f, x = 0f, y = 0f)
            save()
        } else message.value = "Use width 10–100 mm and height 10–150 mm."
    }

    fun save() {
        val p = active.value ?: return
        saveJob?.cancel()
        saveJob =
            viewModelScope.launch {
                delay(150)
                withContext(Dispatchers.IO) { app.database.projects().save(p) }
            }
    }

    private fun removeFiles(p: Project) {
        File(p.original).delete()
        File(app.cacheDir, "exports")
            .listFiles()
            ?.filter { it.name.startsWith(p.id) }
            ?.forEach { it.delete() }
    }

    fun delete(p: Project): Job {
        saveJob?.cancel()
        return work {
            app.database.projects().delete(p)
            removeFiles(p)
            withContext(Dispatchers.Main) {
                if (active.value?.id == p.id) {
                    active.value = null
                    saved["project"] = null
                }
            }
        }
    }

    fun clear(): Job {
        saveJob?.cancel()
        return work {
            val all = projects.value
            app.database.projects().clear()
            all.forEach { removeFiles(it) }
            withContext(Dispatchers.Main) {
                active.value = null
                saved["project"] = null
            }
        }
    }

    fun work(action: suspend () -> Unit) =
        viewModelScope.launch {
            if (busy.value) return@launch
            busy.value = true
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (e: Exception) {
                message.value = e.message ?: "Unable to complete operation"
            } finally {
                busy.value = false
            }
        }
}
