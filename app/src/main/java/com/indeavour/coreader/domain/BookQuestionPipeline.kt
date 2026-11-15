package com.indeavour.coreader.domain

/** What is known about the reader's place in the book when they ask a question. */
data class ReadingContext(
    val title: String,
    val author: String,
    /** Name of the chapter they are in; may be empty. */
    val chapter: String,
    /** How far through the book they are, 0..100. */
    val percentRead: Int,
    /** The text they most recently read, so answers can refer to it; may be empty. */
    val recentText: String
)

/** An earlier question in the same conversation, with the answer the reader was shown. */
data class Exchange(
    val question: String,
    val answer: String,
    /** The reader asked for, and was shown, spoilers in this answer. */
    val containedSpoilers: Boolean = false
)

enum class SpoilerPolicy {
    /** The answer must fully answer the question without revealing anything past the reader. */
    NO_SPOILERS,

    /** The reader asked for the full answer, spoilers included. */
    SPOILERS_ALLOWED,

    /**
     * The reader was told a full answer needs spoilers and declined them: say what can safely
     * be said, even if that does not fully answer the question.
     */
    NO_SPOILERS_BEST_EFFORT
}

data class AnswerRequest(
    val context: ReadingContext,
    val question: String,
    val policy: SpoilerPolicy,
    /** Earlier questions and answers, oldest first, so follow-up questions make sense. */
    val history: List<Exchange> = emptyList(),
    /** Passages from the book's own text that bear on the question; may be empty. */
    val passages: List<Passage> = emptyList(),
    /** The draft the reviewer rejected, when this is a rewrite. */
    val previousAnswer: String? = null,
    /** What the reviewer said was wrong with [previousAnswer]. */
    val feedback: String? = null
)

data class ReviewRequest(
    val context: ReadingContext,
    val question: String,
    val answer: String,
    val policy: SpoilerPolicy,
    /** Earlier questions and answers, oldest first; needed to judge a follow-up question. */
    val history: List<Exchange> = emptyList(),
    /** Passages from the book's own text to check the answer's statements against. */
    val passages: List<Passage> = emptyList()
)

/** The reviewer's verdict on one draft answer. */
data class Review(
    /** The draft reveals or hints at something past the reader's place in the book. */
    val containsSpoilers: Boolean,
    /** The draft actually answers the question. */
    val useful: Boolean,
    /** The question itself cannot be answered usefully without spoilers. */
    val needsSpoilers: Boolean,
    /** How to fix the draft; empty when nothing is wrong. */
    val feedback: String,
    /** What the draft states as fact is right: nothing contradicts the book or is made up. */
    val accurate: Boolean = true
)

sealed interface QuestionResult {
    data class Answered(
        val text: String,
        val containsSpoilers: Boolean,
        /**
         * False when the reviewer never signed the answer off (it may be wrong or incomplete)
         * and this is the best attempt.
         */
        val verified: Boolean
    ) : QuestionResult

    /** Only a spoiler answers this; the reader has to choose whether they want one. */
    data object SpoilerChoiceNeeded : QuestionResult
}

enum class QuestionStage { ANSWERING, CHECKING, REVISING }

/**
 * Answers a reader's question with one model and has a second, the critic, check the result
 * before the reader sees it: that it gives nothing away past their place in the book, that it
 * actually answers what was asked, and that what it says is true. A rejected draft goes back
 * for a rewrite with the critic's feedback, up to [maxRevisions] times.
 *
 * Both models are given passages from the book itself ([evidence]). The writer answers from
 * them; the critic is given the ones matching the draft, so it checks each statement against
 * what the book says rather than against its own memory of the book.
 *
 * The models and the search are passed in as plain functions, so the rules here (what gets
 * shown, what gets rewritten, when the reader is asked about spoilers) can be tested without
 * them.
 */
class BookQuestionPipeline(
    private val answer: suspend (AnswerRequest) -> String,
    private val review: suspend (ReviewRequest) -> Review,
    /**
     * Finds passages of the book relevant to some text. The policy says how much of the book
     * may be searched: only what the reader has read, unless they asked for spoilers.
     */
    private val evidence: suspend (query: String, policy: SpoilerPolicy) -> List<Passage> = { _, _ -> emptyList() },
    private val maxRevisions: Int = 2
) {

    suspend fun ask(
        context: ReadingContext,
        question: String,
        policy: SpoilerPolicy = SpoilerPolicy.NO_SPOILERS,
        history: List<Exchange> = emptyList(),
        onStage: (QuestionStage) -> Unit = {}
    ): QuestionResult {
        var draft = ""
        var feedback: String? = null
        var verdict = Review(containsSpoilers = false, useful = false, needsSpoilers = false, feedback = "")

        // A follow-up ("is she dead?") rarely names its subject, so the search also uses the
        // exchange it follows on from.
        val topic = listOfNotNull(history.lastOrNull()?.question, history.lastOrNull()?.answer, question)
            .joinToString(" ")
        val passagesForWriting = evidence(topic, policy)

        repeat(maxRevisions + 1) { attempt ->
            onStage(if (attempt == 0) QuestionStage.ANSWERING else QuestionStage.REVISING)
            draft = answer(
                AnswerRequest(
                    context, question, policy, history, passagesForWriting,
                    draft.takeIf { attempt > 0 }, feedback
                )
            ).trim()

            onStage(QuestionStage.CHECKING)
            // Searched again with the draft, to find the text behind the statements it makes.
            val passagesForChecking = evidence("$topic $draft", policy)
            verdict = review(ReviewRequest(context, question, draft, policy, history, passagesForChecking))

            when (policy) {
                SpoilerPolicy.NO_SPOILERS -> {
                    // No amount of rewriting helps if the question itself needs a spoiler.
                    if (verdict.needsSpoilers) return QuestionResult.SpoilerChoiceNeeded
                    if (!verdict.containsSpoilers && verdict.useful && verdict.accurate) {
                        return QuestionResult.Answered(draft, containsSpoilers = false, verified = true)
                    }
                }
                SpoilerPolicy.SPOILERS_ALLOWED -> if (verdict.useful && verdict.accurate) {
                    return QuestionResult.Answered(draft, verdict.containsSpoilers, verified = true)
                }
                // The reader accepted a partial answer, so it need not fully answer the question;
                // it must still be spoiler-free and true.
                SpoilerPolicy.NO_SPOILERS_BEST_EFFORT -> if (!verdict.containsSpoilers && verdict.accurate) {
                    return QuestionResult.Answered(draft, containsSpoilers = false, verified = true)
                }
            }
            feedback = verdict.feedback
        }

        // Out of rewrites. A draft the critic still calls a spoiler is never shown to a reader
        // who has not asked for spoilers.
        return when (policy) {
            SpoilerPolicy.NO_SPOILERS ->
                if (verdict.containsSpoilers) {
                    QuestionResult.SpoilerChoiceNeeded
                } else {
                    QuestionResult.Answered(draft, containsSpoilers = false, verified = false)
                }
            SpoilerPolicy.SPOILERS_ALLOWED ->
                QuestionResult.Answered(draft, containsSpoilers = true, verified = false)
            SpoilerPolicy.NO_SPOILERS_BEST_EFFORT ->
                if (verdict.containsSpoilers) {
                    QuestionResult.Answered(NOTHING_SAFE_TO_SAY, containsSpoilers = false, verified = true)
                } else {
                    QuestionResult.Answered(draft, containsSpoilers = false, verified = false)
                }
        }
    }

    companion object {
        const val NOTHING_SAFE_TO_SAY =
            "I can't say anything useful about that without giving away what happens later in the book."
    }
}
