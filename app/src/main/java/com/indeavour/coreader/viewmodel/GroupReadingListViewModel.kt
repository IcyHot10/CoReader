package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.model.ai.AgentTool
import com.indeavour.coreader.model.ai.BookSuggestion
import com.indeavour.coreader.model.ai.BookSuggestionAgent
import com.indeavour.coreader.model.ai.normalizedTitle
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.GroupReadingListBook
import com.indeavour.coreader.model.firebase.GroupReadingListComment
import com.indeavour.coreader.repository.GroupReadingListRepository
import com.indeavour.coreader.repository.GroupRepository
import com.indeavour.coreader.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class GroupReadingListViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val groupReadingListRepo = GroupReadingListRepository()
    private val userRepository = UserRepository()
    private val groupRepository = GroupRepository()

    private val _activeGroupCode = MutableStateFlow<String?>(null)
    val activeGroupCode: StateFlow<String?> = _activeGroupCode

    private val _groupName = MutableStateFlow<String>("")
    val groupName: StateFlow<String> = _groupName

    private val _books = MutableStateFlow<List<GroupReadingListBook>>(emptyList())
    val books: StateFlow<List<GroupReadingListBook>> = _books

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    /** AI suggestions are kept locally until a member proposes one to the group. */
    private val _suggestions = MutableStateFlow<List<BookSuggestion>>(emptyList())
    val suggestions: StateFlow<List<BookSuggestion>> = _suggestions

    private val _isSuggesting = MutableStateFlow(false)
    val isSuggesting: StateFlow<Boolean> = _isSuggesting

    private val suggestionAgent = BookSuggestionAgent(
        audience = "a book club choosing what to read next together",
        tools = listOf(
            AgentTool(
                name = "get_group_proposals",
                description = "Books already proposed to the group, with vote counts and members' comments."
            ) { proposalsJson() },
            AgentTool(
                name = "get_group_reading_history",
                description = "Books the group has read together, with how many members started and finished each."
            ) { groupHistoryJson() },
            AgentTool(
                name = "get_my_library",
                description = "Books in the current member's personal library, with whether each is finished."
            ) { myLibraryJson() }
        )
    )

    private var userListener: ListenerRegistration? = null
    private var groupReadingListListener: ListenerRegistration? = null
    private var groupListener: ListenerRegistration? = null

    init {
        listenToUser()
    }

    private fun listenToUser() {
        val uid = auth.currentUser?.uid ?: return
        userListener = userRepository.listenUser(uid) { userModel ->
            var code = userModel?.activeGroup?.takeIf { it.isNotBlank() }
            if (code == null && !userModel?.groupIDs.isNullOrEmpty()) {
                code = userModel.groupIDs.firstOrNull()
            }
            // Suggestions were tailored to the previous group.
            if (code != _activeGroupCode.value) _suggestions.value = emptyList()
            _activeGroupCode.value = code
            if (code != null) {
                attachGroupListeners(code, uid)
            } else {
                _books.value = emptyList()
                _groupName.value = ""
                _isAdmin.value = false
            }
        }
    }

    private fun attachGroupListeners(code: String, uid: String) {
        groupReadingListListener?.remove()
        groupListener?.remove()

        groupReadingListListener = groupReadingListRepo.listen(code) { list ->
            _books.value = list
        }

        groupListener = groupRepository.listenGroup(code) { groupModel ->
            _groupName.value = groupModel?.groupName ?: code
            _isAdmin.value = groupModel?.groupMembers?.get(uid)?.admin == true
        }
    }

    fun addProposedBook(title: String, author: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(false)
        val code = _activeGroupCode.value ?: return onResult(false)
        if (title.isBlank()) return onResult(false)

        viewModelScope.launch {
            try {
                val username = userRepository.username(uid)
                val newBook = GroupReadingListBook(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    author = author,
                    addedByUid = uid,
                    addedByUsername = username,
                    addedTimestamp = System.currentTimeMillis(),
                    votes = listOf(uid)
                )
                groupReadingListRepo.put(code, newBook)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun toggleVote(bookId: String) {
        val uid = auth.currentUser?.uid ?: return
        val code = _activeGroupCode.value ?: return
        val book = _books.value.find { it.id == bookId } ?: return
        val hasVoted = book.votes.contains(uid)

        viewModelScope.launch {
            try {
                groupReadingListRepo.toggleVote(code, bookId, uid, hasVoted)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    fun addComment(bookId: String, commentText: String) {
        val uid = auth.currentUser?.uid ?: return
        val code = _activeGroupCode.value ?: return
        if (commentText.isBlank()) return

        viewModelScope.launch {
            try {
                val username = userRepository.username(uid)
                val newComment = GroupReadingListComment(
                    id = UUID.randomUUID().toString(),
                    userId = uid,
                    username = username,
                    text = commentText.trim(),
                    timestamp = System.currentTimeMillis()
                )
                groupReadingListRepo.addComment(code, bookId, newComment)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    fun removeComment(bookId: String, comment: GroupReadingListComment) {
        val code = _activeGroupCode.value ?: return
        viewModelScope.launch {
            try {
                groupReadingListRepo.removeComment(code, bookId, comment)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    fun removeBook(bookId: String) {
        val code = _activeGroupCode.value ?: return
        viewModelScope.launch {
            try {
                groupReadingListRepo.remove(code, bookId)
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    /**
     * Asks the AI agent for books the group might enjoy next.
     * [onResult] receives an error message, or null on success.
     */
    fun generateAISuggestions(request: String, onResult: (String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult("You need to be signed in")
        if (_activeGroupCode.value == null) return onResult("Join a group first")
        if (_isSuggesting.value) return
        _isSuggesting.value = true
        viewModelScope.launch {
            try {
                val myTitles = userRepository.user(uid)?.books?.values.orEmpty().map { normalizedTitle(it.title) }
                val known = (myTitles + _books.value.map { normalizedTitle(it.title) }).toSet()
                val fresh = suggestionAgent.suggest(request)
                    .filter { normalizedTitle(it.title) !in known }
                    .distinctBy { normalizedTitle(it.title) }
                _suggestions.value = fresh
                onResult(if (fresh.isEmpty()) "No new suggestions this time, try again" else null)
            } catch (e: Exception) {
                onResult(e.message ?: "Failed to get suggestions")
            } finally {
                _isSuggesting.value = false
            }
        }
    }

    fun proposeSuggestion(suggestion: BookSuggestion, onResult: (Boolean) -> Unit) {
        addProposedBook(suggestion.title, suggestion.author) { success ->
            if (success) dismissSuggestion(suggestion)
            onResult(success)
        }
    }

    fun dismissSuggestion(suggestion: BookSuggestion) {
        _suggestions.value = _suggestions.value - suggestion
    }

    private fun proposalsJson(): JsonArray = buildJsonArray {
        _books.value.forEach { book ->
            add(buildJsonObject {
                put("title", book.title)
                put("author", book.author)
                put("votes", book.votes.size)
                put("comments", buildJsonArray { book.comments.forEach { add(JsonPrimitive(it.text)) } })
            })
        }
    }

    private suspend fun groupHistoryJson(): JsonArray {
        val code = _activeGroupCode.value ?: return JsonArray(emptyList())
        val memberBooks = groupRepository.groupBook(code)?.bookProgression?.values.orEmpty()
            .flatMap { books -> books.values.mapNotNull { BookModel.fromAny(it) } }
            .filter { !it.isDeleted && it.title.isNotBlank() }
        return buildJsonArray {
            memberBooks.groupBy { normalizedTitle(it.title) }.values.forEach { copies ->
                add(buildJsonObject {
                    put("title", copies.first().title)
                    put("author", copies.first().author)
                    put("membersReading", copies.size)
                    put("membersFinished", copies.count { it.completedTimestamp > 0 })
                })
            }
        }
    }

    private suspend fun myLibraryJson(): JsonArray {
        val uid = auth.currentUser?.uid ?: return JsonArray(emptyList())
        val books = userRepository.user(uid)?.books?.values.orEmpty()
            .filter { !it.isDeleted }
            .sortedByDescending { it.progressionUpdatedTimestamp }
            .take(50)
        return buildJsonArray {
            books.forEach { book ->
                add(buildJsonObject {
                    put("title", book.title)
                    put("author", book.author)
                    put("finished", book.completedTimestamp > 0)
                })
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        groupReadingListListener?.remove()
        groupListener?.remove()
    }
}
