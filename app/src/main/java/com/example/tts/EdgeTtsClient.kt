package com.example.tts

import android.util.Log
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class EdgeTtsClient {
    private val client = OkHttpClient.Builder().build()
    private val TAG = "EdgeTtsClient"

    suspend fun synthesize(text: String, voiceName: String, rate: Float): ByteArray? {
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9287E7D6610F655B3F&ConnectionId=$requestId"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0")
            .header("Origin", "chrome-extension://jdiccldimpdaamgbidmimgbjjgcgchne")
            .build()

        // Convert speed rate float (e.g. 0.5f to 2.0f) to Edge TTS SSML percentage format
        val percent = Math.round((rate - 1.0f) * 100)
        val rateSign = if (percent >= 0) "+$percent%" else "$percent%"
        
        val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-TW'>" +
                "<voice name='$voiceName'>" +
                "<prosody pitch='+0Hz' rate='$rateSign'>$text</prosody>" +
                "</voice>" +
                "</speak>"

        val configMessage = "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},\"outputFormat\":\"audio-24khz-48kbps-truesilk-multipage-mp3\"}}}}"

        val ssmlMessage = "X-RequestId:$requestId\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
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
                                continuation.resume(audioBuffer.toByteArray())
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
                            continuation.resume(null)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (!resumed) {
                            resumed = true
                            continuation.resume(audioBuffer.toByteArray())
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error creating and configuring webSocket connection", e)
                if (!resumed) {
                    resumed = true
                    continuation.resume(null)
                }
            }
        }
    }
}
