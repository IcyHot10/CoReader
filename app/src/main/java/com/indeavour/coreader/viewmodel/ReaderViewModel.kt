package com.indeavour.coreader.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.indeavour.coreader.AppRoomDatabase
import com.indeavour.coreader.repository.ReaderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Publication
import java.io.File

class ReaderViewModel(
    private val application: Application,
    private val repository: ReaderRepository
) : AndroidViewModel(application) {

    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication

    private val _initialLocator = MutableStateFlow<org.readium.r2.shared.publication.Locator?>(null)
    val initialLocator: StateFlow<org.readium.r2.shared.publication.Locator?> = _initialLocator

    data class ReadingProgress(
        val value: Float = 0f,
        val pageLabel: String = "",
        val percentageLabel: String = "",
        val chapterLabel: String = ""
    )

    private val _progress = MutableStateFlow(ReadingProgress())
    val progress: StateFlow<ReadingProgress> = _progress

    private val _isBookReady = MutableStateFlow(false)
    val isBookReady: StateFlow<Boolean> = _isBookReady

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setBookReady(ready: Boolean) {
        _isBookReady.value = ready
    }

    fun updateProgress(pageIndex: Int, totalPages: Int, locator: org.readium.r2.shared.publication.Locator) {
        val pub = _publication.value
        val chapterLabel = pub?.let {
            val totalChapters = it.readingOrder.size
            val currentChapterIndex = it.readingOrder.indexOfFirst { link -> link.url() == locator.href }
            if (currentChapterIndex != -1) {
                "Chapter ${currentChapterIndex + 1} of $totalChapters"
            } else ""
        } ?: ""

        val progression = locator.locations.totalProgression?.toFloat() ?: (pageIndex.toFloat() / totalPages.coerceAtLeast(1))
        _progress.value = ReadingProgress(
            value = progression,
            pageLabel = "Page ${pageIndex + 1} of $totalPages",
            percentageLabel = "${(progression * 100).toInt()}%",
            chapterLabel = chapterLabel
        )

        // Update the initial locator so that if the fragment is recreated (e.g. theme change),
        // it stays on the current page.
        _initialLocator.value = locator

        // Save global progress to database
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            database.bookDao().updateActiveBookProgression(locator.toJSON().toString())
        }
    }

    fun loadActiveBook() {
        if (_publication.value != null) {
            Log.d("ReaderViewModel", "Book already loaded, skipping loadActiveBook")
            return
        }
        Log.d("ReaderViewModel", "loadActiveBook called")
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val activeBook = database.bookDao().getActive()
            Log.d("ReaderViewModel", "Active book: $activeBook")
            if (activeBook != null) {
                val bookFile = File(activeBook.filePath)
                Log.d("ReaderViewModel", "Book file path: ${activeBook.filePath}, exists: ${bookFile.exists()}")
                if (bookFile.exists()) {
                    openBook(bookFile, progression = activeBook.progression)
                } else {
                    _error.value = "Book file not found at ${activeBook.filePath}"
                }
            } else {
                _error.value = "No active book found"
            }
        }
    }

    private fun openBook(file: File, progression: String? = null) {
        Log.d("ReaderViewModel", "openBook: ${file.absolutePath}, progression: $progression")
        _isBookReady.value = false
        viewModelScope.launch {
            repository.openBook(file)
                .onSuccess { pub ->
                    Log.d("ReaderViewModel", "Book opened successfully: ${pub.metadata.title}")
                    
                    val locator = if (progression != null) {
                        try {
                            org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(progression))
                        } catch (e: Exception) {
                            Log.e("ReaderViewModel", "Failed to parse initial progression JSON", e)
                            null
                        }
                    } else {
                        null
                    }
                    
                    _initialLocator.value = locator
                    _publication.value = pub
                }.onFailure {
                    Log.e("ReaderViewModel", "Failed to open book", it)
                    _error.value = it.message
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return ReaderViewModel(
                    application,
                    ReaderRepository(application)
                ) as T
            }
        }
    }
}
