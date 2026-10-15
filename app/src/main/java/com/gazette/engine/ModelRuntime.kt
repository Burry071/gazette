package com.gazette.engine

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SessionConfig
import com.gazette.pipeline.LlmEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

// Loads the Gemma .litertlm file from app storage and adapts LiteRT-LM to the
// pipeline's LlmEngine. The model file arrives by adb push (see README); the
// app itself never downloads anything and holds no INTERNET permission.
object ModelRuntime {

    const val MODEL_FILENAME = "model.litertlm"

    fun start(context: Context) {
        CoroutineScope(Dispatchers.Default).launch { load(context.applicationContext) }
    }

    private fun load(context: Context) {
        val internal = File(context.filesDir, MODEL_FILENAME)
        val external = context.getExternalFilesDir(null)?.let { File(it, MODEL_FILENAME) }
        if (!internal.exists()) {
            if (external?.exists() != true) return
            LedgerEngine.modelStatus.value = ModelStatus.LOADING
            // FUSE paths are not safe to mmap; own the file in internal storage first.
            external.copyTo(internal, overwrite = true)
        }
        LedgerEngine.modelStatus.value = ModelStatus.LOADING
        try {
            val engine = Engine(
                EngineConfig(
                    modelPath = internal.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = 1024,
                )
            )
            engine.initialize()
            LedgerEngine.engineProvider = { Adapter(engine) }
            LedgerEngine.modelStatus.value = ModelStatus.READY
        } catch (t: Throwable) {
            LedgerEngine.engineProvider = { null }
            LedgerEngine.modelStatus.value = ModelStatus.MISSING
        }
    }

    private class Adapter(private val engine: Engine) : LlmEngine {

        override suspend fun generate(prompt: String, maxNewTokens: Int, temperature: Double): String {
            // A fresh session per call: sessions accumulate context, and each
            // pipeline generation must start clean. Fresh seed per call so the
            // vote samples differ from one another.
            val session = engine.createSession(
                SessionConfig(
                    samplerConfig = SamplerConfig(
                        topK = 40,
                        topP = 0.95,
                        temperature = temperature,
                        seed = Random.nextInt(),
                    )
                )
            )
            return try {
                // LiteRT-LM 0.18 has no per-call token cap; prompts end at EOS.
                session.generateContent(listOf(InputData.Text(prompt)))
            } finally {
                session.close()
            }
        }
    }
}
