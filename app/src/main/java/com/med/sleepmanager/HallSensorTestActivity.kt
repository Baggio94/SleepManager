package com.med.sleepmanager

import android.content.Context
import android.hardware.input.InputManager
import android.os.Bundle
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

class HallSensorTestActivity : ComponentActivity() {

    data class ProbeResult(
        val directOpen: String,
        val inputManager: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SleepManagerTheme {
                HallSensorTestScreen()
            }
        }
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
                0 // Linux SW_LID
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
                                "What this tests",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "1. Whether the real SleepManager app process can open the Thor hall-switch input device.\n" +
                                    "2. Whether Android's internal InputManager API can report SW_LID without root, ADB or Shizuku.",
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
                        Text("Run hall sensor probe")
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

                    item {
                        Text(
                            "Run the probe once with the lid open. If InputManager succeeds, close the Thor while connected and reproduce the trigger wake issue, then reopen it and run the probe again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
