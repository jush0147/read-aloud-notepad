package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class SpeechSegment(
    val text: String,
    val startCharOffset: Int,
    val endCharOffset: Int
)

data class VoiceOption(
    val id: String,
    val displayName: String,
    val locale: Locale,
    val voiceObj: Voice? = null
)

class SpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "SpeechManager"
    private var tts: TextToSpeech? = null
    private val edgeTtsClient = EdgeTtsClient()
    private var mediaPlayer: android.media.MediaPlayer? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Exposes current highlight characters: Pair(Start, End) absolute indices in original text
    private val _currentHighlightRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val currentHighlightRange: StateFlow<Pair<Int, Int>?> = _currentHighlightRange.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _selectedVoice = MutableStateFlow<VoiceOption?>(null)
    val selectedVoice: StateFlow<VoiceOption?> = _selectedVoice.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _currentSegmentIndex = MutableStateFlow(0)
    val currentSegmentIndex: StateFlow<Int> = _currentSegmentIndex.asStateFlow()

    private val _totalSegmentsCount = MutableStateFlow(0)
    val totalSegmentsCount: StateFlow<Int> = _totalSegmentsCount.asStateFlow()

    private var fullText: String = ""
    private var segments: List<SpeechSegment> = emptyList()
    private val scope = CoroutineScope(Dispatchers.Main)

    private val edgeVoicesList = listOf(
        VoiceOption("zh-TW-HsiaoChenNeural", "微軟 曉臻 (台灣女聲 · 甜美和藹) [網路高品質]", Locale.TAIWAN),
        VoiceOption("zh-TW-HsiaoYuNeural", "微軟 曉雨 (台灣女聲 · 標準流暢) [網路高品質]", Locale.TAIWAN),
        VoiceOption("zh-TW-YunJheNeural", "微軟 雲哲 (台灣男聲 · 溫和磁性) [網路高品質]", Locale.TAIWAN),
        VoiceOption("zh-CN-XiaoxiaoNeural", "微軟 曉曉 (普通話女聲 · 溫柔細膩) [網路高品質]", Locale.CHINA),
        VoiceOption("zh-CN-YunjianNeural", "微軟 雲健 (普通話男聲 · 新聞主播) [網路高品質]", Locale.CHINA),
        VoiceOption("zh-CN-YunxiNeural", "微軟 雲希 (普通話男聲 · 休閒生動) [網路高品質]", Locale.CHINA),
        VoiceOption("zh-HK-HiuMaanNeural", "微軟 曉佳 (香港女聲 · 標準粵語) [網路高品質]", Locale.forLanguageTag("zh-HK")),
        VoiceOption("zh-HK-WanLungNeural", "微軟 雲龍 (香港男聲 · 穩重粵語) [網路高品質]", Locale.forLanguageTag("zh-HK")),
        VoiceOption("en-US-JennyNeural", "微軟 Jenny (美國女聲 · 親切友善) [網路高品質]", Locale.US),
        VoiceOption("en-US-GuyNeural", "微軟 Guy (美國男聲 · 自然大氣) [網路高品質]", Locale.US)
    )

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupTtsSettings()
            _isInitialized.value = true
        } else {
            Log.e(TAG, "Initialization of TextToSpeech failed.")
            _isInitialized.value = false
        }
    }

    private fun setupTtsSettings() {
        val activeTts = tts ?: return
        
        // 1. Establish custom listener
        activeTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    _isPlaying.value = true
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    playNextSegmentIfAny()
                }
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
            override fun onError(utteranceId: String?) {
                scope.launch {
                    _isPlaying.value = false
                    _currentHighlightRange.value = null
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                super.onError(utteranceId, errorCode)
                scope.launch {
                    _isPlaying.value = false
                    _currentHighlightRange.value = null
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                val currVal = _currentSegmentIndex.value
                if (currVal in segments.indices) {
                    val currentSegment = segments[currVal]
                    val absoluteStart = currentSegment.startCharOffset + start
                    val absoluteEnd = currentSegment.startCharOffset + end
                    scope.launch {
                        _currentHighlightRange.value = Pair(absoluteStart, absoluteEnd)
                    }
                }
            }
        })

        // 2. Discover and populate high-quality options styled similar to Microsoft Natural voices
        val voicesList = mutableListOf<VoiceOption>()
        try {
            val systemAvailableVoices = activeTts.voices
            if (!systemAvailableVoices.isNullOrEmpty()) {
                for (voice in systemAvailableVoices) {
                    val locale = voice.locale ?: continue
                    val language = locale.language
                    val country = locale.country

                    // Support Chinese (繁/簡) and English
                    if (language == "zh" || language == "en") {
                        val displayName = parseVoiceFriendlyName(voice, locale)
                        voicesList.add(
                            VoiceOption(
                                id = voice.name ?: "${locale.language}_${locale.country}_${voice.hashCode()}",
                                displayName = displayName,
                                locale = locale,
                                voiceObj = voice
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching advanced voices list. Falling back to default locales.", e)
        }

        // Sort: prioritize zh languages, then english
        val sortedList = voicesList.distinctBy { it.displayName }.sortedWith(compareBy<VoiceOption> {
            if (it.locale.language == "zh") 0 else 1
        }.thenBy { it.locale.country }.thenBy { it.displayName })

        val finalCombinedList = mutableListOf<VoiceOption>()
        finalCombinedList.addAll(edgeVoicesList)
        finalCombinedList.addAll(sortedList)

        // Add guaranteed local fallbacks if empty description
        if (finalCombinedList.isEmpty()) {
            finalCombinedList.add(VoiceOption("zh_TW", "華語語音 - 台灣 (補備標準女聲)", Locale.TAIWAN))
            finalCombinedList.add(VoiceOption("en_US", "English - United States (Azure Style Fallback)", Locale.US))
        }

        _availableVoices.value = finalCombinedList
        _selectedVoice.value = finalCombinedList.firstOrNull()
    }

    private fun parseVoiceFriendlyName(voice: Voice, locale: Locale): String {
        val country = locale.country
        val language = locale.language
        val voiceNameLower = (voice.name ?: "").lowercase()

        val prefix = when {
            language == "zh" && (country == "TW" || country == "HK") -> "繁體中文"
            language == "zh" -> "普通話"
            language == "en" && country == "US" -> "English (US)"
            language == "en" && country == "GB" -> "English (UK)"
            else -> locale.displayLanguage
        }

        val regionLabel = when {
            country == "TW" -> "(台灣)"
            country == "HK" -> "(香港)"
            country == "CN" -> "(大陸)"
            else -> "($country)"
        }

        // Detect voice gender based on Google speech services and android standards
        val voiceType = when {
            // Taiwan-specific Google TTS voices (sft, ctc, ctd are female; ctb, cte are male)
            voiceNameLower.contains("sft") -> "系統女聲 (sft)"
            voiceNameLower.contains("ctb") -> "系統男聲 (ctb)"
            voiceNameLower.contains("ctc") -> "系統女聲 (ctc)"
            voiceNameLower.contains("ctd") -> "系統女聲 (ctd)"
            voiceNameLower.contains("cte") -> "系統男聲 (cte)"

            // Mainland China-specific Google TTS voices
            voiceNameLower.contains("xiaofang") || voiceNameLower.contains("cqi") || voiceNameLower.contains("gqi") -> "系統女聲 (曉芳款)"
            voiceNameLower.contains("yunjian") || voiceNameLower.contains("baoyu") || voiceNameLower.contains("huaye") || voiceNameLower.contains("cqu") || voiceNameLower.contains("gqu") -> "系統男聲 (雲漢款)"

            // Fallback heuristics using standard english strings
            voiceNameLower.contains("female") || voiceNameLower.contains("f1") || voiceNameLower.contains("cstar") || voiceNameLower.contains("girl") || voiceNameLower.contains("woman") -> "系統女聲 [本地]"
            voiceNameLower.contains("male") || voiceNameLower.contains("m1") || voiceNameLower.contains("bstar") || voiceNameLower.contains("guy") || voiceNameLower.contains("man") -> "系統男聲 [本地]"

            // Unclassified
            else -> {
                val sum = voice.name.hashCode().absoluteValue() % 2
                if (sum == 0) "系統語音 (女) [本地]" else "系統語音 (男) [本地]"
            }
        }

        val neuralTag = if (voiceNameLower.contains("network") || voice.isNetworkConnectionRequired) " [網路高品質]" else " [本地合成]"

        // Extract a clean, recognizable short name for the Voice list
        val shortId = voice.name
            ?.substringAfter("zh-tw-")
            ?.substringAfter("zh-cn-")
            ?.substringAfter("en-us-")
            ?.replace("-local", "")
            ?.replace("-network", "")
            ?: "default"

        return "系統 · $prefix $regionLabel · $voiceType ($shortId)$neuralTag"
    }

    private fun Int.absoluteValue(): Int = if (this < 0) -this else this

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        tts?.setSpeechRate(rate)
    }

    fun selectVoice(voiceOption: VoiceOption) {
        _selectedVoice.value = voiceOption
        val activeTts = tts ?: return
        try {
            if (voiceOption.voiceObj != null) {
                activeTts.setVoice(voiceOption.voiceObj)
            } else {
                activeTts.setLanguage(voiceOption.locale)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting voice/language", e)
        }
    }

    // Segments full raw text to structured sentences, preserving indices
    fun setSourceText(text: String) {
        if (fullText == text && segments.isNotEmpty()) return
        fullText = text
        segments = segmentText(text)
        _totalSegmentsCount.value = segments.size
        _currentSegmentIndex.value = 0
        resetPlayback()
    }

    private fun segmentText(text: String): List<SpeechSegment> {
        if (text.isBlank()) return emptyList()
        val segmentsList = mutableListOf<SpeechSegment>()
        // Characters signifying sentence ends
        val delimiters = setOf('。', '！', '？', '；', '\n', '.', '!', '?', ';', ',', '，')
        var startOffset = 0
        var currentToken = StringBuilder()

        for (i in text.indices) {
            val char = text[i]
            currentToken.append(char)
            if (delimiters.contains(char) || i == text.lastIndex) {
                val rawStr = currentToken.toString()
                if (rawStr.isNotBlank()) {
                    segmentsList.add(
                        SpeechSegment(
                            text = rawStr,
                            startCharOffset = startOffset,
                            endCharOffset = i + 1
                        )
                    )
                }
                startOffset = i + 1
                currentToken = StringBuilder()
            }
        }
        return segmentsList
    }

    // Speaks the current segment
    fun play() {
        if (segments.isEmpty()) return
        val isEdgeVoice = _selectedVoice.value?.id?.contains("Neural") == true
        if (!isEdgeVoice && !_isInitialized.value) return

        _isPlaying.value = true
        speakCurrentSegment()
    }

    private fun speakCurrentSegment() {
        val index = _currentSegmentIndex.value
        if (index !in segments.indices) {
            // Finished playing all segments
            _isPlaying.value = false
            _currentHighlightRange.value = null
            _currentSegmentIndex.value = 0
            return
        }

        val segment = segments[index]
        // Highlight entire sentence initially
        _currentHighlightRange.value = Pair(segment.startCharOffset, segment.endCharOffset)

        val voice = _selectedVoice.value
        val isEdgeVoice = voice?.id?.contains("Neural") == true

        if (isEdgeVoice && voice != null) {
            // Stop any active local TTS
            try {
                tts?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping local TTS", e)
            }
            
            val voiceId = voice.id
            val rateValue = _speechRate.value
            
            scope.launch(Dispatchers.IO) {
                try {
                    val soundBytes = edgeTtsClient.synthesize(segment.text, voiceId, rateValue)
                    if (soundBytes != null && soundBytes.isNotEmpty()) {
                        val tempFile = java.io.File(context.cacheDir, "edge_tts_segment.mp3")
                        tempFile.writeBytes(soundBytes)
                        
                        scope.launch(Dispatchers.Main) {
                            playEdgeAudioFile(tempFile.absolutePath)
                        }
                    } else {
                        // Fallback to local
                        scope.launch(Dispatchers.Main) {
                            speakViaLocalEngine(segment.text)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Edge synthesis failed, falling back to local TTS", e)
                    scope.launch(Dispatchers.Main) {
                        speakViaLocalEngine(segment.text)
                    }
                }
            }
        } else {
            // Stop any active mediaplayer
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.reset()
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting mediaPlayer in speakCurrentSegment", e)
            }
            speakViaLocalEngine(segment.text)
        }
    }

    private fun playEdgeAudioFile(filePath: String) {
        try {
            mediaPlayer?.reset() ?: run {
                mediaPlayer = android.media.MediaPlayer()
            }
            val player = mediaPlayer ?: return
            player.setDataSource(filePath)
            
            player.setOnPreparedListener {
                _isPlaying.value = true
                try {
                    it.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting mediaPlayer in onPrepared", e)
                }
            }
            
            player.setOnCompletionListener {
                scope.launch {
                    playNextSegmentIfAny()
                }
            }
            
            player.setOnErrorListener { _, _, _ ->
                _isPlaying.value = false
                val index = _currentSegmentIndex.value
                if (index in segments.indices) {
                    speakViaLocalEngine(segments[index].text)
                }
                true
            }
            
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file", e)
            val index = _currentSegmentIndex.value
            if (index in segments.indices) {
                speakViaLocalEngine(segments[index].text)
            }
        }
    }

    private fun speakViaLocalEngine(text: String) {
        val activeTts = tts ?: return
        if (!_isInitialized.value) return
        val index = _currentSegmentIndex.value
        
        try {
            activeTts.setSpeechRate(_speechRate.value)
            _selectedVoice.value?.let {
                if (it.voiceObj != null) {
                    try {
                        activeTts.setVoice(it.voiceObj)
                    } catch (e: Exception) {
                        activeTts.setLanguage(it.locale)
                    }
                } else {
                    activeTts.setLanguage(it.locale)
                }
            }
            
            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "SEGMENT_$index")
            }
            activeTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "SEGMENT_$index")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking via local TTS engine", e)
        }
    }

    private fun playNextSegmentIfAny() {
        val nextIdx = _currentSegmentIndex.value + 1
        _currentSegmentIndex.value = nextIdx
        if (nextIdx < segments.size) {
            speakCurrentSegment()
        } else {
            _isPlaying.value = false
            _currentHighlightRange.value = null
            _currentSegmentIndex.value = 0
        }
    }

    // Move to previous sentence segment
    fun previousSegment() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting mediaPlayer in previousSegment", e)
        }
        val activeTts = tts ?: return
        try {
            activeTts.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping local TTS", e)
        }
        val prevIdx = (_currentSegmentIndex.value - 1).coerceAtLeast(0)
        _currentSegmentIndex.value = prevIdx
        if (_isPlaying.value) {
            speakCurrentSegment()
        } else {
            // Just move highlighter
            if (prevIdx in segments.indices) {
                val seg = segments[prevIdx]
                _currentHighlightRange.value = Pair(seg.startCharOffset, seg.endCharOffset)
            }
        }
    }

    // Move to next sentence segment
    fun nextSegment() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting mediaPlayer in nextSegment", e)
        }
        val activeTts = tts ?: return
        try {
            activeTts.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping local TTS", e)
        }
        val nextIdx = (_currentSegmentIndex.value + 1).coerceAtMost(segments.lastIndex.coerceAtLeast(0))
        _currentSegmentIndex.value = nextIdx
        if (_isPlaying.value) {
            speakCurrentSegment()
        } else {
            // Just move highlighter
            if (nextIdx in segments.indices) {
                val seg = segments[nextIdx]
                _currentHighlightRange.value = Pair(seg.startCharOffset, seg.endCharOffset)
            }
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing mediaPlayer", e)
        }
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping local TTS in pause", e)
        }
        _isPlaying.value = false
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting mediaPlayer in stop", e)
        }
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping local TTS in stop", e)
        }
        _isPlaying.value = false
        _currentHighlightRange.value = null
        _currentSegmentIndex.value = 0
    }

    fun shutdown() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing mediaPlayer in shutdown", e)
        }
        mediaPlayer = null
        tts?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down local TTS", e)
            }
        }
        tts = null
    }

    private fun resetPlayback() {
        _currentHighlightRange.value = null
    }
}
