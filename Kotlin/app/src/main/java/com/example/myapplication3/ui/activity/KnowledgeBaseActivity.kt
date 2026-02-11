package com.example.myapplication3.ui.activity

import android.content.Intent
import android.net.Uri
import android.app.ProgressDialog
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication3.R
import com.example.myapplication3.utils.Config
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class KnowledgeBaseActivity : AppCompatActivity() {

    private var uploadDialog: ProgressDialog? = null
    
    private lateinit var textStats: TextView
    private lateinit var editTextTitle: EditText
    private lateinit var editTextContent: EditText
    private lateinit var editTextCategory: EditText
    private lateinit var editTextSearch: EditText
    private lateinit var textSearchResults: TextView
    private lateinit var buttonUploadFile: Button
    private lateinit var buttonAddText: Button
    private lateinit var buttonSearch: Button
    private lateinit var buttonClear: Button
    private lateinit var buttonRefresh: Button

    // 使用 Config 类管理后端地址
    private val REQUEST_CODE_PICK_FILE = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_knowledge_base)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        initViews()
        initEvents()
        loadStats()
    }
    
    private fun initViews() {
        textStats = findViewById(R.id.textStats)
        editTextTitle = findViewById(R.id.editTextTitle)
        editTextContent = findViewById(R.id.editTextContent)
        editTextCategory = findViewById(R.id.editTextCategory)
        editTextSearch = findViewById(R.id.editTextSearch)
        textSearchResults = findViewById(R.id.textSearchResults)
        buttonUploadFile = findViewById(R.id.buttonUploadFile)
        buttonAddText = findViewById(R.id.buttonAddText)
        buttonSearch = findViewById(R.id.buttonSearch)
        buttonClear = findViewById(R.id.buttonClear)
        buttonRefresh = findViewById(R.id.buttonRefresh)
    }
    
    private fun initEvents() {
        buttonUploadFile.setOnClickListener {
            openFilePicker()
        }
        
        buttonAddText.setOnClickListener {
            val title = editTextTitle.text.toString().trim()
            val content = editTextContent.text.toString().trim()
            val category = editTextCategory.text.toString().trim().takeIf { it.isNotEmpty() } ?: "general"
            
            if (title.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "请输入标题和内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            addTextToKnowledge(title, content, category)
        }
        
        buttonSearch.setOnClickListener {
            val query = editTextSearch.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            searchKnowledge(query)
        }
        
        buttonClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要清空整个知识库吗？此操作不可恢复！")
                .setPositiveButton("确定") { _, _ ->
                    clearKnowledgeBase()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        buttonRefresh.setOnClickListener {
            loadStats()
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "选择文件"), REQUEST_CODE_PICK_FILE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }
    
    private fun getUserId(): Int {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        return sp.getInt("user_id", -1)
    }

    private fun uploadFile(uri: Uri) {
        val userId = getUserId()
        if (userId == -1) {
            Toast.makeText(this, "用户信息失效，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }
        
        runOnUiThread {
            if (uploadDialog == null) {
                uploadDialog = ProgressDialog(this).apply {
                    setMessage("正在上传...")
                    setCancelable(false)
                }
            }
            uploadDialog?.show()
        }

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val fileName = getFileName(uri) ?: "document_${System.currentTimeMillis()}"

                // 读取文件内容
                val fileContent = inputStream?.readBytes() ?: return@Thread
                inputStream.close()

                // 构建 multipart/form-data 请求
                val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
                val url = URL("${Config.KNOWLEDGE_BASE_URL}/api/knowledge/upload")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connectTimeout = 30000
                    readTimeout = 60000
                }

                val outputStream = conn.outputStream
                val writer = OutputStreamWriter(outputStream, "UTF-8")

                // 写入 user_id 字段
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"user_id\"\r\n\r\n")
                writer.append("$userId\r\n")

                // 写入文件数据
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                writer.append("Content-Type: application/octet-stream\r\n\r\n")
                writer.flush()

                outputStream.write(fileContent)
                outputStream.flush()

                writer.append("\r\n--$boundary--\r\n")
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                val response = if (responseCode in 200..299) {
                    BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).use { it.readText() }
                }

                runOnUiThread {
                    uploadDialog?.dismiss()

                    if (responseCode in 200..299) {
                        val json = JSONObject(response)
                        if (json.optBoolean("ok", false)) {
                            Toast.makeText(this, "文件上传成功", Toast.LENGTH_SHORT).show()
                            loadStats()
                        } else {
                            Toast.makeText(this, "上传失败: ${json.optString("error", "未知错误")}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "上传失败: HTTP $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uploadDialog?.dismiss()
                    Toast.makeText(this, "上传失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    it.substring(cut + 1)
                } else {
                    it
                }
            }
        }
        return result
    }
    
    private fun addTextToKnowledge(title: String, content: String, category: String) {
        val userId = getUserId()
        if (userId == -1) {
            Toast.makeText(this, "用户信息失效，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val url = URL("${Config.KNOWLEDGE_BASE_URL}/api/knowledge/add-text")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 10000
                    readTimeout = 60000
                }
                
                val json = JSONObject().apply {
                    put("user_id", userId)
                    put("title", title)
                    put("text", content)
                    put("category", category)
                }
                
                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }
                
                val responseCode = conn.responseCode
                val response = if (responseCode in 200..299) {
                    BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).use { it.readText() }
                }
                
                runOnUiThread {
                    if (responseCode in 200..299) {
                        val jsonResp = JSONObject(response)
                        if (jsonResp.optBoolean("ok", false)) {
                            Toast.makeText(this, "文本添加成功", Toast.LENGTH_SHORT).show()
                            editTextTitle.setText("")
                            editTextContent.setText("")
                            editTextCategory.setText("")
                            loadStats()
                        } else {
                            Toast.makeText(this, "添加失败: ${jsonResp.optString("error", "未知错误")}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "添加失败: HTTP $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "添加失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    
    private fun searchKnowledge(query: String) {
        val userId = getUserId()
        if (userId == -1) {
            Toast.makeText(this, "用户信息失效，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val url = URL("${Config.KNOWLEDGE_BASE_URL}/api/knowledge/search")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 10000
                    readTimeout = 30000
                }
                
                val json = JSONObject().apply {
                    put("user_id", userId)
                    put("query", query)
                    put("top_k", 5)
                }
                
                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }
                
                val responseCode = conn.responseCode
                val response = if (responseCode in 200..299) {
                    BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).use { it.readText() }
                }
                
                runOnUiThread {
                    if (responseCode in 200..299) {
                        val jsonResp = JSONObject(response)
                        if (jsonResp.optBoolean("ok", false)) {
                            val results = jsonResp.getJSONArray("results")
                            val total = jsonResp.optInt("total", 0)
                            
                            val sb = StringBuilder()
                            sb.append("找到 $total 条结果：\n\n")
                            
                            for (i in 0 until results.length()) {
                                val result = results.getJSONObject(i)
                                val title = result.optString("title", "未知")
                                val content = result.optString("content", "")
                                val score = result.optDouble("score", 0.0)
                                val category = result.optString("category", "general")
                                
                                sb.append("【$title】($category, 相似度: ${String.format("%.2f", score)})\n")
                                sb.append("${content.take(200)}${if (content.length > 200) "..." else ""}\n\n")
                            }
                            
                            textSearchResults.text = sb.toString()
                        } else {
                            textSearchResults.text = "搜索失败: ${jsonResp.optString("error", "未知错误")}"
                        }
                    } else {
                        textSearchResults.text = "搜索失败: HTTP $responseCode"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    textSearchResults.text = "搜索失败: ${e.message}"
                }
            }
        }.start()
    }
    
    private fun loadStats() {
        val userId = getUserId()
        if (userId == -1) {
            runOnUiThread {
                textStats.text = "用户信息失效，请重新登录"
            }
            return
        }

        Thread {
            try {
                val url = URL("${Config.KNOWLEDGE_BASE_URL}/api/knowledge/stats?user_id=$userId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 30000
                }
                
                val responseCode = conn.responseCode
                val response = if (responseCode in 200..299) {
                    BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).use { it.readText() }
                }
                
                runOnUiThread {
                    if (responseCode in 200..299) {
                        val json = JSONObject(response)
                        if (json.optBoolean("ok", false)) {
                            val stats = json.getJSONObject("stats")
                            val totalChunks = stats.optInt("total_chunks", 0)

                            val sb = StringBuilder()
                            sb.append("📚 知识库统计\n\n")
                            sb.append("文本块总数: $totalChunks\n")

                            textStats.text = sb.toString()
                        } else {
                            textStats.text = "获取统计失败: ${json.optString("error", "未知错误")}"
                        }
                    } else {
                        textStats.text = "获取统计失败: HTTP $responseCode"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    textStats.text = "获取统计失败: ${e.message}"
                }
            }
        }.start()
    }
    
    private fun clearKnowledgeBase() {
        Thread {
            try {
                val userId = getUserId()
                val url = URL("${Config.KNOWLEDGE_BASE_URL}/api/knowledge/clear?user_id=$userId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 10000
                    readTimeout = 30000
                }
                
                val responseCode = conn.responseCode
                val response = if (responseCode in 200..299) {
                    BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).use { it.readText() }
                }
                
                runOnUiThread {
                    if (responseCode in 200..299) {
                        val json = JSONObject(response)
                        if (json.optBoolean("ok", false)) {
                            Toast.makeText(this, "知识库已清空", Toast.LENGTH_SHORT).show()
                            loadStats()
                            textSearchResults.text = ""
                        } else {
                            Toast.makeText(this, "清空失败: ${json.optString("error", "未知错误")}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "清空失败: HTTP $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "清空失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
