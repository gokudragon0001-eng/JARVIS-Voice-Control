package com.jarvis.localai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val Bg = Color(0xFF050B14)
private val Panel = Color(0xFF0B1928)
private val Cyan = Color(0xFF36D9FF)
private val Muted = Color(0xFF8AA9BA)

data class ChatLine(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var modelManager: LocalModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelManager = LocalModelManager(applicationContext)
        tts = TextToSpeech(this, this)
        setContent {
            JarvisApp(
                modelManager = modelManager,
                onSpeak = { text -> tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_voice") }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    override fun onDestroy() {
        modelManager.close()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}

@Composable
private fun JarvisApp(modelManager: LocalModelManager, onSpeak: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var speechCommandPending by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("LOCAL AI • MODEL NOT LOADED") }
    var modelName by remember { mutableStateOf("No model selected") }
    val chat = remember {
        mutableStateListOf(
            ChatLine("Welcome. I am JARVIS. Select a compatible local AI model to begin. No API key is needed.", false)
        )
    }

    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = "COPYING AND LOADING LOCAL MODEL..."
                try {
                    val fileName = queryDisplayName(context, uri) ?: "selected_model.litertlm"
                    val temp = File(context.cacheDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { src ->
                        temp.outputStream().use { dst -> src.copyTo(dst) }
                    } ?: error("Could not open the selected model file.")
                    modelManager.loadModel(temp)
                    ready = true
                    modelName = fileName
                    status = "JARVIS ONLINE • RUNNING ON THIS PHONE"
                    chat.add(ChatLine("Local AI model loaded successfully. I am ready.", false))
                } catch (e: Exception) {
                    ready = false
                    status = "MODEL LOAD FAILED"
                    chat.add(ChatLine("I could not load this model: ${e.message ?: "Unknown error"}. Check that it is a compatible .litertlm file and your phone has enough memory.", false))
                } finally {
                    busy = false
                }
            }
        }
    }

    val speechPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) {
            input = words
            speechCommandPending = true
        }
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openSpeech(speechPicker)
        else status = "MICROPHONE PERMISSION DENIED"
    }

    fun listen() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            openSpeech(speechPicker)
        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun send() {
        val prompt = input.trim()
        if (prompt.isEmpty() || busy) return
        val phoneAction = VoiceCommandProcessor.execute(context, prompt)
        if (phoneAction != null) {
            input = ""
            chat.add(ChatLine(prompt, true))
            chat.add(ChatLine(phoneAction, false))
            onSpeak(phoneAction)
            status = "PHONE COMMAND EXECUTED"
            return
        }
        if (!ready) {
            status = "LOAD A LOCAL MODEL FIRST"
            return
        }
        input = ""
        chat.add(ChatLine(prompt, true))
        busy = true
        status = "JARVIS IS THINKING LOCALLY..."
        scope.launch {
            try {
                val answer = modelManager.ask(prompt)
                chat.add(ChatLine(answer, false))
                onSpeak(answer)
                status = "LOCAL AI READY"
            } catch (e: Exception) {
                chat.add(ChatLine("Sorry, local generation failed: ${e.message ?: "Unknown error"}", false))
                status = "GENERATION ERROR"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(speechCommandPending, input, busy) {
        if (speechCommandPending && input.isNotBlank() && !busy) {
            speechCommandPending = false
            send()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, background = Bg, surface = Panel,
        onPrimary = Bg, onBackground = Color.White, onSurface = Color.White
    )) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF071827), Bg)))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("J.A.R.V.I.S.", color = Cyan, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("LOCAL PERSONAL AI", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                    Text("NO API KEY", color = Color(0xFF83E6A1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Box(Modifier.fillMaxWidth().height(142.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(118.dp).border(2.dp, Cyan.copy(alpha = .75f), CircleShape).padding(9.dp)
                            .border(1.dp, Cyan.copy(alpha = .4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(75.dp).background(Cyan.copy(alpha = .08f), CircleShape)
                                .border(1.dp, Cyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("AI", color = Cyan, fontSize = 25.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
                        }
                    }
                }

                Text(status, color = Cyan, fontSize = 10.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("WHATSAPP CONTROL", color = Muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        try { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
                    }) { Text("ENABLE ASSIST", color = Cyan, fontSize = 10.sp) }
                }
                Row(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
                        .border(1.dp, Cyan.copy(alpha = .25f), RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("LOCAL MODEL", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(modelName, color = Muted, fontSize = 10.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12364A)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text("SELECT MODEL", fontSize = 10.sp, color = Color.White) }
                }
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(chat) { line ->
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = if (line.fromUser) Alignment.End else Alignment.Start
                        ) {
                            Text(if (line.fromUser) "YOU" else "JARVIS", color = if (line.fromUser) Color(0xFFFFB45B) else Cyan, fontSize = 9.sp, letterSpacing = 1.4.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                line.text, color = Color(0xFFE4F5FF), fontSize = 14.sp,
                                modifier = Modifier.widthIn(max = 310.dp)
                                    .background(if (line.fromUser) Color(0xFF183047) else Panel, RoundedCornerShape(12.dp))
                                    .border(1.dp, Cyan.copy(alpha = .12f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f).background(Panel, RoundedCornerShape(12.dp)).padding(13.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        decorationBox = { inner ->
                            if (input.isEmpty()) Text("Ask JARVIS...", color = Muted, fontSize = 14.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.width(5.dp))
                    Button(
                        onClick = { listen() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12364A)),
                        contentPadding = PaddingValues(11.dp)
                    ) { Text("🎙", fontSize = 17.sp) }
                    Spacer(Modifier.width(5.dp))
                    Button(
                        onClick = { send() }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 13.dp)
                    ) { Text(if (busy) "..." else "SEND", color = Bg, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(5.dp))
                Text("ON-DEVICE AI • VOICE INPUT • SPOKEN ANSWERS", color = Muted, fontSize = 9.sp, letterSpacing = .7.sp)
            }
        }
    }
}

private fun openSpeech(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
    }
    try { launcher.launch(intent) } catch (_: Exception) {}
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
