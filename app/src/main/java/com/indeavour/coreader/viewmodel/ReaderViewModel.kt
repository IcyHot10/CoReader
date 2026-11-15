package com.indeavour.coreader.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.indeavour.coreader.AppRoomDatabase
import com.indeavour.coreader.domain.BookQuestionPipeline
import com.indeavour.coreader.domain.BookText
import com.indeavour.coreader.domain.ChapterText
import com.indeavour.coreader.domain.ChapterTitles
import com.indeavour.coreader.domain.Exchange
import com.indeavour.coreader.domain.Passage
import com.indeavour.coreader.domain.PassageIndex
import com.indeavour.coreader.domain.QuestionResult
import com.indeavour.coreader.domain.QuestionStage
import com.indeavour.coreader.domain.ReadPosition
import com.indeavour.coreader.domain.ReadSoFar
import com.indeavour.coreader.domain.ReadingContext
import com.indeavour.coreader.domain.SpoilerPolicy
import com.indeavour.coreader.model.ai.AgentException
import com.indeavour.coreader.model.ai.BookQuestionAgent
import com.indeavour.coreader.domain.ProgressLookup
import com.indeavour.coreader.domain.ReadingSessionTimer
import com.indeavour.coreader.domain.ReadingStatsCalculator
import com.indeavour.coreader.domain.ReturnPoint
import com.indeavour.coreader.domain.TocEntry
import com.indeavour.coreader.model.firebase.AnnotationModel
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.MemberColour
import com.indeavour.coreader.model.firebase.ReplyModel
import com.indeavour.coreader.model.firebase.UserModel
import com.indeavour.coreader.model.room.ReadingActivity
import com.indeavour.coreader.model.room.UserPreferences
import com.indeavour.coreader.repository.ReaderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.indeavour.coreader.repository.AnnotationRepository
import com.indeavour.coreader.repository.AnnotationScope
import com.indeavour.coreader.repository.GroupRepository
import com.indeavour.coreader.repository.LocatorFields
import com.indeavour.coreader.repository.StatsRepository
import com.indeavour.coreader.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.mediatype.MediaType
import java.io.File

class ReaderViewModel(
    private val application: Application,
    private val repository: ReaderRepository
) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val users = UserRepository()
    private val groups = GroupRepository()
    private val stats = StatsRepository()
    private val annotations = AnnotationRepository()

    // Persistence launched from teardown paths (onCleared/onPause) must outlive
    // viewModelScope, which is already cancelled when onCleared runs.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication

    private val _initialLocator = MutableStateFlow<Locator?>(null)
    val initialLocator: StateFlow<Locator?> = _initialLocator

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

    var hasEverLoaded = false
        private set

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * [id] is the annotation's document id. Deletes and replies address it directly instead of
     * re-identifying an annotation by comparing its locator and text, which could not tell two
     * identical notes apart.
     */
    data class HighlightData(
        val id: String,
        val locator: Locator,
        val userId: String,
        val color: Int = AnnotationModel.DEFAULT_COLOR
    )

    data class NoteData(
        val id: String,
        val locator: Locator,
        val userId: String,
        val content: String,
        val color: Int = AnnotationModel.DEFAULT_COLOR,
        val replies: List<ReplyModel> = emptyList()
    )

    private val _selectedHighlightColor = MutableStateFlow(0x66FFFF00)
    val selectedHighlightColor: StateFlow<Int> = _selectedHighlightColor

    private val _fontSize = MutableStateFlow(100f)
    val fontSize: StateFlow<Float> = _fontSize

    private val _theme = MutableStateFlow("system")
    val theme: StateFlow<String> = _theme

    fun setSelectedHighlightColor(color: Int) {
        _selectedHighlightColor.value = color

        // Save to Firestore
        val uid = auth.currentUser?.uid ?: return
        saveScope.launch {
            try {
                users.setLastHighlightColor(uid, color)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to save lastHighlightColor", e)
            }
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val currentPrefs = database.userPreferencesDao().getPreferences().firstOrNull() ?: UserPreferences()
            database.userPreferencesDao().insertOrUpdate(currentPrefs.copy(fontSize = size))
        }
    }

    fun setTheme(theme: String) {
        _theme.value = theme
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val currentPrefs = database.userPreferencesDao().getPreferences().firstOrNull() ?: UserPreferences()
            database.userPreferencesDao().insertOrUpdate(currentPrefs.copy(theme = theme))
        }
    }

    private val _highlights = MutableStateFlow<List<HighlightData>>(emptyList())
    val highlights: StateFlow<List<HighlightData>> = _highlights

    private val _notes = MutableStateFlow<List<NoteData>>(emptyList())
    val notes: StateFlow<List<NoteData>> = _notes

    data class MemberProgress(
        val userId: String,
        val username: String,
        val locator: Locator
    )

    private val _groupProgress = MutableStateFlow<List<MemberProgress>>(emptyList())
    val groupProgress: StateFlow<List<MemberProgress>> = _groupProgress

    private val _usernames = MutableStateFlow<Map<String, String>>(emptyMap())
    val usernames: StateFlow<Map<String, String>> = _usernames

    private val _remoteProgression = MutableStateFlow<Locator?>(null)
    val remoteProgression: StateFlow<Locator?> = _remoteProgression

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    /** Each group member's colour (ARGB), keyed by user id. Empty outside a group. */
    private val _memberColours = MutableStateFlow<Map<String, Int>>(emptyMap())
    val memberColours: StateFlow<Map<String, Int>> = _memberColours

    /** Whether the open book is being read with a group. */
    private val _isGroupBook = MutableStateFlow(false)
    val isGroupBook: StateFlow<Boolean> = _isGroupBook

    /** Sets the colour the rest of the group sees this reader's initials on. */
    fun setMyMemberColour(argb: Int) {
        val uid = auth.currentUser?.uid ?: return
        val groupId = loadedGroupId?.takeIf { it != NO_GROUP && it.isNotBlank() } ?: return
        // Shown immediately; the group listener confirms it.
        _memberColours.value = _memberColours.value + (uid to argb)
        saveScope.launch {
            try {
                groups.setMemberColour(groupId, uid, MemberColour.encode(argb))
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to save member colour", e)
            }
        }
    }

    // ------------------------------------------------------------ asking about the book

    sealed interface BookChatState {
        data class Working(val stage: QuestionStage) : BookChatState
        data class Answered(
            val text: String,
            val containsSpoilers: Boolean,
            val verified: Boolean,
            /** The critic checked it against passages of the book, not just from memory. */
            val grounded: Boolean
        ) : BookChatState

        /** Only a spoiler answers the question; waiting for the reader to choose. */
        data object SpoilerChoice : BookChatState
        /** [policy] is what was being attempted, so that it can be tried again. */
        data class Failed(val message: String, val policy: SpoilerPolicy) : BookChatState
    }

    data class BookChatEntry(val id: Long, val question: String, val state: BookChatState)

    /** Questions asked about the open book this session, oldest first. */
    private val _bookChat = MutableStateFlow<List<BookChatEntry>>(emptyList())
    val bookChat: StateFlow<List<BookChatEntry>> = _bookChat

    private var nextChatId = 0L

    // Created on first use: most reading sessions never ask anything.
    private val questionPipeline by lazy {
        val agent = BookQuestionAgent()
        BookQuestionPipeline(
            answer = agent::answer,
            review = agent::review,
            evidence = ::findPassages,
            unreadText = ::findUnreadPassages
        )
    }

    /** The whole book as plain text, one entry per file in reading order; read on first use. */
    private var bookText: List<ChapterText>? = null
    private var bookTextFor: Publication? = null

    /** [bookText] prepared for searching; built once per book. */
    private var bookIndex: PassageIndex? = null
    private var bookIndexFor: List<ChapterText>? = null

    private suspend fun loadBookIndex(pub: Publication): PassageIndex {
        val book = loadBookText(pub)
        bookIndex?.takeIf { bookIndexFor === book }?.let { return it }
        return withContext(Dispatchers.Default) { PassageIndex(book) }.also {
            bookIndex = it
            bookIndexFor = book
        }
    }

    private suspend fun loadBookText(pub: Publication): List<ChapterText> {
        bookText?.takeIf { bookTextFor === pub }?.let { return it }
        // Makes sure the chapter titles for this book are worked out.
        pub.readingOrder.firstOrNull()?.let { first ->
            chapterTitleFor(pub, Locator(href = first.url(), mediaType = first.mediaType ?: MediaType.XHTML))
        }
        val titles = chapterTitles
        val loaded = withContext(Dispatchers.IO) {
            pub.readingOrder.mapIndexed { index, link ->
                // Only pages of text: an image or stylesheet in the reading order is not prose.
                // A file that does not say what it is is assumed to be a page, as EPUB pages are.
                val isPage = link.mediaType?.isHtml ?: true
                val html = try {
                    if (isPage) pub.get(link)?.read()?.getOrNull()?.let { BookText.decode(it) } else null
                } catch (e: Exception) {
                    Log.e("ReaderViewModel", "Failed to read ${link.href}", e)
                    null
                }
                ChapterText(titles.getOrNull(index).orEmpty(), html?.let { BookText.fromHtml(it) }.orEmpty())
            }
        }
        bookText = loaded
        bookTextFor = pub
        return loaded
    }

    /**
     * Searches the book's own text for passages about [query]. Unless the reader asked for
     * spoilers, only the part they have already read is searched, so the evidence itself can
     * never give anything away.
     */
    private suspend fun findPassages(query: String, policy: SpoilerPolicy): List<Passage> =
        if (policy == SpoilerPolicy.SPOILERS_ALLOWED) {
            searchBook(query, PassageIndex.Scope.WHOLE_BOOK)
        } else {
            searchBook(query, PassageIndex.Scope.READ)
        }

    /**
     * Searches the part of the book the reader has not reached. Only the critic sees the
     * result, as evidence that a draft answer is repeating something from further on.
     */
    private suspend fun findUnreadPassages(query: String): List<Passage> =
        searchBook(query, PassageIndex.Scope.UNREAD)

    private suspend fun searchBook(query: String, scope: PassageIndex.Scope): List<Passage> {
        val pub = _publication.value ?: return emptyList()
        return try {
            val index = loadBookIndex(pub)
            val position = readPosition(pub)
            withContext(Dispatchers.Default) { index.search(query, scope, position) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ReaderViewModel", "Failed to search the book's text", e)
            emptyList()
        }
    }

    /** Asks a question about the open book; the answer is kept free of spoilers. */
    fun askAboutBook(question: String) {
        val text = question.trim().take(MAX_QUESTION_CHARS)
        if (text.isEmpty()) return
        val id = nextChatId++
        _bookChat.value = _bookChat.value +
            BookChatEntry(id, text, BookChatState.Working(QuestionStage.ANSWERING))
        runQuestion(id, text, SpoilerPolicy.NO_SPOILERS)
    }

    /** The reader's answer to "this needs spoilers: do you want them?". */
    fun chooseSpoilers(id: Long, withSpoilers: Boolean) {
        val entry = _bookChat.value.firstOrNull { it.id == id } ?: return
        if (entry.state !is BookChatState.SpoilerChoice) return
        setChatState(id, BookChatState.Working(QuestionStage.ANSWERING))
        runQuestion(
            id,
            entry.question,
            if (withSpoilers) SpoilerPolicy.SPOILERS_ALLOWED else SpoilerPolicy.NO_SPOILERS_BEST_EFFORT
        )
    }

    /** Runs a question that failed again, the same way it was being asked. */
    fun retryQuestion(id: Long) {
        val entry = _bookChat.value.firstOrNull { it.id == id } ?: return
        val failed = entry.state as? BookChatState.Failed ?: return
        setChatState(id, BookChatState.Working(QuestionStage.ANSWERING))
        runQuestion(id, entry.question, failed.policy)
    }

    /** Forgets the conversation. A question still being answered is dropped when it finishes. */
    fun clearBookChat() {
        _bookChat.value = emptyList()
    }

    private fun setChatState(id: Long, state: BookChatState) {
        _bookChat.value = _bookChat.value.map { if (it.id == id) it.copy(state = state) else it }
    }

    private fun runQuestion(id: Long, question: String, policy: SpoilerPolicy) {
        viewModelScope.launch {
            val state = try {
                val context = readingContext()
                    ?: throw AgentException("Open a book before asking about it")
                // Everything already answered above this question, so a follow-up such as
                // "is she dead?" is understood.
                val history = _bookChat.value
                    .takeWhile { it.id != id }
                    .mapNotNull { entry ->
                        (entry.state as? BookChatState.Answered)?.let {
                            Exchange(entry.question, it.text, it.containsSpoilers, it.verified)
                        }
                    }
                val startedAt = System.currentTimeMillis()
                val result = withTimeout(QUESTION_TIMEOUT_MS) {
                    questionPipeline.ask(
                        context, question, policy, history,
                        isOutOfTime = { System.currentTimeMillis() - startedAt > REWRITE_BUDGET_MS }
                    ) { stage ->
                        setChatState(id, BookChatState.Working(stage))
                    }
                }
                when (result) {
                    is QuestionResult.Answered -> BookChatState.Answered(
                        result.text, result.containsSpoilers, result.verified, result.grounded
                    )
                    QuestionResult.SpoilerChoiceNeeded -> BookChatState.SpoilerChoice
                }
            } catch (e: TimeoutCancellationException) {
                BookChatState.Failed("The assistant took too long to respond", policy)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Book question failed", e)
                BookChatState.Failed(e.message ?: "Something went wrong", policy)
            }
            setChatState(id, state)
        }
    }

    /**
     * How much of the book counts as read, for the assistant. This is the reader's real place:
     * if they have jumped back to look at something, it is still where they had read up to. It
     * runs to the end of the page in front of them, since that is text they can see and ask about.
     */
    private fun readPosition(pub: Publication): ReadPosition? {
        val anchor = _returnLocator.value
        val locator = anchor ?: currentLocator ?: lastLocator ?: return null
        val key = ChapterTitles.resourceKey(locator.href.toString())
        val index = pub.readingOrder.indexOfFirst { ChapterTitles.resourceKey(it.url().toString()) == key }
        if (index < 0) return null
        val endOfPage = when {
            anchor != null -> returnPageEnd
            locator === currentLocator -> currentPageEnd
            else -> null
        }
        return ReadPosition(index, endOfPage ?: locator.locations.progression ?: 0.0)
    }

    private suspend fun readingContext(): ReadingContext? {
        val pub = _publication.value ?: return null
        val locator = _returnLocator.value ?: currentLocator ?: lastLocator
        val progression = locator?.locations?.totalProgression?.toFloat() ?: _progress.value.value
        val recentText = try {
            ReadSoFar.recentText(loadBookText(pub), readPosition(pub), RECENT_TEXT_CHARS)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ReaderViewModel", "Failed to read the book's text", e)
            ""
        }
        return ReadingContext(
            title = pub.metadata.title ?: UNTITLED_BOOK,
            author = pub.metadata.authors.firstOrNull()?.name ?: UNKNOWN_AUTHOR,
            chapter = locator?.let { chapterTitleFor(pub, it) }.orEmpty(),
            percentRead = kotlin.math.round(progression * 100).toInt().coerceIn(0, 100),
            recentText = recentText
        )
    }

    private var loadedBookId: Int? = null
    private var loadedGroupId: String? = NO_GROUP
    private var groupBookListener: ListenerRegistration? = null
    private var groupInfoListener: ListenerRegistration? = null
    private var annotationsListener: ListenerRegistration? = null

    private var lastLocator: Locator? = null
    private var wasCompletedAtStart: Boolean = false

    /** Counts reading time; see [ReadingSessionTimer] for what does and does not count. */
    private val sessionTimer = ReadingSessionTimer(MAX_SECONDS_PER_PAGE)

    // Stats that could not be written (the cloud write needs a connection) wait here and go out
    // with the next write instead of being dropped.
    @Volatile private var unsyncedSeconds: Long = 0
    @Volatile private var unsyncedCompletion: Boolean = false

    private fun startReadingSession(initialProgress: Float) {
        sessionTimer.start(System.currentTimeMillis())
        wasCompletedAtStart = initialProgress >= COMPLETION_THRESHOLD
    }

    private fun syncReadingProgress(isCompletedNow: Boolean, forceSync: Boolean = false) {
        if (!sessionTimer.isStarted) return

        val now = System.currentTimeMillis()
        sessionTimer.tick(now)

        val newlyCompleted = !wasCompletedAtStart && isCompletedNow
        val endedSessionSeconds = if (forceSync) sessionTimer.endSession() else 0L

        // Only trigger heavy updates if forced (e.g. leaving screen) or if we have a completion event
        val hasTimeToSave = forceSync && (sessionTimer.unsavedSeconds > 0 || unsyncedSeconds > 0)
        val endsCountableSession = endedSessionSeconds >= ReadingStatsCalculator.MIN_SESSION_SECONDS
        if (hasTimeToSave || endsCountableSession || newlyCompleted) {
            val secondsToSave = sessionTimer.takeUnsavedSeconds()
            if (newlyCompleted) wasCompletedAtStart = true

            val bookId = loadedBookId ?: return
            val uid = auth.currentUser?.uid ?: return

            saveScope.launch {
                // 1. Save to Local Room
                val database = AppRoomDatabase.getDatabase(getApplication())
                if (secondsToSave > 0 || newlyCompleted) {
                    database.readingActivityDao().insert(ReadingActivity(
                        bookId = bookId,
                        timestamp = now,
                        durationSeconds = secondsToSave,
                        isCompletedEvent = newlyCompleted
                    ))
                }

                if (newlyCompleted) {
                    database.bookDao().setCompletedTimestamp(bookId, now)
                }

                // 2. Push to Firestore for cross-device sync
                val cloudSeconds = secondsToSave + unsyncedSeconds
                val cloudCompletion = newlyCompleted || unsyncedCompletion
                unsyncedSeconds = 0
                unsyncedCompletion = false
                try {
                    val pub = _publication.value
                    val bookKey = pub?.let { bookKeyOf(it) }
                    stats.recordReading(
                        uid = uid,
                        secondsToSave = cloudSeconds,
                        newlyCompleted = cloudCompletion,
                        nowMillis = now,
                        bookKey = bookKey,
                        sessionSeconds = endedSessionSeconds
                    )
                } catch (e: Exception) {
                    Log.e("ReaderViewModel", "Failed to sync reading activity to Firestore", e)
                    unsyncedSeconds += cloudSeconds
                    unsyncedCompletion = unsyncedCompletion || cloudCompletion
                }
            }
        }
    }

    private fun recordAnnotationStat(uid: String, isNote: Boolean) {
        saveScope.launch {
            try {
                stats.recordAnnotation(uid, isNote, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to record annotation stat", e)
            }
        }
    }

    private fun bookKeyOf(pub: Publication): String {
        val author = pub.metadata.authors.firstOrNull()?.name ?: UNKNOWN_AUTHOR
        // Must match the key the library stores the book under, including its fallback title,
        // or annotations would be written to a different path than the one being listened to.
        return "${pub.metadata.title ?: UNTITLED_BOOK}_$author"
    }

    private fun annotationScope(groupId: String?, uid: String): AnnotationScope =
        if (groupId != null && groupId != NO_GROUP && groupId.isNotBlank()) {
            AnnotationScope.Group(groupId)
        } else {
            AnnotationScope.Personal(uid)
        }

    private fun parseLocator(json: String): Locator? = try {
        Locator.fromJSON(org.json.JSONObject(json))
    } catch (e: Exception) {
        null
    }

    /**
     * Streams this book's annotations. In a group that is every member's highlights and notes in
     * one flat query, rather than reconstructing them from each member's embedded arrays.
     *
     * Local edits appear immediately through Firestore's latency compensation, so there is no
     * separate optimistic copy of the list to keep in step.
     */
    private fun observeAnnotations(scope: AnnotationScope, bookKey: String) {
        annotationsListener?.remove()
        annotationsListener = annotations.listen(scope, bookKey) { loaded ->
            val highlights = mutableListOf<HighlightData>()
            val notes = mutableListOf<NoteData>()
            loaded.forEach { annotation ->
                val locator = parseLocator(annotation.locator) ?: return@forEach
                if (annotation.isNote) {
                    notes += NoteData(
                        id = annotation.id,
                        locator = locator,
                        userId = annotation.ownerUid,
                        content = annotation.content,
                        color = annotation.color,
                        replies = annotation.replies
                    )
                } else {
                    highlights += HighlightData(
                        id = annotation.id,
                        locator = locator,
                        userId = annotation.ownerUid,
                        color = annotation.color
                    )
                }
            }
            _highlights.value = highlights
            _notes.value = notes

            resolveUsernames(
                loaded.flatMap { listOf(it.ownerUid) + it.replies.map { reply -> reply.userId } }
            )
        }
    }

    /** Group reading positions; annotations no longer travel on this document. */
    private fun observeGroupProgress(groupCode: String, bookKey: String, uid: String) {
        groupBookListener?.remove()
        groupBookListener = groups.listenGroupBooks(groupCode) { groupBook ->
            val progress = mutableListOf<MemberProgress>()
            groupBook?.bookProgression?.forEach { (memberId, memberBooks) ->
                val book = BookModel.fromAny(memberBooks[bookKey]) ?: return@forEach
                if (book.progress.isBlank()) return@forEach
                val locator = parseLocator(book.progress) ?: return@forEach
                progress += MemberProgress(memberId, _usernames.value[memberId] ?: "", locator)
                if (memberId == uid) _remoteProgression.value = locator
            }
            resolveUsernames(progress.map { it.userId })
            _groupProgress.value = progress.map {
                it.copy(username = _usernames.value[it.userId] ?: "")
            }
        }
    }

    private fun resolveUsernames(userIds: Collection<String>) {
        // A placeholder is not an answer: keep asking, so a member whose profile did not exist
        // (or could not be read) the first time gets their real name once it does.
        val missing = userIds.filter {
            it.isNotBlank() && _usernames.value[it].let { name -> name == null || name == UserRepository.UNKNOWN }
        }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val resolved = users.usernames(missing)
            if (resolved.isEmpty()) return@launch
            _usernames.value = _usernames.value + resolved
            _groupProgress.value = _groupProgress.value.map {
                if (it.username.isBlank()) it.copy(username = _usernames.value[it.userId] ?: "") else it
            }
        }
    }

    fun endReadingSession() {
        val currentProgress = _progress.value.value
        val isCompletedNow = currentProgress >= COMPLETION_THRESHOLD
        syncReadingProgress(isCompletedNow, forceSync = true)
        sessionTimer.pause()
    }

    // Restart the tick clock so time spent paused/backgrounded is not counted as reading time.
    fun resumeReadingSession() {
        sessionTimer.resume(System.currentTimeMillis())
    }

    fun setBookReady(ready: Boolean) {
        _isBookReady.value = ready
        if (ready) {
            hasEverLoaded = true
        }
    }

    fun updateProgress(pageIndex: Int, totalPages: Int, locator: Locator) {
        val pub = _publication.value ?: return
        val chapterLabel = chapterTitleFor(pub, locator)

        val progression = locator.locations.totalProgression?.toFloat() ?: (pageIndex.toFloat() / (totalPages - 1).coerceAtLeast(1))
        val percentage = (progression * 100).coerceIn(0f, 100f)
        val isCompletedNow = progression >= COMPLETION_THRESHOLD

        _progress.value = ReadingProgress(
            value = progression,
            pageLabel = "Page ${pageIndex + 1} of $totalPages",
            percentageLabel = if (percentage > 99.5f) "100%" else "${kotlin.math.round(percentage).toInt()}%",
            chapterLabel = chapterLabel
        )

        // Append reading time and sync completion status
        syncReadingProgress(isCompletedNow, forceSync = false)

        // Update the initial locator so that if the fragment is recreated (e.g. theme change),
        // it stays on the current page.
        _initialLocator.value = locator
        currentLocator = locator
        currentPageEnd = ReadSoFar.endOfPage(pageIndex, totalPages, locator.locations.progression ?: 0.0)

        // While the reader is only visiting another part of the book, the saved position stays
        // where they were actually reading.
        if (returnPoint.onProgress(progression.toDouble())) _returnLocator.value = null
        if (returnPoint.isActive) return

        lastLocator = locator
        saveProgressionLocally(locator)
    }

    /** Saves the reading position to Room; done on every page turn. */
    private fun saveProgressionLocally(locator: Locator) {
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val now = System.currentTimeMillis()
            database.bookDao().updateActiveBookProgression(locator.toJSON().toString(), now)
        }
    }

    // ------------------------------------------------------------ jumping around the book

    /** Where the navigator is right now, which differs from [lastLocator] while visiting. */
    private var currentLocator: Locator? = null

    /** How far through its chapter the bottom of the current page is; see [ReadSoFar.endOfPage]. */
    private var currentPageEnd: Double? = null

    /** The same, for the page the reader left when they jumped away. */
    private var returnPageEnd: Double? = null

    private val _returnLocator = MutableStateFlow<Locator?>(null)

    /**
     * The position the reader was at before jumping elsewhere (slider, note, table of contents),
     * or null when they are simply reading. While set, it is what gets saved as their progress,
     * so looking back at an old note never moves their place in the book.
     */
    val returnLocator: StateFlow<Locator?> = _returnLocator

    private val returnPoint = ReturnPoint<Locator>(RETURN_POINT_TOLERANCE)

    private var positions: List<Locator> = emptyList()

    /**
     * Call just before jumping away from the current page. The first jump sets the return point;
     * further jumps keep it, so it always leads back to where reading actually stopped.
     */
    fun markReturnPoint(targetProgression: Double? = null) {
        val from = currentLocator ?: lastLocator ?: return
        if (returnPoint.mark(from, from.locations.totalProgression, targetProgression)) {
            lastLocator = from
            returnPageEnd = if (from === currentLocator) currentPageEnd else null
            _returnLocator.value = from
        }
    }

    /** The reader is heading back to the return point; normal progress saving resumes there. */
    fun clearReturnPoint() {
        returnPoint.clear()
        returnPageEnd = null
        _returnLocator.value = null
    }

    /** The reader chose to stay where they jumped to, so that becomes their position. */
    fun dismissReturnPoint() {
        clearReturnPoint()
        currentLocator?.let {
            lastLocator = it
            saveProgressionLocally(it)
        }
    }

    /** The position closest to [totalProgression] (0..1) through the whole book. */
    fun locatorAt(totalProgression: Float): Locator? {
        val index = ProgressLookup.nearestIndex(
            positions.map { it.locations.totalProgression ?: 0.0 },
            totalProgression.toDouble()
        )
        return positions.getOrNull(index)
    }

    /** Chapter name at [totalProgression], for previewing where the slider will land. */
    fun chapterAt(totalProgression: Float): String {
        val pub = _publication.value ?: return ""
        val locator = locatorAt(totalProgression) ?: return ""
        return chapterTitleFor(pub, locator)
    }

    private var chapterTitlesFor: Publication? = null
    private var chapterTitles: List<String> = emptyList()

    /**
     * The name of the chapter [locator] falls in, taken from the table of contents.
     *
     * An EPUB's reading order is a list of files, not chapters: one chapter is often split over
     * several files and covers, title pages and the like are files too, so counting them gives
     * misleading "Chapter x of y" numbers. A file without its own table-of-contents entry is
     * treated as a continuation of the last one that had an entry.
     */
    private fun chapterTitleFor(pub: Publication, locator: Locator): String {
        if (chapterTitlesFor !== pub) {
            fun entry(link: Link): TocEntry =
                TocEntry(link.url().toString(), link.title, link.children.map(::entry))
            chapterTitles = ChapterTitles.byReadingOrder(
                toc = pub.tableOfContents.map(::entry),
                readingOrder = pub.readingOrder.map { it.url().toString() }
            )
            chapterTitlesFor = pub
        }

        val key = ChapterTitles.resourceKey(locator.href.toString())
        val index = pub.readingOrder.indexOfFirst { ChapterTitles.resourceKey(it.url().toString()) == key }
        return chapterTitles.getOrNull(index)?.takeIf { it.isNotEmpty() }
            ?: locator.title?.trim().orEmpty()
    }

    /**
     * Persists the current reading position. Writes only the fields that changed, on both the
     * personal copy and the group copy, instead of reading the whole library map and writing it
     * back — which dropped any edit another device had made in between.
     */
    fun saveProgressionToFirestore() {
        val pub = _publication.value ?: return
        // A return point is where the reader really is in the book; see [returnLocator].
        val locator = _returnLocator.value ?: lastLocator ?: return
        val uid = auth.currentUser?.uid ?: return
        val authorName = pub.metadata.authors.firstOrNull()?.name ?: UNKNOWN_AUTHOR
        val bookKey = bookKeyOf(pub)
        val progressionJson = locator.toJSON().toString()
        val savedProgress = locator.locations.totalProgression?.toFloat() ?: _progress.value.value
        val isCompletedNow = savedProgress >= COMPLETION_THRESHOLD
        val now = System.currentTimeMillis()

        saveScope.launch {
            try {
                val existing = users.book(uid, bookKey)
                val completedTimestamp = when {
                    existing != null && existing.completedTimestamp > 0 -> existing.completedTimestamp
                    isCompletedNow -> now
                    else -> 0L
                }

                val fields = mutableMapOf<String, Any?>(
                    "title" to (pub.metadata.title ?: ""),
                    "author" to authorName,
                    "progress" to progressionJson,
                    "progressionUpdatedTimestamp" to now,
                    "addedTimestamp" to (existing?.addedTimestamp ?: now)
                )
                if (completedTimestamp > 0) fields["completedTimestamp"] = completedTimestamp

                users.mergeBookFields(uid, bookKey, fields)

                if (isCompletedNow && completedTimestamp > 0) {
                    val database = AppRoomDatabase.getDatabase(getApplication())
                    database.bookDao().setCompletedTimestamp(loadedBookId ?: 0, completedTimestamp)
                }

                val groupId = loadedGroupId?.takeIf { it != NO_GROUP && it.isNotBlank() }
                if (groupId != null) {
                    groups.mergeGroupBookFields(groupId, uid, bookKey, fields)
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to update Firestore progress", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        groupBookListener?.remove()
        groupInfoListener?.remove()
        annotationsListener?.remove()
        endReadingSession()
        saveProgressionToFirestore()
    }

    fun loadActiveBook() {
        Log.d("ReaderViewModel", "loadActiveBook called")
        viewModelScope.launch {
            val database = AppRoomDatabase.getDatabase(getApplication())
            val activeBook = database.bookDao().getActive()
            Log.d("ReaderViewModel", "Active book: $activeBook")

            if (activeBook == null) {
                _error.value = "No active book found"
                return@launch
            }

            val uid = auth.currentUser?.uid
            val userModel: UserModel? = if (uid != null) {
                try {
                    users.user(uid)
                } catch (e: Exception) {
                    Log.e("ReaderViewModel", "Failed to load user", e)
                    null
                }
            } else {
                null
            }

            val bookKey = "${activeBook.title}_${activeBook.author}"
            val activeGroupId = userModel?.activeGroup?.takeIf { it.isNotBlank() } ?: NO_GROUP
            val resolvedGroupId = resolveGroupForBook(activeGroupId, activeBook.title, activeBook.author)

            // Set the last used color from user profile if available
            userModel?.lastHighlightColor?.let {
                _selectedHighlightColor.value = it
            }

            // Load local user preferences
            val prefs = database.userPreferencesDao().getPreferences().firstOrNull()
            prefs?.let {
                _fontSize.value = it.fontSize
                _theme.value = it.theme
            }

            // If it's a different book OR the resolved active group has changed, clear state immediately
            if (activeBook.id != loadedBookId || resolvedGroupId != loadedGroupId) {
                Log.d("ReaderViewModel", "Switching book/group: book ${loadedBookId}->${activeBook.id}, group ${loadedGroupId}->${resolvedGroupId}")
                if (loadedBookId != null) {
                    endReadingSession()
                }
                groupBookListener?.remove()
                groupBookListener = null
                groupInfoListener?.remove()
                groupInfoListener = null
                annotationsListener?.remove()
                annotationsListener = null
                _isAdmin.value = false
                _memberColours.value = emptyMap()
                _isGroupBook.value = resolvedGroupId != NO_GROUP
                _publication.value = null
                _isBookReady.value = false
                hasEverLoaded = false
                _progress.value = ReadingProgress()
                _initialLocator.value = null
                _highlights.value = emptyList()
                _notes.value = emptyList()
                _groupProgress.value = emptyList()
                clearReturnPoint()
                _bookChat.value = emptyList()
                bookText = null
                bookTextFor = null
                bookIndex = null
                bookIndexFor = null
                currentPageEnd = null
                currentLocator = null
                positions = emptyList()
                loadedGroupId = resolvedGroupId

                // Start listening to group info if in a group
                if (resolvedGroupId != NO_GROUP) {
                    groupInfoListener = groups.listenGroup(resolvedGroupId) { group ->
                        _isAdmin.value = group?.groupMembers?.get(uid)?.admin == true
                        _memberColours.value = group?.groupMembers.orEmpty()
                            .mapValues { (memberId, member) ->
                                MemberColour.resolve(memberId, member.highlightColour)
                            }
                    }
                }
            } else if (_publication.value != null) {
                Log.d("ReaderViewModel", "Book ${activeBook.id} already loaded, resuming session")
                val currentProgress = _progress.value.value
                startReadingSession(currentProgress)
                return@launch
            }

            val bookFile = File(activeBook.filePath)
            Log.d("ReaderViewModel", "Book file path: ${activeBook.filePath}, exists: ${bookFile.exists()}")
            if (!bookFile.exists()) {
                _error.value = "Book file not found at ${activeBook.filePath}"
                return@launch
            }

            loadedBookId = activeBook.id

            val progressionToUse = storedProgression(uid, userModel, bookKey, activeBook, resolvedGroupId)
            val initialProgress = progressionToUse?.let { progressionOf(it) } ?: 0f

            startReadingSession(initialProgress)
            openBook(bookFile, progression = progressionToUse)

            if (uid != null) {
                // Annotations come from the book's own subcollection in both modes; only the
                // reading positions of other members are group-specific.
                observeAnnotations(annotationScope(resolvedGroupId, uid), bookKey)
                if (resolvedGroupId != NO_GROUP) {
                    observeGroupProgress(resolvedGroupId, bookKey, uid)
                } else {
                    _groupProgress.value = emptyList()
                }
            }
        }
    }

    /**
     * A book that has not been uploaded to the active group is read in personal mode, so its
     * annotations stay private rather than appearing in the group's shared set.
     */
    private suspend fun resolveGroupForBook(
        activeGroupId: String,
        title: String,
        author: String
    ): String {
        if (activeGroupId == NO_GROUP) return NO_GROUP
        return try {
            val groupBook = groups.groupBook(activeGroupId)
            if (groupBook == null) {
                Log.d("ReaderViewModel", "GroupBook doc for $activeGroupId not found. Using personal mode.")
                return NO_GROUP
            }
            val uploaded = groupBook.bookProgression.values.any { memberBooks ->
                memberBooks.values.any { entry ->
                    BookModel.fromAny(entry)?.let { it.title == title && it.author == author } ?: false
                }
            }
            if (uploaded) {
                activeGroupId
            } else {
                Log.d("ReaderViewModel", "Book $title not uploaded to group $activeGroupId. Using personal mode.")
                NO_GROUP
            }
        } catch (e: Exception) {
            Log.e("ReaderViewModel", "Error checking book in group", e)
            NO_GROUP
        }
    }

    /**
     * Compares local Room progression timestamp vs remote Firebase progression timestamp
     * and returns the progression from whichever is newer.
     */
    private suspend fun storedProgression(
        uid: String?,
        userModel: UserModel?,
        bookKey: String,
        activeBook: com.indeavour.coreader.model.room.RoomBook,
        resolvedGroupId: String
    ): String? {
        val remoteBook: BookModel? = userModel?.books?.get(bookKey)
            ?: if (uid != null && resolvedGroupId != NO_GROUP) {
                try {
                    groups.groupBook(resolvedGroupId)?.getBookModel(uid, bookKey)
                } catch (e: Exception) {
                    Log.e("ReaderViewModel", "Failed to fetch group progress", e)
                    null
                }
            } else null

        val remoteProgress = remoteBook?.progress?.takeIf { it.isNotBlank() }
        val remoteTimestamp = remoteBook?.progressionUpdatedTimestamp ?: 0L

        val localProgress = activeBook.progression?.takeIf { it.isNotBlank() }
        val localTimestamp = activeBook.progressionUpdatedTimestamp

        val (chosenProgress, chosenTimestamp, isFromRemote) = when {
            remoteProgress != null && localProgress != null -> {
                if (remoteTimestamp > localTimestamp) {
                    Triple(remoteProgress, remoteTimestamp, true)
                } else {
                    Triple(localProgress, localTimestamp, false)
                }
            }
            remoteProgress != null -> Triple(remoteProgress, remoteTimestamp, true)
            localProgress != null -> Triple(localProgress, localTimestamp, false)
            else -> Triple(null, 0L, false)
        }

        if (isFromRemote && chosenProgress != null) {
            try {
                val database = AppRoomDatabase.getDatabase(getApplication())
                database.bookDao().updateProgressionById(activeBook.id, chosenProgress, chosenTimestamp)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to sync newer remote progression to local Room DB", e)
            }
        }

        return chosenProgress
    }

    private fun progressionOf(locatorJson: String): Float = try {
        val json = org.json.JSONObject(locatorJson)
        json.optJSONObject("locations")?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
    } catch (e: Exception) {
        0f
    }

    fun addHighlight(locator: Locator) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)
        val locatorJson = locator.toJSON().toString()
        val position = LocatorFields.of(locatorJson)

        val annotation = AnnotationModel(
            id = annotations.newId(),
            ownerUid = uid,
            type = AnnotationModel.TYPE_HIGHLIGHT,
            locator = locatorJson,
            href = position.href,
            totalProgression = position.totalProgression,
            color = _selectedHighlightColor.value,
            createdAt = System.currentTimeMillis()
        )

        saveScope.launch {
            try {
                annotations.add(scope, bookKey, annotation)
                recordAnnotationStat(uid, isNote = false)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to add highlight", e)
            }
        }
    }

    fun addNote(locator: Locator, content: String) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)
        val locatorJson = locator.toJSON().toString()
        val position = LocatorFields.of(locatorJson)

        val annotation = AnnotationModel(
            id = annotations.newId(),
            ownerUid = uid,
            type = AnnotationModel.TYPE_NOTE,
            locator = locatorJson,
            href = position.href,
            totalProgression = position.totalProgression,
            color = _selectedHighlightColor.value,
            content = content,
            createdAt = System.currentTimeMillis()
        )

        saveScope.launch {
            try {
                annotations.add(scope, bookKey, annotation)
                recordAnnotationStat(uid, isNote = true)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to add note", e)
            }
        }
    }

    fun deleteHighlight(highlight: HighlightData) {
        deleteAnnotation(highlight.userId, highlight.id, "highlight")
    }

    fun deleteNote(note: NoteData) {
        deleteAnnotation(note.userId, note.id, "note")
    }

    /**
     * Deleting by document id removes exactly the annotation that was tapped. The previous
     * approach matched on locator and text, so two identical notes in the same place were
     * indistinguishable and both disappeared.
     */
    private fun deleteAnnotation(ownerUid: String, annotationId: String, kind: String) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        if (ownerUid != uid && !_isAdmin.value) return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)

        viewModelScope.launch {
            try {
                annotations.delete(scope, bookKey, annotationId)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to delete $kind", e)
            }
        }
    }

    fun addReplyToNote(note: NoteData, replyText: String) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)
        val reply = ReplyModel(userId = uid, text = replyText, timestamp = System.currentTimeMillis())

        viewModelScope.launch {
            try {
                // arrayUnion appends without rewriting the note, so two readers replying at the
                // same time no longer overwrite each other.
                annotations.addReply(scope, bookKey, note.id, reply)
                resolveUsernames(listOf(uid))
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to add reply", e)
            }
        }
    }

    fun deleteReply(note: NoteData, reply: ReplyModel) {
        val pub = _publication.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val scope = annotationScope(loadedGroupId, uid)
        val bookKey = bookKeyOf(pub)

        viewModelScope.launch {
            try {
                annotations.removeReply(scope, bookKey, note.id, reply)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to delete reply", e)
            }
        }
    }

    private fun openBook(file: File, progression: String? = null) {
        Log.d("ReaderViewModel", "openBook: ${file.absolutePath}, progression: $progression")
        if (!hasEverLoaded) {
            _isBookReady.value = false
        }
        viewModelScope.launch {
            repository.openBook(file)
                .onSuccess { pub ->
                    Log.d("ReaderViewModel", "Book opened successfully: ${pub.metadata.title}")

                    val locator = if (progression != null) {
                        try {
                            Locator.fromJSON(org.json.JSONObject(progression))
                        } catch (e: Exception) {
                            Log.e("ReaderViewModel", "Failed to parse initial progression JSON", e)
                            null
                        }
                    } else {
                        null
                    }

                    _initialLocator.value = locator
                    lastLocator = locator
                    _publication.value = pub

                    // Used to turn a slider position into a place in the book.
                    positions = try {
                        pub.positions()
                    } catch (e: Exception) {
                        Log.e("ReaderViewModel", "Failed to compute positions", e)
                        emptyList()
                    }
                }.onFailure {
                    Log.e("ReaderViewModel", "Failed to open book", it)
                    _error.value = it.message
                }
        }
    }

    companion object {
        /** Sentinel for "not reading in a group"; also the value stored in [loadedGroupId]. */
        private const val NO_GROUP = "none"
        private const val UNKNOWN_AUTHOR = "Unknown Author"
        private const val UNTITLED_BOOK = "Untitled Book"
        private const val COMPLETION_THRESHOLD = 0.995f
        private const val MAX_SECONDS_PER_PAGE = 300L
        private const val RETURN_POINT_TOLERANCE = 0.0005
        private const val QUESTION_TIMEOUT_MS = 90_000L

        /** After this long no further rewrite is started; the best answer so far is used. */
        private const val REWRITE_BUDGET_MS = 40_000L
        private const val MAX_QUESTION_CHARS = 500
        private const val RECENT_TEXT_CHARS = 4000

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
