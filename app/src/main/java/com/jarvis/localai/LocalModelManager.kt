package com.jarvis.localai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local-only LLM manager. The model file is selected by the user and copied
 * into app-private storage. No remote AI endpoint or API key is used.
 */
class LocalModelManager(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun loadModel(source: File) = withContext(Dispatchers.IO) {
        check(source.exists() && source.length() > 100_000_000L) {
            "Selected file does not look like a full .litertlm model."
        }
        close()
        val localModel = File(context.filesDir, "jarvis_model.litertlm")
        source.inputStream().use { input ->
            localModel.outputStream().use { output -> input.copyTo(output) }
        }
        val newEngine = Engine(
            EngineConfig(
                modelPath = localModel.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        )
        try {
            newEngine.initialize()
            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are JARVIS, a friendly and intelligent personal assistant. " +
                        "Answer clearly and helpfully. The user may speak English, Urdu, or Roman Urdu. " +
                        "Reply in the same language as the user. Keep spoken answers reasonably concise."
                    )
                )
            )
            engine = newEngine
            conversation = newConversation
        } catch (t: Throwable) {
            // Do not close an engine whose initialization failed.
            throw t
        }
    }

    suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        val activeConversation = conversation
            ?: throw IllegalStateException("Load a local AI model first.")
        val chunks = activeConversation.sendMessageAsync(prompt).toList()
        chunks.joinToString(separator = "") { it.text }.ifBlank {
            "I could not create a response. Please try a shorter question."
        }
    }

    fun isReady(): Boolean = conversation != null

    fun close() {
        try { conversation?.close() } catch (_: Exception) {}
        conversation = null
        try { engine?.close() } catch (_: Exception) {}
        engine = null
    }
}
