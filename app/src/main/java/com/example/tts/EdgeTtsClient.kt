package com.example.tts

import android.util.Log
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class EdgeTtsClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val TAG = "EdgeTtsClient"
    private val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9287E7D6610F655B3F"

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Calculates the required dynamic Sec-MS-GEC signature header value for Microsoft Edge Read Aloud API.
     * The signature is derived from Windows FILETIME ticks formatted at 5-minute precision and salt.
     */
    private fun generateSecMsGec(): String {
        try {
            val unixTimeSeconds = System.currentTimeMillis() / 1000L
            // Convert UNIX timestamp to Windows FILETIME ticks (100-nanosecond intervals since 1601-01-01)
            val windowsEpochOffset = 11644473600L
            val ticks = (unixTimeSeconds + windowsEpochOffset) * 10000000L
            
            // Round to 5 minutes precision (5 min = 300 sec * 10^7 ticks/sec = 3,000,000,000 ticks)
            val roundedTicks = ticks - (ticks % 3000000000L)
            
            val stringToHash = "$roundedTicks$TRUSTED_CLIENT_TOKEN"
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(stringToHash.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }.uppercase()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute Sec-MS-GEC", e)
            return "UNKNOWN_GEC"
        }
    }

    suspend fun synthesize(text: String, voiceName: String, rate: Float): ByteArray? {
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$requestId"

        // Generate the vital MS encryption headers
        val secMsGec = generateSecMsGec()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .header("Origin", "chrome-extension://jdiccldimpdaamgbidmimgbjjgcgchne")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Sec-MS-GEC", secMsGec)
            .header("Sec-MS-GEC-Version", "1-130.0.2849.68")
            .build()

        // Convert speed rate float (e.g. 0.5f to 2.0f) to Edge TTS SSML percentage format
        val percent = Math.round((rate - 1.0f) * 100)
        val rateSign = if (percent >= 0) "+$percent%" else "$percent%"
        
        // Dynamically deduce voice locale to avoid strict XML validation rejections from the gateway
        val voiceLang = try {
            voiceName.split("-").take(2).joinToString("-")
        } catch (e: Exception) {
            "zh-TW"
        }

        val escapedText = escapeXml(text)
        val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$voiceLang'>" +
                "<voice name='$voiceName'>" +
                "<prosody pitch='+0Hz' rate='$rateSign'>$escapedText</prosody>" +
                "</voice>" +
                "</speak>"

        val sdf = java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val timestamp = sdf.format(java.util.Date())

        val configMessage = "X-Timestamp:$timestamp\r\n" +
                "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},\"outputFormat\":\"audio-24khz-48kbps-truesilk-multipage-mp3\"}}}}"

        val ssmlMessage = "X-RequestId:$requestId\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
                "X-Timestamp:$timestamp\r\n" +
                "Path:ssml\r\n\r\n" +
                ssml

        return suspendCoroutine { continuation ->
            var resumed = false
            val audioBuffer = ByteArrayOutputStream()

            try {
                val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        try {
                            webSocket.send(configMessage)
                            webSocket.send(ssmlMessage)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending initial messages on WS open", e)
                            if (!resumed) {
                                resumed = true
                                continuation.resumeWithException(e)
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("Path:turn.end")) {
                            try {
                                webSocket.close(1000, "Done")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error closing websocket on turn end", e)
                            }
                            if (!resumed) {
                                resumed = true
                                val bytes = audioBuffer.toByteArray()
                                if (bytes.isNotEmpty()) {
                                    continuation.resume(bytes)
                                } else {
                                    continuation.resumeWithException(Exception("語音合成已結束，但語音緩衝區爲空"))
                                }
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        try {
                            val data = bytes.toByteArray()
                            if (data.size > 2) {
                                // Extract headers length (Big Endian Short)
                                val headerLength = (((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF))
                                if (data.size >= 2 + headerLength) {
                                    val headers = String(data, 2, headerLength, Charsets.UTF_8)
                                    if (headers.contains("Path:audio")) {
                                        val audioPayload = data.copyOfRange(2 + headerLength, data.size)
                                        audioBuffer.write(audioPayload)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing binary webSocket message", e)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket connection failed: ${t.message}", t)
                        try {
                            webSocket.close(1001, "Error")
                        } catch (e: Exception) {
                            // ignore or log
                        }
                        if (!resumed) {
                            resumed = true
                            val codeMsg = if (response != null) " - HTTP ${response.code}: ${response.message}" else ""
                            val errMessage = "Edge TTS 連線失敗: ${t.localizedMessage ?: t.message ?: "未知錯誤"}$codeMsg"
                            continuation.resumeWithException(Exception(errMessage))
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (!resumed) {
                            resumed = true
                            val bytes = audioBuffer.toByteArray()
                            if (bytes.isNotEmpty()) {
                                continuation.resume(bytes)
                            } else {
                                continuation.resumeWithException(Exception("WebSocket 正常關閉，但音頻緩衝區為空 (代碼=$code, 原因=$reason)"))
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error creating and configuring webSocket connection", e)
                if (!resumed) {
                    resumed = true
                    continuation.resumeWithException(e)
                }
            }
        }
    }
}
