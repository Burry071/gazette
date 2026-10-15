package com.gazette.engine

import android.content.Context
import android.net.Uri
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
// pipeline's LlmEngine. The file arrives either by adb push into external
// files (see README) or via the in-app SAF picker; the app itself never
// downloads anything and holds no INTERNET permission.
object ModelRuntime {

    const val MODEL_FILENAME = "model.litertlm"

    fun start(context: Context) {
        CoroutineScope(Dispatchers.Default).launch { load(context.applicationContext) }
    }

    // User picked a .litertlm through the system file picker (no storage
    // permission needed). Copy it into internal storage, then load.
    fun importFrom(context: Context, uri: Uri) {
        CoroutineScope(Dispatchers.Default).launch {
            val app = context.applicationContext
            val internal = File(app.filesDir, MODEL_FILENAME)
            LedgerEngine.modelStatus.value = ModelStatus.LOADING
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    internal.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Picker returned no stream")
            } catch (t: Throwable) {
                internal.delete()
                LedgerEngine.modelStatus.value = ModelStatus.MISSING
                return@launch
            }
            load(app)
        }
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
            // A bad or foreign file must not wedge setup forever: remove it so
            // the next push or picker pick gets a clean start.
            internal.delete()
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
