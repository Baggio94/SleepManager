package com.med.sleepmanager

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HallSensorTestActivity : ComponentActivity() {

    data class ProbeResult(
        val directOpen: String,
        val inputManager: String
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val liveLidState = mutableStateOf("NOT STARTED")
    private val liveHistory = mutableStateOf("No SW_LID events captured yet")

    @Volatile
    private var monitorRunning = false
    private var monitorStream: FileInputStream? = null
    private var monitorThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SleepManagerTheme {
                HallSensorTestScreen()
            }
        }
    }

    override fun onDestroy() {
        stopMonitor()
        super.onDestroy()
    }

    private fun runProbe(): ProbeResult {
        val directOpenResult = try {
            FileInputStream("/dev/input/event1").use { }
            "SUCCESS — /dev/input/event1 opened from the real app process"
        } catch (t: Throwable) {
            "FAILED — ${t.javaClass.simpleName}: ${t.message ?: "no message"}"
        }

        val inputManagerResult = try {
            val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
            val method = InputManager::class.java.getDeclaredMethod(
                "getSwitchState",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true

            val value = method.invoke(
                inputManager,
                -1,
                InputDevice.SOURCE_ANY,
                0
            ) as Int

            val meaning = when (value) {
                1 -> "CLOSED"
                0 -> "OPEN"
                else -> "UNKNOWN"
            }

            "SUCCESS — value=$value ($meaning)"
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            "FAILED — ${cause.javaClass.simpleName}: ${cause.message ?: "no message"}"
        }

        return ProbeResult(
            directOpen = directOpenResult,
            inputManager = inputManagerResult
        )
    }

    private fun startMonitor() {
        if (monitorRunning) return

        monitorRunning = true
        liveLidState.value = "WAITING FOR SW_LID EVENT"
        liveHistory.value = "Monitor started. Close and reopen the Thor."

        monitorThread = Thread {
            try {
                val stream = FileInputStream("/dev/input/event1")
                monitorStream = stream

                val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
                val eventSize = if (is64Bit) 24 else 16
                val payloadOffset = if (is64Bit) 16 else 8
                val buffer = ByteArray(eventSize)

                while (monitorRunning) {
                    var offset = 0
                    while (offset < eventSize && monitorRunning) {
                        val count = stream.read(buffer, offset, eventSize - offset)
                        if (count < 0) throw IllegalStateException("Unexpected EOF")
                        offset += count
                    }
                    if (!monitorRunning) break

                    val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                    byteBuffer.position(payloadOffset)

                    val type = byteBuffer.short.toInt() and 0xffff
                    val code = byteBuffer.short.toInt() and 0xffff
                    val value = byteBuffer.int

                    // Linux input-event constants:
                    // EV_SW = 0x05, SW_LID = 0x00
                    if (type == 0x05 && code == 0x00) {
                        val state = when (value) {
                            1 -> "CLOSED"
                            0 -> "OPEN"
                            else -> "UNKNOWN ($value)"
                        }

                        val timestamp = SimpleDateFormat(
                            "HH:mm:ss.SSS",
                            Locale.getDefault()
                        ).format(Date())

                        mainHandler.post {
                            liveLidState.value = state
                            val previous = liveHistory.value
                            liveHistory.value = if (previous.startsWith("Monitor started")) {
                                "$timestamp  SW_LID → $state"
                            } else {
                                (previous + "\n" + "$timestamp  SW_LID → $state")
                                    .lineSequence()
                                    .toList()
                                    .takeLast(8)
                                    .joinToString("\n")
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (monitorRunning) {
                    mainHandler.post {
                        liveLidState.value =
                            "ERROR — ${t.javaClass.simpleName}: ${t.message ?: "no message"}"
                    }
                }
            } finally {
                try {
                    monitorStream?.close()
                } catch (_: Throwable) {
                }
                monitorStream = null
                monitorRunning = false
            }
        }.apply {
            name = "HallSwitchMonitor"
            isDaemon = true
            start()
        }
    }

    private fun stopMonitor() {
        monitorRunning = false
        try {
            monitorStream?.close()
        } catch (_: Throwable) {
        }
        monitorStream = null
        monitorThread = null
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HallSensorTestScreen() {
        val result = remember { mutableStateOf<ProbeResult?>(null) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "SleepManager Hall Test",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "AYN Thor closed-lid diagnostic",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Step 1 — Access probe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Checks direct access to the Thor hall-switch device and the optional Android InputManager path.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = { result.value = runProbe() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run access probe")
                    }
                }

                result.value?.let { probe ->
                    item {
                        ResultCard(
                            title = "Direct /dev/input access",
                            value = probe.directOpen
                        )
                    }

                    item {
                        ResultCard(
                            title = "Android InputManager",
                            value = probe.inputManager
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Step 2 — Live SW_LID monitor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Start while the Thor is open. Then close it, press a trigger immediately to reproduce the wake bug, wait a few seconds, and reopen it.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = { startMonitor() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start live lid monitor")
                    }
                }

                item {
                    ResultCard(
                        title = "Current captured lid state",
                        value = liveLidState.value
                    )
                }

                item {
                    ResultCard(
                        title = "SW_LID event history",
                        value = liveHistory.value
                    )
                }

                item {
                    Text(
                        "If the history shows CLOSED followed by OPEN after the test, SleepManager can track the lid directly from the real app process without root, ADB or Shizuku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
