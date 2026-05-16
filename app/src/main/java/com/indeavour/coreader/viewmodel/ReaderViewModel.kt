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

    data class ReadingProgress(
        val value: Float = 0f,
        val pageLabel: String = "",
        val percentageLabel: String = "",
        val chapterLabel: String = ""
    )

    private val _progress = MutableStateFlow(ReadingProgress())
    val progress: StateFlow<ReadingProgress> = _progress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

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
    }

    fun loadActiveBook() {
        Log.d("ReaderViewModel", "loadActiveBook called")
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val activeBook = database.bookDao().getActive()
            Log.d("ReaderViewModel", "Active book: $activeBook")
            if (activeBook != null) {
                val bookFile = File(activeBook.filePath)
                Log.d("ReaderViewModel", "Book file path: ${activeBook.filePath}, exists: ${bookFile.exists()}")
                if (bookFile.exists()) {
                    openBook(bookFile)
                } else {
                    _error.value = "Book file not found at ${activeBook.filePath}"
                }
            } else {
                _error.value = "No active book found"
            }
        }
    }

    private fun openBook(file: File) {
        Log.d("ReaderViewModel", "openBook: ${file.absolutePath}")
        viewModelScope.launch {
            repository.openBook(file)
                .onSuccess {
                    Log.d("ReaderViewModel", "Book opened successfully: ${it.metadata.title}")
                    _publication.value = it
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
