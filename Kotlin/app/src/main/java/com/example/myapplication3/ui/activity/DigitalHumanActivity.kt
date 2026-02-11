package com.example.myapplication3.ui.activity

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication3.R
import com.example.myapplication3.utils.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DigitalHumanActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var editTextSay: EditText
    private lateinit var buttonSay: Button

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Flask API 地址
     */
    private val chatApiUrl: String = "${Config.BASE_URL}/api/chat"
    private val ttsApiUrl: String = "${Config.BASE_URL}/api/tts"

    /**
     * 获取当前登录用户的 ID
     */
    private fun getUserId(): Int {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        return sp.getInt("user_id", -1)
    }

    // WebView 内部引用的虚拟音频 URL，实际由 shouldInterceptRequest 返回本地 mp3
    private val audioVirtualUrl = "https://appassets.androidplatform.net/audio/tts.mp3"
    private val ttsOutputFile: File by lazy { File(cacheDir, "digital_human_tts.mp3") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_digital_human)

        webView = findViewById(R.id.webViewAvatar)
        editTextSay = findViewById(R.id.editTextSay)
        buttonSay = findViewById(R.id.buttonSay)

        setupWebView()

        buttonSay.setOnClickListener {
            val text = editTextSay.text.toString().trim()
            if (text.isNotEmpty()) {
                notifyWebThinking()
                synthAnswer(text)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // Serve asset files through https origin so that Fetch API works
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val urlStr = request.url.toString()

                if (urlStr.startsWith(audioVirtualUrl)) {
                    // Provide synthesized audio
                    if (!ttsOutputFile.exists()) {
                        return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", mapOf(), null)
                    }
                    val headers = mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Credentials" to "true"
                    )
                    return WebResourceResponse(
                        "audio/mpeg",
                        null,
                        200,
                        "OK",
                        headers,
                        FileInputStream(ttsOutputFile)
                    )
                }

                // Let assetLoader handle html/js/css/glb/etc.
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        // Use https origin provided by WebViewAssetLoader
        webView.loadUrl("https://appassets.androidplatform.net/assets/digital_human/index.html")
    }

    /**
     * 1. 调用 /api/chat 获得 LLM 回复文本
     * 2. 再调用 /api/tts 将文本转语音
     */
    private fun synthAnswer(question: String) {
        val userId = getUserId()
        if (userId == -1) {
            runOnUiThread {
                Toast.makeText(this, "用户信息失效，请重新登录", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        Thread {
            try {
                // 1) 调用 /api/chat 获取回答文本
                val json = JSONObject().apply {
                    put("prompt", question)
                    put("user_id", userId)
                    put("save_history", false) // 数字人对话不存入数据库
                }
                val chatReqBody = json.toString().toRequestBody(jsonMediaType)
                
                val chatRespText = httpClient.newCall(
                    Request.Builder().url(chatApiUrl).post(chatReqBody).build()
                ).execute().use { r ->
                    if (!r.isSuccessful) throw RuntimeException("Chat API error: ${r.code}")
                    r.body?.string() ?: throw RuntimeException("Chat API empty body")
                }
                // 简单解析 JSON 获取 content 字段
                val answerText = JSONObject(chatRespText).optString("content")
                if (answerText.isBlank()) throw RuntimeException("Chat API missing content")

                // 2) 再调用 /api/tts 获取音频
                val ttsBody = """{"text":"${escapeJson(answerText)}"}""".toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(ttsApiUrl)
                    .post(ttsBody)
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string()?.take(300)
                        runOnUiThread {
                            Toast.makeText(
                                this@DigitalHumanActivity,
                                "TTS 接口错误: ${resp.code}${if (!err.isNullOrBlank()) " / $err" else ""}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@use
                    }

                    val bytes = resp.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this@DigitalHumanActivity, "TTS 返回空音频", Toast.LENGTH_SHORT).show()
                        }
                        return@use
                    }

                    // Save MP3 file locally
                    FileOutputStream(ttsOutputFile).use { it.write(bytes) }

                    runOnUiThread {
                        webView.evaluateJavascript("window.avatar && window.avatar.setThinking(false);", null)
                        notifyWebToPlay(answerText)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@DigitalHumanActivity, "调用 TTS 失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun escapeJson(str: String): String = str
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private fun notifyWebThinking() {
        webView.evaluateJavascript(
            "window.avatar && window.avatar.setThinking(true); document.getElementById('status') && (document.getElementById('status').textContent = '思考中...');",
            null
        )
    }

    private fun notifyWebToPlay(text: String) {
        val audioUrl = "${Uri.parse(audioVirtualUrl)}?ts=${System.currentTimeMillis()}"
        val jsText = JSONObject.quote(text) // 生成安全 JS 字符串
        val jsAudio = JSONObject.quote(audioUrl)
        webView.evaluateJavascript(
            "window.avatar && window.avatar.say && window.avatar.say($jsText, $jsAudio);",
            null
        )
    }
}
