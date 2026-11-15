package com.indeavour.coreader.model.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionCallingConfig
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.indeavour.coreader.domain.AnswerRequest
import com.indeavour.coreader.domain.Exchange
import com.indeavour.coreader.domain.Passage
import com.indeavour.coreader.domain.ReadingContext
import com.indeavour.coreader.domain.Review
import com.indeavour.coreader.domain.ReviewRequest
import com.indeavour.coreader.domain.SpoilerPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The two Gemini models behind "ask about this book", reached through Firebase AI Logic: one
 * that answers the reader's question and a critic that reviews the answer. How they are
 * combined is decided by [com.indeavour.coreader.domain.BookQuestionPipeline].
 */
class BookQuestionAgent {

    companion object {
        private const val TAG = "BookQuestionAgent"
        // Same model as the suggestion agent, which found flash-lite the reliable one here.
        private const val MODEL_NAME = "gemini-flash-lite-latest"
        private const val SUBMIT = "submit_review"
        private const val MAX_HISTORY = 6
        private const val MAX_HISTORY_ANSWER_CHARS = 600

        internal fun describe(context: ReadingContext): String = buildString {
            append("Book: \"${context.title}\" by ${context.author}.\n")
            append("The reader is about ${context.percentRead}% of the way through")
            if (context.chapter.isNotBlank()) append(", in the chapter \"${context.chapter}\"")
            append(".\n")
            if (context.recentText.isNotBlank()) {
                append("The text they have most recently read (it may start or stop mid-sentence):\n")
                append("\"\"\"\n${context.recentText}\n\"\"\"\n")
            }
        }

        /**
         * The conversation so far, so a follow-up such as "is she dead?" can be understood.
         * Only the most recent exchanges are sent, to keep the request small.
         */
        internal fun describe(history: List<Exchange>): String {
            if (history.isEmpty()) return ""
            return buildString {
                append("Earlier in this conversation (the reader has already seen these answers")
                append(", so nothing in them is a spoiler to repeat):\n")
                history.takeLast(MAX_HISTORY).forEach { exchange ->
                    append("Reader: ${exchange.question.trim()}\n")
                    append("Answer")
                    if (exchange.containedSpoilers) append(" (the reader asked for spoilers)")
                    // An answer the critic never approved must not be built on as if it were fact.
                    if (!exchange.verified) append(" (unverified and possibly wrong: do not rely on it)")
                    append(": ${exchange.answer.trim().take(MAX_HISTORY_ANSWER_CHARS)}\n")
                }
                append("Use this to work out who or what the new question refers to.\n")
            }
        }

        private fun StringBuilder.appendPassages(passages: List<Passage>, label: String = "") {
            passages.forEachIndexed { index, passage ->
                append("[$label${index + 1}]")
                if (passage.chapter.isNotBlank()) append(" ${passage.chapter}")
                append(": \"${passage.text}\"\n")
            }
        }

        /** Passages of the book's own text, as the source of truth for an answer. */
        internal fun describe(passages: List<Passage>, policy: SpoilerPolicy): String {
            if (passages.isEmpty()) {
                return "No passages from the book's text could be found for this question.\n"
            }
            return buildString {
                append("Passages from the book's own text that may be relevant")
                append(
                    if (policy == SpoilerPolicy.SPOILERS_ALLOWED) " (taken from anywhere in the book):\n"
                    else " (all from the part the reader has already read):\n"
                )
                appendPassages(passages)
            }
        }

        /**
         * Passages from the part of the book the reader has not reached, shown to the critic
         * only, as evidence of what would be a spoiler.
         */
        internal fun describeUnread(passages: List<Passage>): String {
            if (passages.isEmpty()) return ""
            return buildString {
                append("Passages from the part of the book the reader has NOT read yet. The reader must ")
                append("not learn anything from these. They were found by searching the unread text for ")
                append("the draft's wording, so most will only mention the same people or places, which ")
                append("alone is not a spoiler. But if the draft states or hints at an event or fact that ")
                append("appears here and not in what the reader has read, it is a spoiler:\n")
                appendPassages(passages, label = "U")
            }
        }

        internal fun spoilerRule(policy: SpoilerPolicy): String = when (policy) {
            SpoilerPolicy.NO_SPOILERS ->
                "Spoiler rule: do not reveal or hint at anything that happens after the reader's " +
                    "current position, in this book or its sequels: no later events, deaths, twists, " +
                    "reveals, or how anything turns out. Use only what a reader at this point already " +
                    "knows. If the question cannot be answered properly within that limit, say what " +
                    "you safely can and state plainly that the rest would be a spoiler."
            SpoilerPolicy.SPOILERS_ALLOWED ->
                "Spoiler rule: the reader has asked for spoilers. Answer fully, including events " +
                    "after their current position."
            SpoilerPolicy.NO_SPOILERS_BEST_EFFORT ->
                "Spoiler rule: a full answer needs spoilers and the reader has chosen not to get " +
                    "them. Do not reveal or hint at anything after their current position. Give " +
                    "whatever helpful information you can from what they have already read, and " +
                    "state plainly that the rest is left out, without hinting at what it is."
        }

        /** Everything the writing model is sent for one answer or rewrite. */
        internal fun answerPrompt(request: AnswerRequest): String = buildString {
            append(describe(request.context))
            append('\n').append(spoilerRule(request.policy)).append("\n\n")
            append(describe(request.passages, request.policy)).append('\n')
            append(describe(request.history))
            append("Question: ${request.question.trim()}\n")
            if (request.previousAnswer != null) {
                append("\nYour previous answer was rejected by the reviewer:\n")
                append("\"\"\"\n${request.previousAnswer}\n\"\"\"\n")
                if (!request.feedback.isNullOrBlank()) append("Reviewer feedback: ${request.feedback}\n")
                append("Write a new answer that fixes this.\n")
            }
        }

        /** Everything the critic is sent to judge one draft. */
        internal fun reviewPrompt(request: ReviewRequest): String = buildString {
            append(describe(request.context))
            append('\n')
            append(
                when (request.policy) {
                    SpoilerPolicy.SPOILERS_ALLOWED ->
                        "The reader asked for spoilers, so they are permitted in this answer. Still report whether it contains any.\n"
                    else -> "The reader has not asked for spoilers, so the answer must not contain any.\n"
                }
            )
            append('\n').append(describe(request.passages, request.policy))
            describeUnread(request.unreadPassages).takeIf { it.isNotEmpty() }?.let { append('\n').append(it) }
            append('\n').append(describe(request.history))
            append("\nQuestion: ${request.question.trim()}\n")
            append("\nDraft answer:\n\"\"\"\n${request.answer}\n\"\"\"\n")
        }
    }

    private val backend = Firebase.ai(backend = GenerativeBackend.googleAI())

    private val answerModel = backend.generativeModel(
        modelName = MODEL_NAME,
        systemInstruction = content {
            text(
                """
                You are a reading companion inside an e-book app. You answer a reader's questions about
                the book they are currently reading: what has happened so far, characters, places,
                vocabulary, themes and background.
                Be accurate and brief: one or two short paragraphs of plain text, no markdown.
                When passages from the book's text are provided, they are the source of truth: base
                your answer on them, and prefer them over your own memory of the book wherever the
                two differ.
                If neither the passages nor your knowledge of the book settles a detail, say you are
                not sure instead of guessing. Never invent names, events or quotations.
                Follow the spoiler rule you are given exactly.
                If earlier questions and answers are shown, treat the new question as a follow-up to
                them: work out who or what it refers to from that conversation.
                """.trimIndent()
            )
        }
    )

    private val reviewModel = backend.generativeModel(
        modelName = MODEL_NAME,
        // A verdict should be the same every time it is asked for.
        generationConfig = generationConfig { temperature = 0f },
        systemInstruction = content {
            text(
                """
                You are a strict reviewer for a reading companion in an e-book app. You are shown where
                a reader is in a book, a question they asked and a draft answer. Judge the draft and
                report your verdict by calling $SUBMIT exactly once.
                - claims_checked: fill this in FIRST, before deciding anything else. List every statement
                  of fact in the draft, one per line, each followed by exactly one of: "supported by
                  passage [n]", "supported by my confident knowledge of the book", "contradicted by
                  passage [n]", "cannot verify", or "only in unread passage [Un]". Your other answers
                  must follow from this list.
                - contains_spoilers: true if the draft reveals or strongly hints at anything after the
                  reader's current position, in this book or its sequels. Things the reader has already
                  read, or was already told earlier in the conversation, are not spoilers. If you are
                  given passages from the unread part of the book, use them: a statement that appears
                  only there is a spoiler. A mere mention of the same people or places is not.
                - If earlier questions and answers are shown, read the question as a follow-up to them:
                  "is she dead?" is about whoever was just being discussed.
                - useful: true if the draft directly answers the question. A draft that refuses,
                  dodges, is vague, or is about something else is not useful.
                - accurate: check every statement of fact in the draft (who people are, what happened,
                  when, why, what a word means). You are given passages from the book's own text;
                  they are the source of truth and outrank your memory of the book. Set accurate to
                  false if any statement is contradicted by the passages, if the draft mixes up
                  characters or events, or if it states as fact something that neither the passages
                  nor your own confident knowledge of the book supports. A draft that says plainly
                  that it is unsure of something is accurate about that thing. If you cannot verify
                  a specific claim, that claim is not accurate: say which one in the feedback so
                  it can be corrected, hedged or removed.
                - needs_spoilers: true only if the question itself cannot be usefully answered without
                  revealing later events, for example "does she survive?" or "who is the killer?".
                - feedback: one or two sentences telling the writer exactly what to change. Never
                  include the spoiler itself, and never quote or describe anything from the unread
                  passages: say only which statement to remove. Empty if nothing needs changing.
                """.trimIndent()
            )
        },
        tools = listOf(
            Tool.functionDeclarations(
                listOf(
                    FunctionDeclaration(
                        name = SUBMIT,
                        description = "Submit the verdict on the draft answer.",
                        parameters = mapOf(
                            "claims_checked" to Schema.string("Each statement of fact in the draft, one per line, with how it was checked. Fill this in first."),
                            "contains_spoilers" to Schema.boolean("Whether the draft gives away anything past the reader's position."),
                            "useful" to Schema.boolean("Whether the draft directly answers the question."),
                            "accurate" to Schema.boolean("Whether every statement of fact in the draft is supported by the passages or by confident knowledge of the book."),
                            "needs_spoilers" to Schema.boolean("Whether the question cannot be usefully answered without spoilers."),
                            "feedback" to Schema.string("What to change in the draft; empty if nothing.")
                        )
                    )
                )
            )
        ),
        toolConfig = ToolConfig(FunctionCallingConfig.any())
    )

    /** Writes an answer, or rewrites a rejected one using the critic's feedback. */
    suspend fun answer(request: AnswerRequest): String {
        val prompt = answerPrompt(request)
        return call("answer") {
            answerModel.generateContent(prompt).text?.trim().orEmpty()
        }.ifBlank { throw AgentException("The assistant returned an empty answer") }
    }

    /** Has the critic judge a draft answer. */
    suspend fun review(request: ReviewRequest): Review {
        val prompt = reviewPrompt(request)
        val args = call("review") {
            reviewModel.generateContent(prompt).functionCalls.firstOrNull { it.name == SUBMIT }?.args
        } ?: throw AgentException("The reviewer did not return a verdict")

        fun flag(key: String): Boolean? = (args[key] as? JsonPrimitive)?.booleanOrNull
        fun text(key: String): String = (args[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val review = Review(
            // An unreadable verdict is treated as the cautious one.
            containsSpoilers = flag("contains_spoilers") ?: true,
            useful = flag("useful") ?: false,
            needsSpoilers = flag("needs_spoilers") ?: false,
            accurate = flag("accurate") ?: false,
            feedback = text("feedback"),
            reasoning = text("claims_checked")
        )
        // Why a draft was rejected is otherwise invisible; this is the place to look.
        Log.d(
            TAG,
            "Review: spoilers=${review.containsSpoilers} useful=${review.useful} accurate=${review.accurate} " +
                "needsSpoilers=${review.needsSpoilers} feedback=\"${review.feedback}\"\n${review.reasoning}"
        )
        return review
    }

    private suspend fun <T> call(what: String, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AgentException) {
        throw e
    } catch (e: PromptBlockedException) {
        Log.w(TAG, "Book question $what request was blocked", e)
        throw AgentException("The assistant's content filters blocked this question", e)
    } catch (e: ResponseStoppedException) {
        Log.w(TAG, "Book question $what response was stopped", e)
        throw AgentException("The assistant's content filters stopped the answer", e)
    } catch (e: Exception) {
        Log.e(TAG, "Book question $what request failed", e)
        throw AgentException("Couldn't reach the assistant: ${e.message}", e)
    }
}
