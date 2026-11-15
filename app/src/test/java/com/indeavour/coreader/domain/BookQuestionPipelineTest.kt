package com.indeavour.coreader.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookQuestionPipelineTest {

    private val context = ReadingContext("Dune", "Frank Herbert", "Book One", 30, "…")

    private val fine = Review(containsSpoilers = false, useful = true, needsSpoilers = false, feedback = "")
    private val spoiler = Review(containsSpoilers = true, useful = true, needsSpoilers = false, feedback = "Remove the ending.")
    private val unhelpful = Review(containsSpoilers = false, useful = false, needsSpoilers = false, feedback = "Answer the question.")
    private val onlyASpoilerWillDo = Review(containsSpoilers = false, useful = false, needsSpoilers = true, feedback = "")

    /** A pipeline whose models give scripted drafts and verdicts, recording what they were asked. */
    private class Script(drafts: List<String>, verdicts: List<Review>, maxRevisions: Int = 2) {
        val answerRequests = mutableListOf<AnswerRequest>()
        val reviewRequests = mutableListOf<ReviewRequest>()
        val searches = mutableListOf<Pair<String, SpoilerPolicy>>()
        val unreadSearches = mutableListOf<String>()
        private val drafts = drafts.iterator()
        private val verdicts = verdicts.iterator()

        val pipeline = BookQuestionPipeline(
            answer = { request -> answerRequests += request; this.drafts.next() },
            review = { request -> reviewRequests += request; this.verdicts.next() },
            // The "book" simply echoes what was searched for, so tests can see what reached whom.
            evidence = { query, policy -> searches += query to policy; listOf(Passage("found", query)) },
            unreadText = { query -> unreadSearches += query; listOf(Passage("later", "unread: $query")) },
            maxRevisions = maxRevisions
        )
    }

    private val wrong = Review(containsSpoilers = false, useful = true, needsSpoilers = false, feedback = "Jessica is his mother, not his sister.", accurate = false)

    // ---------------------------------------------------------------- checking against unread text

    @Test
    fun `the critic is shown unread text matching the draft`() {
        val script = Script(listOf("He becomes emperor."), listOf(fine))
        script.ask()
        assertEquals(listOf("He becomes emperor."), script.unreadSearches)
        assertEquals(
            listOf(Passage("later", "unread: He becomes emperor.")),
            script.reviewRequests.single().unreadPassages
        )
    }

    @Test
    fun `every rewrite is checked against unread text again`() {
        val script = Script(listOf("He becomes emperor.", "He is the duke's son."), listOf(spoiler, fine))
        script.ask()
        assertEquals(listOf("He becomes emperor.", "He is the duke's son."), script.unreadSearches)
    }

    @Test
    fun `the writer is never given unread text`() {
        val script = Script(listOf("a", "b", "c"), List(3) { spoiler })
        script.ask()
        script.answerRequests.forEach { request ->
            assertTrue(request.passages.none { it.chapter == "later" })
        }
    }

    @Test
    fun `unread text is not searched when the reader asked for spoilers`() {
        val script = Script(listOf("He becomes emperor."), listOf(spoiler))
        script.ask(SpoilerPolicy.SPOILERS_ALLOWED)
        assertTrue(script.unreadSearches.isEmpty())
        assertTrue(script.reviewRequests.single().unreadPassages.isEmpty())
    }

    @Test
    fun `unread text is still searched after the reader declined spoilers`() {
        val script = Script(listOf("The rest would be a spoiler."), listOf(fine))
        script.ask(SpoilerPolicy.NO_SPOILERS_BEST_EFFORT)
        assertEquals(1, script.unreadSearches.size)
    }

    // ---------------------------------------------------------------- checking the facts

    @Test
    fun `a wrong answer is sent back with the correction and the rewrite is shown`() {
        val script = Script(listOf("Jessica is his sister.", "Jessica is his mother."), listOf(wrong, fine))
        assertEquals(
            QuestionResult.Answered("Jessica is his mother.", containsSpoilers = false, verified = true, grounded = true),
            script.ask()
        )
        assertEquals("Jessica is his mother, not his sister.", script.answerRequests[1].feedback)
    }

    @Test
    fun `an answer that is still wrong after every rewrite is shown as unverified`() {
        val script = Script(List(3) { "Jessica is his sister." }, List(3) { wrong })
        assertEquals(
            QuestionResult.Answered("Jessica is his sister.", containsSpoilers = false, verified = false),
            script.ask()
        )
    }

    @Test
    fun `accuracy is checked even when the reader asked for spoilers`() {
        val script = Script(listOf("He dies.", "He becomes emperor."), listOf(wrong.copy(containsSpoilers = true), spoiler))
        assertEquals(
            QuestionResult.Answered("He becomes emperor.", containsSpoilers = true, verified = true, grounded = true),
            script.ask(SpoilerPolicy.SPOILERS_ALLOWED)
        )
    }

    @Test
    fun `a partial answer after declining spoilers must still be true`() {
        val script = Script(listOf("Jessica is his sister.", "Jessica is his mother."), listOf(wrong, onlyASpoilerWillDo))
        assertEquals(
            QuestionResult.Answered("Jessica is his mother.", containsSpoilers = false, verified = true, grounded = true),
            script.ask(SpoilerPolicy.NO_SPOILERS_BEST_EFFORT)
        )
    }

    @Test
    fun `the writer gets passages about the question and the critic gets passages about the draft`() {
        val script = Script(listOf("Paul is the duke's son."), listOf(fine))
        script.ask()

        assertEquals(listOf(Passage("found", "Who is Paul?")), script.answerRequests.single().passages)
        assertEquals(
            listOf(Passage("found", "Who is Paul? Paul is the duke's son.")),
            script.reviewRequests.single().passages
        )
    }

    @Test
    fun `a follow-up is searched together with the exchange it follows`() {
        val script = Script(listOf("No."), listOf(fine))
        script.ask(history = listOf(Exchange("Who is Jessica?", "Paul's mother.")))
        assertEquals("Who is Jessica? Paul's mother. Who is Paul?", script.searches.first().first)
    }

    @Test
    fun `the search is told whether the whole book may be used`() {
        val script = Script(listOf("He becomes emperor."), listOf(spoiler))
        script.ask(SpoilerPolicy.SPOILERS_ALLOWED)
        assertTrue(script.searches.all { it.second == SpoilerPolicy.SPOILERS_ALLOWED })
    }

    private fun Script.ask(
        policy: SpoilerPolicy = SpoilerPolicy.NO_SPOILERS,
        stages: MutableList<QuestionStage>? = null,
        history: List<Exchange> = emptyList(),
        isOutOfTime: () -> Boolean = { false }
    ) = runBlocking { pipeline.ask(context, "Who is Paul?", policy, history, isOutOfTime) { stages?.add(it) } }

    // ---------------------------------------------------------------- running out of time

    @Test
    fun `no rewrite is started once time is up and the draft so far is shown unverified`() {
        val script = Script(listOf("Jessica is his sister."), listOf(wrong))
        assertEquals(
            QuestionResult.Answered("Jessica is his sister.", containsSpoilers = false, verified = false),
            script.ask(isOutOfTime = { true })
        )
        assertEquals(1, script.answerRequests.size)
    }

    @Test
    fun `the first answer is always attempted even with no time left`() {
        val script = Script(listOf("He is the duke's son."), listOf(fine))
        assertTrue((script.ask(isOutOfTime = { true }) as QuestionResult.Answered).verified)
    }

    @Test
    fun `running out of time never lets a spoiler through`() {
        val script = Script(listOf("He becomes emperor."), listOf(spoiler))
        assertEquals(QuestionResult.SpoilerChoiceNeeded, script.ask(isOutOfTime = { true }))
    }

    // ---------------------------------------------------------------- how well an approval is founded

    @Test
    fun `an approval with no passages to check against is not called grounded`() {
        val pipeline = BookQuestionPipeline(
            answer = { "He is the duke's son." },
            review = { fine }
            // No evidence function: the book's text has nothing on the question.
        )
        val result = runBlocking { pipeline.ask(context, "Who is Paul?") }
        assertEquals(
            QuestionResult.Answered("He is the duke's son.", containsSpoilers = false, verified = true, grounded = false),
            result
        )
    }

    // ---------------------------------------------------------------- follow-up questions

    @Test
    fun `earlier questions and answers reach both the writer and the critic`() {
        val history = listOf(
            Exchange("Who is Jessica?", "Paul's mother."),
            Exchange("Does she survive?", "Yes.", containedSpoilers = true)
        )
        val script = Script(listOf("a", "b"), listOf(unhelpful, fine))
        script.ask(history = history)

        // Including the rewrite, which must not lose the thread either.
        assertEquals(listOf(history, history), script.answerRequests.map { it.history })
        assertEquals(listOf(history, history), script.reviewRequests.map { it.history })
    }

    @Test
    fun `a first question has no history`() {
        val script = Script(listOf("a"), listOf(fine))
        script.ask()
        assertTrue(script.answerRequests.single().history.isEmpty())
        assertTrue(script.reviewRequests.single().history.isEmpty())
    }

    // ---------------------------------------------------------------- no spoilers (the default)

    @Test
    fun `an answer the critic approves is shown as verified and spoiler-free`() {
        val script = Script(listOf(" Paul is the duke's son. "), listOf(fine))
        assertEquals(
            QuestionResult.Answered("Paul is the duke's son.", containsSpoilers = false, verified = true, grounded = true),
            script.ask()
        )
        assertEquals(1, script.answerRequests.size)
        assertNull(script.answerRequests.single().previousAnswer)
    }

    @Test
    fun `a spoiler is sent back with the critic's feedback and the rewrite is shown`() {
        val script = Script(listOf("He becomes emperor.", "He is the duke's son."), listOf(spoiler, fine))
        assertEquals(
            QuestionResult.Answered("He is the duke's son.", containsSpoilers = false, verified = true, grounded = true),
            script.ask()
        )
        val rewrite = script.answerRequests[1]
        assertEquals("He becomes emperor.", rewrite.previousAnswer)
        assertEquals("Remove the ending.", rewrite.feedback)
    }

    @Test
    fun `an unhelpful answer is rewritten too`() {
        val script = Script(listOf("I can't say.", "He is the duke's son."), listOf(unhelpful, fine))
        assertTrue((script.ask() as QuestionResult.Answered).verified)
        assertEquals("Answer the question.", script.answerRequests[1].feedback)
    }

    @Test
    fun `a question only a spoiler answers asks the reader instead of rewriting`() {
        val script = Script(listOf("I can't say without spoilers."), listOf(onlyASpoilerWillDo))
        assertEquals(QuestionResult.SpoilerChoiceNeeded, script.ask())
        assertEquals(1, script.answerRequests.size)
    }

    @Test
    fun `a spoiler that survives every rewrite is never shown`() {
        val script = Script(List(3) { "He becomes emperor." }, List(3) { spoiler })
        assertEquals(QuestionResult.SpoilerChoiceNeeded, script.ask())
        assertEquals(3, script.answerRequests.size)
    }

    @Test
    fun `a spoiler-free answer the critic never approved is shown as unverified`() {
        val script = Script(listOf("a", "b", "c"), List(3) { unhelpful })
        assertEquals(QuestionResult.Answered("c", containsSpoilers = false, verified = false), script.ask())
    }

    @Test
    fun `the number of rewrites is limited`() {
        val script = Script(listOf("a", "b"), List(2) { unhelpful }, maxRevisions = 1)
        script.ask()
        assertEquals(2, script.answerRequests.size)
        assertEquals(2, script.reviewRequests.size)
    }

    @Test
    fun `the critic sees the draft, the question and where the reader is`() {
        val script = Script(listOf("He is the duke's son."), listOf(fine))
        script.ask()
        val request = script.reviewRequests.single()
        assertEquals("He is the duke's son.", request.answer)
        assertEquals("Who is Paul?", request.question)
        assertEquals(context, request.context)
        assertEquals(SpoilerPolicy.NO_SPOILERS, request.policy)
    }

    @Test
    fun `progress is reported as answering, checking, then revising`() {
        val stages = mutableListOf<QuestionStage>()
        Script(listOf("a", "b"), listOf(unhelpful, fine)).ask(stages = stages)
        assertEquals(
            listOf(QuestionStage.ANSWERING, QuestionStage.CHECKING, QuestionStage.REVISING, QuestionStage.CHECKING),
            stages
        )
    }

    // ---------------------------------------------------------------- reader chose spoilers

    @Test
    fun `with spoilers allowed a useful spoiler is shown and labelled`() {
        val script = Script(listOf("He becomes emperor."), listOf(spoiler))
        assertEquals(
            QuestionResult.Answered("He becomes emperor.", containsSpoilers = true, verified = true, grounded = true),
            script.ask(SpoilerPolicy.SPOILERS_ALLOWED)
        )
        assertEquals(SpoilerPolicy.SPOILERS_ALLOWED, script.answerRequests.single().policy)
    }

    @Test
    fun `with spoilers allowed an unhelpful answer is still rewritten`() {
        val script = Script(listOf("I can't say.", "He becomes emperor."), listOf(unhelpful, spoiler))
        assertEquals(
            QuestionResult.Answered("He becomes emperor.", containsSpoilers = true, verified = true, grounded = true),
            script.ask(SpoilerPolicy.SPOILERS_ALLOWED)
        )
    }

    @Test
    fun `with spoilers allowed the question is never bounced back to the reader`() {
        val script = Script(List(3) { "Hard to say." }, List(3) { onlyASpoilerWillDo })
        assertEquals(
            QuestionResult.Answered("Hard to say.", containsSpoilers = true, verified = false),
            script.ask(SpoilerPolicy.SPOILERS_ALLOWED)
        )
    }

    // ---------------------------------------------------------------- reader declined spoilers

    @Test
    fun `after declining spoilers a partial spoiler-free answer is enough`() {
        val script = Script(listOf("So far he is the duke's son; the rest would be a spoiler."), listOf(onlyASpoilerWillDo))
        assertEquals(
            QuestionResult.Answered(
                "So far he is the duke's son; the rest would be a spoiler.",
                containsSpoilers = false,
                verified = true,
                grounded = true
            ),
            script.ask(SpoilerPolicy.NO_SPOILERS_BEST_EFFORT)
        )
    }

    @Test
    fun `after declining spoilers a spoiler is rewritten`() {
        val script = Script(listOf("He becomes emperor.", "The rest would be a spoiler."), listOf(spoiler, unhelpful))
        assertEquals(
            QuestionResult.Answered("The rest would be a spoiler.", containsSpoilers = false, verified = true, grounded = true),
            script.ask(SpoilerPolicy.NO_SPOILERS_BEST_EFFORT)
        )
    }

    @Test
    fun `after declining spoilers a spoiler that survives every rewrite is replaced`() {
        val script = Script(List(3) { "He becomes emperor." }, List(3) { spoiler })
        assertEquals(
            QuestionResult.Answered(BookQuestionPipeline.NOTHING_SAFE_TO_SAY, containsSpoilers = false, verified = true),
            script.ask(SpoilerPolicy.NO_SPOILERS_BEST_EFFORT)
        )
    }
}
