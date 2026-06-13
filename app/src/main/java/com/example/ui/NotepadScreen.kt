package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.SpeechManager
import com.example.tts.VoiceOption
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen(
    speechManager: SpeechManager,
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("notepad_prefs", Context.MODE_PRIVATE) }

    // Restore text size preference (default 18sp)
    var editorTextSize by remember { mutableStateOf(sharedPrefs.getFloat("text_size", 18f)) }

    // Notepad text state
    var textInput by remember {
        mutableStateOf(sharedPrefs.getString("note_text", "") ?: "")
    }

    // Saved state tracker
    var isSavedStatus by remember { mutableStateOf(true) }

    // TTS Flows
    val isTtsInitialized by speechManager.isInitialized.collectAsState()
    val isPlaying by speechManager.isPlaying.collectAsState()
    val currentHighlightRange by speechManager.currentHighlightRange.collectAsState()
    val availableVoices by speechManager.availableVoices.collectAsState()
    val selectedVoice by speechManager.selectedVoice.collectAsState()
    val ttsSpeechRate by speechManager.speechRate.collectAsState()
    val currentSegmentIdx by speechManager.currentSegmentIndex.collectAsState()
    val totalSegmentsCount by speechManager.totalSegmentsCount.collectAsState()

    var showVoiceDropdown by remember { mutableStateOf(false) }

    // Handle initial sample text loading
    val sampleText = """歡迎使用朗讀記事本！

這是一個支援微軟 Edge 網路高品質天然語音的朗讀記事本。
您可以在此處直接輸入，或點擊下方的「剪貼簿貼上」按鈕快速載入文字。

本軟體具備以下特色功能：
1. 即時朗讀：點擊最下方的播放鈕，流暢朗讀。
2. 卡拉OK單字高亮：朗讀時，文字框會同步顯示當前正在朗讀的字句。
3. 播放導航：支援首尾句、前一句與後一句等流暢句讀跳轉按鈕。
4. 語速無級調整：支援 0.5x 到 2.0x 快捷鍵。
5. 多種高品質語音：支援台語、國語女男聲並標註 [網路高品質] 與 [本地合成] 引擎。
6. 深淺色模式切換。

請點擊下方播放按鈕，體驗高品質語音朗讀！"""

    // Auto-save logic
    LaunchedEffect(textInput) {
        sharedPrefs.edit().putString("note_text", textInput).apply()
        speechManager.setSourceText(textInput)
        isSavedStatus = true
    }

    // Save text size preference
    LaunchedEffect(editorTextSize) {
        sharedPrefs.edit().putFloat("text_size", editorTextSize).apply()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Icon Frame similar to custom circular VoicePad asset
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "VoicePad logo icon",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "VoicePad",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSavedStatus) Color(0xFF2E7D32) else Color(0xFFEF6C00))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSavedStatus) "已在本地安全存檔" else "編輯中...",
                                    style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Sleek rounded toggle for Dark Theme
                    IconButton(
                        onClick = { onDarkThemeChange(!isDarkTheme) },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // High fidelity modern reader panel
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    // Voice selection trigger & EQ Visualizer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "語音引擎",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = selectedVoice?.displayName ?: "尚未偵測到語音...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // EQ Waveform Bouncing Visualizer
                        AnimatedVisibility(
                            visible = isPlaying,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            BouncingAudioVisualizer()
                        }
                    }

                    // Progress slider line with clean feedback
                    if (totalSegmentsCount > 0) {
                        val progress = if (totalSegmentsCount > 0) currentSegmentIdx.toFloat() / totalSegmentsCount else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                strokeCap = strokeCap(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "進度：句 ${currentSegmentIdx + 1} / $totalSegmentsCount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}% 已朗讀",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Obvious Button: Configure/Select voice dropdown trigger
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showVoiceDropdown = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("voice_select_button"),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Voice Select",
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = selectedVoice?.displayName ?: "設定高品質朗讀發音人...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand Voices"
                                )
                            }
                        }

                        // Dropdown selection list
                        DropdownMenu(
                            expanded = showVoiceDropdown,
                            onDismissRequest = { showVoiceDropdown = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .heightIn(max = 280.dp)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            if (availableVoices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("搜尋系統可用高品質語音...") },
                                    onClick = {}
                                )
                            } else {
                                availableVoices.forEach { voice ->
                                    val isSelected = selectedVoice?.id == voice.id
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RecordVoiceOver,
                                                contentDescription = "Check",
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        },
                                        text = {
                                            Column {
                                                Text(
                                                    text = voice.displayName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "系統代碼: ${voice.id}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                )
                                            }
                                        },
                                        onClick = {
                                            speechManager.selectVoice(voice)
                                            showVoiceDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Interactive quick chips for speeds
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed rate icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "語速調整",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${Math.round(ttsSpeechRate * 100) / 100.0}x 主觀語速",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rates = listOf(0.5f, 0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
                            rates.forEach { rate ->
                                val scoreActive = Math.abs(ttsSpeechRate - rate) < 0.05f
                                FilterChip(
                                    selected = scoreActive,
                                    onClick = { speechManager.setSpeechRate(rate) },
                                    label = {
                                        Text(
                                            text = if (rate == 1.0f) "1.0x (標準)" else "${rate}x",
                                            fontSize = 11.sp,
                                            fontWeight = if (scoreActive) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = null,
                                    modifier = Modifier.testTag("speed_chip_$rate")
                                )
                            }
                        }
                    }

                    // READ ALOUD prominent controller bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous sentence navigation
                        IconButton(
                            onClick = { speechManager.previousSegment() },
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .testTag("rewind_sentence_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Sentence",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Prominent giant READ ALOUD primary trigger button
                        Button(
                            onClick = {
                                if (textInput.isBlank()) {
                                    Toast.makeText(context, "請點擊上方貼上，或輸入欲朗讀的文字！", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (isPlaying) {
                                        speechManager.pause()
                                    } else {
                                        speechManager.setSourceText(textInput)
                                        speechManager.play()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("play_pause_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                    contentDescription = "Speak trigger icon",
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = if (isPlaying) "暫停朗讀 PAUSE" else "開始朗讀 READ ALOUD",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Stop Speaking
                        IconButton(
                            onClick = { speechManager.stop() },
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .testTag("stop_speaking_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Speech",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        // Next sentence navigation
                        IconButton(
                            onClick = { speechManager.nextSegment() },
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .testTag("forward_sentence_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Sentence",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            
            // Upper secondary actions bar (Clear, Sample loading, Text-scaling)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear note button
                FilledTonalButton(
                    onClick = {
                        textInput = ""
                        isSavedStatus = false
                        speechManager.stop()
                        Toast.makeText(context, "已快速清除記事本內容", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(44.dp)
                        .testTag("clear_notepad_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear note content",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "清除文字",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sample insertion button
                FilledTonalButton(
                    onClick = {
                        textInput = sampleText
                        isSavedStatus = false
                        speechManager.stop()
                        Toast.makeText(context, "已成功載入範例！請點擊下方播放鈕朗讀", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("load_sample_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Load Note sample",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "載入範例",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Font adjustment block
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = { editorTextSize = (editorTextSize - 2f).coerceAtLeast(12f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease text point size",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "${editorTextSize.toInt()}pt",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { editorTextSize = (editorTextSize + 2f).coerceAtMost(32f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase text point size",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Live status indication: Microsoft Read Aloud Engine Active status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pulsing green/primary status dot
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse_engine_transition")
                    val alphaScale by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_pulsing"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = alphaScale)
                            )
                    )
                    Text(
                        text = "MICROSOFT EDGE NATURAL ENGINE READY",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    )
                }
                
                Text(
                    text = "${textInput.length} 字",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                )
            }

            // Immersive Text Editor box featuring Floating glass Paste action button
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant, // filled slate grey/obsidian variant
                border = null,
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    val styledText = remember(textInput, currentHighlightRange, isPlaying, isDarkTheme) {
                        try {
                            buildAnnotatedString {
                                val highlight = currentHighlightRange
                                if (isPlaying && highlight != null && highlight.first < textInput.length && highlight.second <= textInput.length) {
                                    val start = highlight.first
                                    val end = highlight.second

                                    // Append before highlight
                                    if (start > 0) {
                                        append(textInput.substring(0, start))
                                    }

                                    // Append highlighted text
                                    withStyle(
                                        style = SpanStyle(
                                            background = if (isDarkTheme) TextHighlightDark else TextHighlightLight,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) Color.Black else Color.Black
                                        )
                                    ) {
                                        append(textInput.substring(start, end))
                                    }

                                    // Append after highlight
                                    if (end < textInput.length) {
                                        append(textInput.substring(end))
                                    }
                                } else {
                                    append(textInput)
                                }
                            }
                        } catch (e: Exception) {
                            buildAnnotatedString { append(textInput) }
                        }
                    }

                    // Render editor
                    BasicTextField(
                        value = textInput,
                        onValueChange = {
                            textInput = it
                            isSavedStatus = false
                        },
                        textStyle = TextStyle(
                            fontSize = editorTextSize.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = (editorTextSize * 1.5).sp,
                            fontFamily = FontFamily.SansSerif
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(end = 4.dp)
                            .testTag("notepad_text_editor"),
                        decorationBox = { innerTextField ->
                            if (textInput.isEmpty()) {
                                Text(
                                    text = "在此輸入、貼上文字開始體驗語音朗讀...",
                                    fontSize = editorTextSize.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    lineHeight = (editorTextSize * 1.5).sp,
                                    modifier = Modifier.padding(top = 36.dp) // Leave safe offset for floating badge
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 36.dp) // safe offset downward
                            ) {
                                if (isPlaying && currentHighlightRange != null) {
                                    Text(
                                        text = styledText,
                                        fontSize = editorTextSize.sp,
                                        lineHeight = (editorTextSize * 1.5).sp,
                                        fontFamily = FontFamily.SansSerif,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    innerTextField()
                                }
                            }
                        }
                    )

                    // Floating Glass Action Badge on top right of Editor: Paste Text
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clickable {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    if (clipboard.hasPrimaryClip()) {
                                        val item = clipboard.primaryClip?.getItemAt(0)
                                        val text = item?.text?.toString()
                                        if (!text.isNullOrBlank()) {
                                            textInput = text
                                            isSavedStatus = false
                                            Toast.makeText(context, "已成功貼上剪貼簿內容！", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "剪貼簿內沒有文字喔", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "剪貼簿是空的", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "讀取剪貼簿出錯：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .testTag("paste_clipboard_floating_badge"),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = SystemBorder(isDarkTheme),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste Clipboard Float",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "貼上文字",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// Inline border helper
@Composable
private fun SystemBorder(isDark: Boolean) = BorderStroke(
    width = 1.dp,
    color = if (isDark) Color(0xFF3C4043) else Color(0xFFDADCE0)
)

// Bouncing Sound equalizer bars reflecting MS Read Aloud TTS status
@Composable
fun BouncingAudioVisualizer(modifier: Modifier = Modifier) {
    val barCount = 5
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_bars")

    Row(
        modifier = modifier
            .width(42.dp)
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount) {
            val initialDelay = i * 140
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 650
                        0.2f at initialDelay
                        1.0f at initialDelay + 250
                        0.2f at initialDelay + 500
                    },
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(scale)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
            )
        }
    }
}

// Helper for linear progress strokeCap
@Composable
private fun strokeCap() = androidx.compose.ui.graphics.StrokeCap.Round
