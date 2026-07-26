package com.indeavour.coreader.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.ReadingListBook
import com.indeavour.coreader.model.firebase.ReadingListDocument
import com.indeavour.coreader.model.firebase.UserModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ReadingListViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Initialize Firebase AI Logic directly
    private val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel("gemini-3.6-flash")

    private val _readingList = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val readingList: StateFlow<List<ReadingListBook>> = _readingList

    private val _suggestions = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val suggestions: StateFlow<List<ReadingListBook>> = _suggestions

    private val _pendingSuggestions = MutableStateFlow<List<ReadingListBook>>(emptyList())
    val pendingSuggestions: StateFlow<List<ReadingListBook>> = _pendingSuggestions

    private var readingListListener: ListenerRegistration? = null

    init {
        // Ensure listener starts as soon as we have a user
        auth.addAuthStateListener {
            if (it.currentUser != null) {
                startReadingListListener()
            }
        }
        startReadingListListener()
    }

    private fun startReadingListListener() {
        val uid = auth.currentUser?.uid ?: return
        if (readingListListener != null) return // Already listening
        
        Log.d("ReadingListVM", "Starting listener for user: $uid")
        readingListListener = firestore.collection("readingLists").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ReadingListVM", "Listener error", error)
                    return@addSnapshotListener
                }
                Log.d("ReadingListVM", "Snapshot received. Raw data: ${snapshot?.data}")
                val doc = snapshot?.toObject(ReadingListDocument::class.java)
                Log.d("ReadingListVM", "Doc exists: ${snapshot?.exists()}, Books count: ${doc?.books?.size ?: 0}")
                val items = doc?.books?.values?.sortedBy { it.position } ?: emptyList()
                _readingList.value = items.filter { !it.isSuggestion }
                _suggestions.value = items.filter { it.isSuggestion && !it.isDismissed }
                Log.d("ReadingListVM", "Filtered: List=${_readingList.value.size}, Suggestions=${_suggestions.value.size}")
            }
    }

    override fun onCleared() {
        super.onCleared()
        readingListListener?.remove()
    }

    fun addBook(title: String, author: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val bookId = UUID.randomUUID().toString()
        val nextPosition = _readingList.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        val book = ReadingListBook(
            id = bookId,
            title = title,
            author = author,
            addedTimestamp = System.currentTimeMillis(),
            position = nextPosition,
            isSuggestion = false
        )
        viewModelScope.launch {
            try {
                val docRef = firestore.collection("readingLists").document(uid)
                try {
                    docRef.update("books.$bookId", book).await()
                } catch (e: Exception) {
                    // If update fails, document might not exist, try to set it
                    firestore.collection("readingLists").document(uid).set(
                        mapOf(
                            "userId" to uid,
                            "books" to mapOf(bookId to book)
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun removeBook(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (bookId.isBlank()) return
        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid)
                    .update("books.$bookId", com.google.firebase.firestore.FieldValue.delete()).await()
            } catch (_: Exception) {}
        }
    }

    fun dismissSuggestion(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (bookId.isBlank()) return
        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid)
                    .update("books.$bookId.isDismissed", true).await()
            } catch (_: Exception) {}
        }
    }

    fun dismissPendingSuggestion(bookId: String) {
        _pendingSuggestions.value = _pendingSuggestions.value.filter { it.id != bookId }
    }

    fun acceptSuggestion(suggestion: ReadingListBook) {
        val uid = auth.currentUser?.uid ?: return
        val nextPosition = _readingList.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        val book = suggestion.copy(
            isSuggestion = false,
            addedTimestamp = System.currentTimeMillis(),
            position = nextPosition
        )
        
        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid)
                    .update("books.${book.id}", book).await()
                dismissPendingSuggestion(book.id)
            } catch (e: Exception) {
                Log.e("ReadingListVM", "Failed to accept suggestion", e)
            }
        }
    }

    fun convertSuggestionToToRead(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (bookId.isBlank()) return
        val nextPosition = _readingList.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid).update(
                    "books.$bookId.isSuggestion", false,
                    "books.$bookId.addedTimestamp", System.currentTimeMillis(),
                    "books.$bookId.position", nextPosition
                ).await()
            } catch (_: Exception) {}
        }
    }

    fun moveBook(bookId: String, moveUp: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val list = _readingList.value
        val currentIndex = list.indexOfFirst { it.id == bookId }
        if (currentIndex == -1) return

        val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex < 0 || targetIndex >= list.size) return

        val currentBook = list[currentIndex]
        val targetBook = list[targetIndex]

        viewModelScope.launch {
            try {
                firestore.collection("readingLists").document(uid).update(
                    "books.${currentBook.id}.position", targetBook.position,
                    "books.${targetBook.id}.position", currentBook.position
                ).await()
            } catch (_: Exception) {}
        }
    }

    fun generateAISuggestions(userPrompt: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        Log.d("ReadingListVM", "Generating AI suggestions directly. Prompt: $userPrompt")
        
        viewModelScope.launch {
            try {
                // Fetch user's library to get preferences
                val userDoc = firestore.collection("users").document(uid).get().await()
                val user = userDoc.toObject(UserModel::class.java)
                val libraryBooks = user?.books?.values?.filter { !it.isDeleted } ?: emptyList()
                
                // Context for the AI: what the user is currently reading or has read
                val contextString = libraryBooks.joinToString(", ") { "${it.title} by ${it.author}" }

                val fullPrompt = """
                    You are a book recommendation assistant for the "Co Reader" app.
                    Based on the user's reading history: $contextString
                    ${if (!userPrompt.isNullOrBlank()) "The user specifically asked for: $userPrompt" else "Suggest 3-5 books they might like next."}
                    
                    Return ONLY a JSON array of objects. Each object must have "title", "author", and "description" fields.
                    The "description" must be a small spoiler-free description of the book.
                    Do not include any other text or markdown formatting.
                """.trimIndent()

                Log.d("ReadingListVM", "Calling Gemini...")
                val response = generativeModel.generateContent(fullPrompt)
                val responseText = response.text?.trim() ?: ""
                Log.d("ReadingListVM", "AI Response: $responseText")

                // Clean the response from markdown if present
                val cleanedJson = responseText.removePrefix("```json").removeSuffix("```").trim()
                
                val suggestionsArray = try {
                    JSONArray(cleanedJson)
                } catch (e: Exception) {
                    // Fallback if AI didn't follow JSON format strictly
                    Log.e("ReadingListVM", "JSON parsing failed, trying to extract objects", e)
                    null
                }

                if (suggestionsArray != null && suggestionsArray.length() > 0) {
                    val newPending = mutableListOf<ReadingListBook>()
                    
                    for (i in 0 until suggestionsArray.length()) {
                        val obj = suggestionsArray.getJSONObject(i)
                        val title = obj.optString("title")
                        val author = obj.optString("author")
                        val description = obj.optString("description")
                        
                        if (title.isNotBlank()) {
                            val bookId = UUID.randomUUID().toString()
                            val suggestion = ReadingListBook(
                                id = bookId,
                                title = title,
                                author = author,
                                description = description,
                                isSuggestion = true
                            )
                            newPending.add(suggestion)
                        }
                    }
                    
                    if (newPending.isNotEmpty()) {
                        _pendingSuggestions.value = newPending
                        Log.d("ReadingListVM", "Successfully updated pending suggestions")
                    }
                }

            } catch (e: Exception) {
                Log.e("ReadingListVM", "Error generating AI suggestions directly", e)
            }
        }
    }
}
