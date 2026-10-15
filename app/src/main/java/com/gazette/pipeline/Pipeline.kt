package com.gazette.pipeline

// Layers 1-4 orchestration: gate -> classify votes -> extraction votes ->
// reconciliation. Mirrors scripts/extract_test_v3.js with reconcile.js folded
// in as the final stage. The engine is injected so the UI layer owns model
// lifecycle (LiteRT-LM on device; scripted fakes in tests).
interface LlmEngine {
    suspend fun generate(prompt: String, maxNewTokens: Int, temperature: Double): String
}

class Pipeline(
    private val engine: LlmEngine,
    private val votes: Int = 3,
    private val temperature: Double = 0.4,
) {
    suspend fun process(text: String): Reconciler.Reconciled {
        val gated = !Gate.hasAmount(text)
        val clsVotes = mutableListOf<String>()
        val amtVotes = mutableListOf<Double?>()
        val dirVotes = mutableListOf<String?>()
        try {
            if (!gated) {
                repeat(votes) {
                    clsVotes += VoteParsing.parseClass(engine.generate(Prompts.classify(text), 4, temperature))
                }
                if (clsVotes.count { it == "P" } >= (votes + 1) / 2) {
                    repeat(votes) {
                        val raw = engine.generate(Prompts.extract(text), 24, temperature)
                        amtVotes += VoteParsing.parseAmount(raw)
                        dirVotes += VoteParsing.parseDir(raw)
                    }
                }
            }
        } catch (e: Exception) {
            // Fail-safe: an engine failure is "not sure", and not sure means OTHER.
            return Reconciler.Reconciled(false, null, null, gated, false, false, false, false, engineError = true)
        }
        return Reconciler.reconcile(text, gated, clsVotes, amtVotes, dirVotes)
    }
}
