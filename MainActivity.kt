package com.example.braillink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Compose basics
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// Compose Material
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text

// UI utilities
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Time formatting (backwards compatible)
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Coroutines
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BraillinkControlScreen()
        }
    }
}

@Composable
fun BraillinkControlScreen() {
    var status by remember { mutableStateOf("Idle") }
    val logs = remember { mutableStateListOf<String>() }
    var lastSent by remember { mutableStateOf<String>("") }

    // coroutine scope for sending chars
    val scope = rememberCoroutineScope()
    var sendJob by remember { mutableStateOf<Job?>(null) }

    fun appendLog(s: String) {
        pushLog(logs, s)
    }

    // Simulated capture text (change this to test other inputs)
    val simulatedCapturedText = remember { "Hello Braillink!" }

    // Delay between characters (ms) — tune as needed
    val charDelayMs = 300L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top status
        Text(text = "Status: $status", fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))

        // Buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (sendJob != null) {
                        // already sending
                        appendLog("Already sending — press STOP to cancel")
                        status = "Sending (already in progress)"
                        return@Button
                    }
                    status = "Capturing..."
                    appendLog("Capture pressed")
                    // Start simulated capture -> send char by char
                    sendJob = scope.launch {
                        status = "Sending characters..."
                        // Get real captured text from the accessibility service
                        val textToSend = ScreenTextAccessibilityService.getLatestText()
                        if (textToSend.isBlank()) {
                            appendLog("No accessible text found on screen. Try focusing the text or enable OCR fallback.")
                            status = "Idle"
                            sendJob = null
                            return@launch
                        }

                        appendLog("Captured text: \"$textToSend\"")
                        for (ch in textToSend) {
                            if (!isActive) break
                            val s = ch.toString()
                            appendLog("Sending: '$s'")
                            lastSent = s
                            delay(charDelayMs)
                        }
                        appendLog("Send finished")
                        status = "Idle"
                        sendJob = null
                    }

                },
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E7D32))
            ) {
                Text(text = "CAPTURE", color = Color.White, fontSize = 18.sp)
            }

            Button(
                onClick = {
                    appendLog("Stop pressed")
                    // Cancel the send job if running
                    sendJob?.cancel()
                    sendJob = null
                    status = "Stopped"
                    lastSent = ""
                },
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFC62828))
            ) {
                Text(text = "STOP", color = Color.White, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Small status area
        Text(text = "Last sent: ${if (lastSent.isEmpty()) "—" else lastSent}", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Tip: simulated text = \"$simulatedCapturedText\"", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(12.dp))

        // Logs (most recent first)
        Text(text = "Activity Log:", fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(color = Color(0xFFF5F5F5))
            .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                Text(text = "No activity yet", color = Color.Gray)
            } else {
                LazyColumn {
                    items(items = logs) { item ->
                        Text(text = item, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
}

/** Backwards-compatible log helper that appends a timestamped message to the UI list. */
fun pushLog(logs: MutableList<String>, s: String) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val time = sdf.format(Date())
    logs.add(0, "$time — $s")
}
