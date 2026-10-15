package com.gazette

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.gazette.engine.LedgerEngine
import com.gazette.ui.GazetteTheme
import com.gazette.ui.StatementScreen
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {

    private val listenerGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LedgerEngine.init(applicationContext)
        listenerGranted.value = isListenerGranted()
        setContent {
            val entriesFlow = remember {
                LedgerEngine.db?.ledgerDao()?.all() ?: flowOf(emptyList())
            }
            val entries by entriesFlow.collectAsState(initial = emptyList())
            val reading by LedgerEngine.reading.collectAsState()
            val readingText by LedgerEngine.readingText.collectAsState()
            val modelStatus by LedgerEngine.modelStatus.collectAsState()

            GazetteTheme {
                StatementScreen(
                    entries = entries,
                    reading = reading,
                    readingText = readingText,
                    modelStatus = modelStatus,
                    listenerGranted = listenerGranted.value,
                    onEnableAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerGranted.value = isListenerGranted()
    }

    private fun isListenerGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}
