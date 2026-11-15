package com.indeavour.coreader.model.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionCallingConfig
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A book recommended by [BookSuggestionAgent], with the agent's one-line justification. */
data class BookSuggestion(
    val title: String,
    val author: String,
    val reason: String
)

/**
 * A read-only capability the agent may call while gathering context, such as "list my library".
 * Results are returned as JSON so the model can reason over them directly.
 */
class AgentTool(
    val name: String,
    val description: String,
    val execute: suspend () -> JsonElement
)

class AgentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A single tool-calling agent backed by Gemini through Firebase AI Logic.
 *
 * The agent decides which [tools] to call to learn about the reader (or group), then finishes by
 * calling the built-in `submit_suggestions` tool. Function calling is forced on every turn, so
 * the loop always ends in structured suggestions rather than free text that would need parsing.
 */
class BookSuggestionAgent(
    private val audience: String,
    tools: List<AgentTool>,
    private val maxSteps: Int = 6
) {
    companion object {
        private const val TAG = "BookSuggestionAgent"
        // gemini-flash-latest returns 504 timeouts for this project; flash-lite responds reliably.
        private const val MODEL_NAME = "gemini-flash-lite-latest"
        private const val SUBMIT = "submit_suggestions"
        /** Upper bound for the whole agent loop, so the UI never spins indefinitely. */
        private const val TIMEOUT_MS = 90_000L
    }

    private val toolsByName = tools.associateBy { it.name }

    private val submitDeclaration = FunctionDeclaration(
        name = SUBMIT,
        description = "Submit the final book suggestions. Call this exactly once, when done.",
        parameters = mapOf(
            "suggestions" to Schema.array(
                Schema.obj(
                    mapOf(
                        "title" to Schema.string("The book's title."),
                        "author" to Schema.string("The book's author."),
                        "reason" to Schema.string("One short sentence on why it fits, referencing their reading.")
                    )
                )
            )
        )
    )

    private val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = MODEL_NAME,
        systemInstruction = content {
            text(
                """
                You are a book recommendation agent inside a reading app, suggesting books for $audience.
                First call the available tools to learn what they have read, are reading and already plan to read.
                Then call $SUBMIT with real, published books that fit their tastes.
                Never suggest a book that already appears in any tool result, including dismissed suggestions.
                Prefer variety: mix familiar authors with new ones that share a style or theme.
                """.trimIndent()
            )
        },
        tools = listOf(
            Tool.functionDeclarations(
                tools.map { FunctionDeclaration(it.name, it.description, emptyMap()) } + submitDeclaration
            )
        ),
        toolConfig = ToolConfig(FunctionCallingConfig.any())
    )

    /**
     * Runs the agent loop and returns up to [count] suggestions.
     *
     * @param request optional free-text steer from the user, e.g. "something short and funny".
     */
    suspend fun suggest(request: String, count: Int = 5): List<BookSuggestion> {
        val task = buildString {
            append("Suggest $count books.")
            if (request.isNotBlank()) append(" The reader asked for: \"${request.trim()}\".")
        }

        return try {
            withTimeout(TIMEOUT_MS) { runAgent(task, count) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Suggestion request timed out after ${TIMEOUT_MS / 1000}s", e)
            throw AgentException("The suggestion service took too long to respond", e)
        }
    }

    private suspend fun runAgent(task: String, count: Int): List<BookSuggestion> {
        try {
            val chat = model.startChat()
            Log.d(TAG, "Sending request to $MODEL_NAME")
            var response = chat.sendMessage(task)
            repeat(maxSteps) { step ->
                val calls = response.functionCalls
                Log.d(TAG, "Step $step: model called ${calls.map { it.name }}")
                calls.firstOrNull { it.name == SUBMIT }?.let { return parseSuggestions(it.args).take(count) }
                if (calls.isEmpty()) throw AgentException("The agent stopped without suggesting any books")

                val results = calls.map { call ->
                    val output = toolsByName[call.name]
                        ?.let { tool -> runCatching { tool.execute() }.getOrElse { JsonPrimitive("Error: ${it.message}") } }
                        ?: JsonPrimitive("Error: unknown tool ${call.name}")
                    FunctionResponsePart(call.name, JsonObject(mapOf("result" to output)))
                }
                response = chat.sendMessage(content("user") { results.forEach { part(it) } })
            }
        } catch (e: AgentException) {
            throw e
        } catch (e: CancellationException) {
            // Includes timeouts; let withTimeout and viewModelScope cancellation propagate untouched.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Suggestion request failed", e)
            throw AgentException("Couldn't reach the suggestion service: ${e.message}", e)
        }
        throw AgentException("The agent took too many steps without suggesting any books")
    }

    private fun parseSuggestions(args: Map<String, JsonElement>): List<BookSuggestion> =
        (args["suggestions"] as? JsonArray).orEmpty().mapNotNull { element ->
            val fields = element as? JsonObject ?: return@mapNotNull null
            fun field(key: String) = fields[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val title = field("title")
            if (title.isBlank()) null else BookSuggestion(title, field("author"), field("reason"))
        }
}

/** Case- and punctuation-insensitive key, so "The Hobbit" and "the hobbit." count as one book. */
fun normalizedTitle(title: String): String =
    title.lowercase().filter { it.isLetterOrDigit() }
