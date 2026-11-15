package com.indeavour.coreader.model.ai

import com.indeavour.coreader.domain.AnswerRequest
import com.indeavour.coreader.domain.Exchange
import com.indeavour.coreader.domain.Passage
import com.indeavour.coreader.domain.ReadingContext
import com.indeavour.coreader.domain.ReviewRequest
import com.indeavour.coreader.domain.SpoilerPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What is sent to the two models. These do not judge the wording; they make sure a change
 * cannot quietly drop something a prompt depends on, or send the writer something it must
 * not see.
 */
class BookQuestionPromptTest {

    private val context = ReadingContext("Dune", "Frank Herbert", "Book One", 30, "The duke looked out over the sand.")
    private val readPassage = Passage("Book One", "Jessica was the duke's concubine.")
    private val unreadPassage = Passage("Book Three", "SECRET-ENDING-TEXT")

    private fun answerPrompt(
        policy: SpoilerPolicy = SpoilerPolicy.NO_SPOILERS,
        history: List<Exchange> = emptyList(),
        previousAnswer: String? = null,
        feedback: String? = null,
        passages: List<Passage> = listOf(readPassage)
    ) = BookQuestionAgent.answerPrompt(
        AnswerRequest(context, "Who is Jessica?", policy, history, passages, previousAnswer, feedback)
    )

    private fun reviewPrompt(
        policy: SpoilerPolicy = SpoilerPolicy.NO_SPOILERS,
        history: List<Exchange> = emptyList(),
        unread: List<Passage> = listOf(unreadPassage)
    ) = BookQuestionAgent.reviewPrompt(
        ReviewRequest(context, "Who is Jessica?", "She is Paul's mother.", policy, history, listOf(readPassage), unread)
    )

    // ---------------------------------------------------------------- the writer

    @Test
    fun `the writer is told the book, the reader's place, the question and the passages`() {
        val prompt = answerPrompt()
        assertTrue("\"Dune\" by Frank Herbert" in prompt)
        assertTrue("30%" in prompt)
        assertTrue("Book One" in prompt)
        assertTrue("The duke looked out over the sand." in prompt)
        assertTrue("Jessica was the duke's concubine." in prompt)
        assertTrue("Question: Who is Jessica?" in prompt)
    }

    @Test
    fun `each spoiler policy gives the writer its own rule`() {
        val strict = answerPrompt(SpoilerPolicy.NO_SPOILERS)
        val allowed = answerPrompt(SpoilerPolicy.SPOILERS_ALLOWED)
        val partial = answerPrompt(SpoilerPolicy.NO_SPOILERS_BEST_EFFORT)

        assertTrue("do not reveal or hint at anything that happens after" in strict)
        assertTrue("has asked for spoilers" in allowed)
        assertFalse("do not reveal" in allowed.lowercase())
        assertTrue("chosen not to get" in partial)
        assertTrue("Do not reveal or hint" in partial)
    }

    @Test
    fun `the writer is told when passages come from the whole book`() {
        assertTrue("already read" in answerPrompt(SpoilerPolicy.NO_SPOILERS))
        assertTrue("anywhere in the book" in answerPrompt(SpoilerPolicy.SPOILERS_ALLOWED))
    }

    @Test
    fun `the writer is told when the book's text has nothing on the question`() {
        assertTrue("No passages from the book's text could be found" in answerPrompt(passages = emptyList()))
    }

    @Test
    fun `a rewrite carries the rejected draft and the critic's feedback`() {
        val prompt = answerPrompt(previousAnswer = "She is his sister.", feedback = "She is his mother.")
        assertTrue("She is his sister." in prompt)
        assertTrue("Reviewer feedback: She is his mother." in prompt)
        assertFalse("rejected by the reviewer" in answerPrompt())
    }

    @Test
    fun `earlier exchanges are included so a follow-up makes sense`() {
        val history = listOf(Exchange("Who is Paul?", "The duke's son."))
        assertTrue("Reader: Who is Paul?" in answerPrompt(history = history))
        assertTrue("The duke's son." in reviewPrompt(history = history))
    }

    @Test
    fun `only the most recent exchanges are sent`() {
        val history = (1..10).map { Exchange("Question $it?", "Answer $it.") }
        val prompt = answerPrompt(history = history)
        assertFalse("Question 4?" in prompt)
        assertTrue("Question 5?" in prompt)
        assertTrue("Question 10?" in prompt)
    }

    @Test
    fun `an unverified earlier answer is flagged as unreliable`() {
        val history = listOf(Exchange("Who is Paul?", "The duke's brother.", verified = false))
        assertTrue("unverified and possibly wrong" in answerPrompt(history = history))
        assertFalse("unverified" in answerPrompt(history = listOf(Exchange("Who is Paul?", "The duke's son."))))
    }

    @Test
    fun `an earlier answer the reader asked spoilers for is marked`() {
        val history = listOf(Exchange("Does he die?", "Yes.", containedSpoilers = true))
        assertTrue("the reader asked for spoilers" in answerPrompt(history = history))
    }

    // ---------------------------------------------------------------- the critic

    @Test
    fun `the critic is shown the draft, the question and the passages to check it against`() {
        val prompt = reviewPrompt()
        assertTrue("She is Paul's mother." in prompt)
        assertTrue("Question: Who is Jessica?" in prompt)
        assertTrue("Jessica was the duke's concubine." in prompt)
    }

    @Test
    fun `the critic is shown matching unread text, marked as not to be revealed`() {
        val prompt = reviewPrompt()
        assertTrue("SECRET-ENDING-TEXT" in prompt)
        assertTrue("NOT read yet" in prompt)
        assertTrue("[U1]" in prompt)
    }

    @Test
    fun `the critic's prompt has no unread section when there is nothing unread to show`() {
        assertFalse("NOT read yet" in reviewPrompt(unread = emptyList()))
    }

    @Test
    fun `the critic is told whether spoilers are permitted`() {
        assertTrue("must not contain any" in reviewPrompt(SpoilerPolicy.NO_SPOILERS))
        assertTrue("must not contain any" in reviewPrompt(SpoilerPolicy.NO_SPOILERS_BEST_EFFORT))
        assertTrue("they are permitted" in reviewPrompt(SpoilerPolicy.SPOILERS_ALLOWED))
    }
}
