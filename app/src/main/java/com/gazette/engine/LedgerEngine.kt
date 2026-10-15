package com.gazette.engine

import android.content.Context
import com.gazette.data.LedgerDb
import com.gazette.data.LedgerEntry
import com.gazette.pipeline.LlmEngine
import com.gazette.pipeline.Pipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ModelStatus { MISSING, LOADING, READY }

// Process-wide glue between the listener service, the pipeline, and storage.
// The queue is unbounded and serialized: one model session, one item at a time.
object LedgerEngine {

    data class Candidate(val text: String, val packageName: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<Candidate>(Channel.UNLIMITED)

    private val _reading = MutableStateFlow(false)
    val reading: StateFlow<Boolean> = _reading

    private val _readingText = MutableStateFlow<String?>(null)
    val readingText: StateFlow<String?> = _readingText

    val modelStatus = MutableStateFlow(ModelStatus.MISSING)

    // Set by the model runtime layer once LiteRT-LM lands; null = no model file.
    var engineProvider: suspend () -> LlmEngine? = { null }

    // On-device latency knob: 3 votes is the calibrated default, 1 is the
    // single-sample experiment.
    @Volatile
    var pipelineVotes: Int = 3

    @Volatile
    var db: LedgerDb? = null
        private set

    fun init(context: Context) {
        if (db != null) return
        db = LedgerDb.get(context)
        scope.launch {
            for (candidate in queue) process(candidate)
        }
    }

    fun submit(text: String, packageName: String) {
        queue.trySend(Candidate(text, packageName))
    }

    private suspend fun process(candidate: Candidate) {
        _reading.value = true
        _readingText.value = candidate.text
        try {
            val engine = engineProvider() ?: return
            val result = Pipeline(engine, votes = pipelineVotes).process(candidate.text)
            val amount = result.amount
            val direction = result.direction
            if (result.transaction && amount != null && direction != null) {
                db?.ledgerDao()?.insert(
                    LedgerEntry(
                        epochMs = System.currentTimeMillis(),
                        amount = amount,
                        direction = direction,
                        originalText = candidate.text,
                        packageName = candidate.packageName,
                    )
                )
            }
        } finally {
            _reading.value = false
            _readingText.value = null
        }
    }
}
