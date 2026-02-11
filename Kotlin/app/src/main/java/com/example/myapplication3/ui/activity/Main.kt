package com.example.myapplication3.ui.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication3.ui.adapter.FileAdapter
import com.example.myapplication3.ui.adapter.Message
import com.example.myapplication3.ui.adapter.MessageAdapter
import com.example.myapplication3.R
import com.example.myapplication3.ui.adapter.SelectedFile
import com.example.myapplication3.utils.Config
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

private lateinit var editTextTask: EditText
private lateinit var recyclerViewMessages: RecyclerView
private lateinit var messageList: MutableList<Message>
private lateinit var adapter: MessageAdapter
private lateinit var buttonSend: Button
private lateinit var buttonClear: Button
private lateinit var buttonKnowledgeBase: Button
private lateinit var buttonUploadFile: Button
private lateinit var buttonConversationList: Button
private lateinit var buttonNewConversation: Button
private lateinit var buttonDigitalHuman: Button
private lateinit var layoutSelectedFiles: LinearLayout
private lateinit var recyclerViewFiles: RecyclerView
private lateinit var selectedFiles: MutableList<SelectedFile>
private lateinit var fileAdapter: FileAdapter

// 与 Flask 后端会话 ID
private var conversationId: String? = null

// 使用 Config 类管理后端地址
private const val REQUEST_CODE_PICK_FILE = 1001

class Main : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 检查是否有传入的对话ID
        val intentConversationId = intent.getStringExtra("conversation_id")
        if (!intentConversationId.isNullOrEmpty()) {
            conversationId = intentConversationId
        }

        initViews()
        initList()
        initEvents()
        setupKeyboardListener()
        
        // 如果有对话ID，加载历史消息
        if (!conversationId.isNullOrEmpty()) {
            loadConversationHistory(conversationId!!)
        }
    }
    
    /**
     * 设置键盘监听，确保键盘弹出时输入框和按钮始终在键盘上方
     * 使用 adjustResize + 手动调整，双重保障
     */
    private fun setupKeyboardListener() {
        val rootView = findViewById<View>(R.id.main)
        val layoutInputArea = findViewById<LinearLayout>(R.id.layoutInputArea)
        
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            // 如果键盘高度超过屏幕的15%，认为键盘已弹出
            if (keypadHeight > screenHeight * 0.15) {
                // 键盘弹出，确保输入区域在键盘上方
                layoutInputArea.post {
                    // 获取输入区域在屏幕中的位置
                    val location = IntArray(2)
                    layoutInputArea.getLocationOnScreen(location)
                    val inputAreaY = location[1]
                    val inputAreaHeight = layoutInputArea.height
                    val inputAreaBottom = inputAreaY + inputAreaHeight
                    
                    // 键盘顶部位置（可见区域底部）
                    val keyboardTop = rect.bottom
                    
                    // 如果输入区域底部在键盘下方，需要上移
                    if (inputAreaBottom > keyboardTop) {
                        // 计算需要上移的距离（额外加10dp安全间距）
                        val offset = inputAreaBottom - keyboardTop 
                        
                        // 使用 translateY 上移输入区域
                        layoutInputArea.translationY = -offset.toFloat()
                    } else {
                        // 输入区域已经在键盘上方，确保位置正确
                        if (layoutInputArea.translationY != 0f) {
                            layoutInputArea.translationY = 0f
                        }
                    }
                    
                    // 滚动消息列表到底部
                    if (messageList.isNotEmpty()) {
                        recyclerViewMessages.post {
                            recyclerViewMessages.smoothScrollToPosition(messageList.size - 1)
                        }
                    }
                }
            } else {
                // 键盘隐藏，重置输入区域位置
                if (layoutInputArea.translationY != 0f) {
                    layoutInputArea.translationY = 0f
                }
            }
        }
    }

    private fun initViews() {
        editTextTask = findViewById(R.id.editTextTask)
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages)
        buttonSend = findViewById(R.id.buttonSend)
        buttonClear = findViewById(R.id.buttonClear)
        buttonKnowledgeBase = findViewById(R.id.buttonKnowledgeBase)
        buttonUploadFile = findViewById(R.id.buttonUploadFile)
        buttonConversationList = findViewById(R.id.buttonConversationList)
        buttonNewConversation = findViewById(R.id.buttonNewConversation)
        buttonDigitalHuman = findViewById(R.id.buttonDigitalHuman)
        layoutSelectedFiles = findViewById(R.id.layoutSelectedFiles)
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles)
        
        // 确保 EditText 能正确显示中文
        editTextTask.setTextColor(Color.BLACK)
        editTextTask.hint = "请输入你的学习问题..."
        
        // 确保支持中文输入 - 使用 textCapSentences 类型
        editTextTask.inputType = InputType.TYPE_CLASS_TEXT or
                                  InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        
        // 设置输入法动作
        editTextTask.imeOptions = EditorInfo.IME_ACTION_SEND
    }

    private fun initList() {
        messageList = mutableListOf()
        adapter = MessageAdapter(messageList)
        recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        recyclerViewMessages.adapter = adapter
        
        // 初始化文件列表
        selectedFiles = mutableListOf()
        fileAdapter = FileAdapter(selectedFiles) { position ->
            selectedFiles.removeAt(position)
            fileAdapter.notifyItemRemoved(position)
            updateFileListVisibility()
        }
        recyclerViewFiles.layoutManager = LinearLayoutManager(this)
        recyclerViewFiles.adapter = fileAdapter
    }
    
    private fun updateFileListVisibility() {
        layoutSelectedFiles.visibility = if (selectedFiles.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun initEvents() {
        buttonUploadFile.setOnClickListener {
            openFilePicker()
        }


        // 底部旧数字人按钮删除需求，直接隐藏
        buttonDigitalHuman.visibility = View.GONE
        
        buttonSend.setOnClickListener {
            val text = editTextTask.text.toString().trim()
            if (text.isEmpty() && selectedFiles.isEmpty()) {
                Toast.makeText(this, "请输入问题或选择文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedFiles.isNotEmpty()) {
                // 如果有文件，使用带文件的接口
                val prompt = text.ifEmpty { "请分析这些文件" }
                val fileNames = selectedFiles.joinToString(", ") { it.fileName }
                addMessageToList(if (text.isNotEmpty()) "$text\n[文件: $fileNames]" else "[已上传${selectedFiles.size}个文件: $fileNames]", isUser = true)
                editTextTask.setText("")
                
                // 显示加载提示
                addMessageToList("正在思考中...", isUser = false)
                buttonSend.isEnabled = false
                
                sendMessageWithFiles(prompt, selectedFiles.toList())
                // 清空已选择的文件
                selectedFiles.clear()
                fileAdapter.notifyDataSetChanged()
                updateFileListVisibility()
            } else {
                // 普通聊天
                addMessageToList(text, isUser = true)
                editTextTask.setText("")
                
                // 显示加载提示
                addMessageToList("正在思考中...", isUser = false)
                buttonSend.isEnabled = false  // 禁用按钮防止重复发送
                
                sendMessageToBackend(text)
            }
        }

        buttonClear.setOnClickListener {
            // 清空当前对话的显示（不删除数据库记录）
            messageList.clear()
            adapter.notifyDataSetChanged()
            selectedFiles.clear()
            fileAdapter.notifyDataSetChanged()
            updateFileListVisibility()
            Toast.makeText(this, "已清空当前对话显示", Toast.LENGTH_SHORT).show()
        }
        
        buttonKnowledgeBase.setOnClickListener {
            val intent = Intent(this, KnowledgeBaseActivity::class.java)
            startActivity(intent)
        }
        
        buttonConversationList.setOnClickListener {
            // 跳转到对话列表
            val intent = Intent(this, ConversationListActivity::class.java)
            startActivity(intent)
        }
        
        buttonNewConversation.setOnClickListener {
            // 创建新对话
            conversationId = null
            messageList.clear()
            adapter.notifyDataSetChanged()
            selectedFiles.clear()
            fileAdapter.notifyDataSetChanged()
            updateFileListVisibility()
            Toast.makeText(this, "已创建新对话", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)  // 支持多选
        }
        startActivityForResult(Intent.createChooser(intent, "选择文件"), REQUEST_CODE_PICK_FILE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            if (data?.clipData != null) {
                // 多选文件
                val clipData = data.clipData!!
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}_$i"
                    selectedFiles.add(SelectedFile(uri, fileName))
                }
            } else if (data?.data != null) {
                // 单选文件
                val uri = data.data!!
                val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
                selectedFiles.add(SelectedFile(uri, fileName))
            }
            fileAdapter.notifyDataSetChanged()
            updateFileListVisibility()
        }
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

    private fun addMessageToList(text: String, isUser: Boolean) {
        messageList.add(Message(text, isUser))
        adapter.notifyItemInserted(messageList.size - 1)
        recyclerViewMessages.smoothScrollToPosition(messageList.size - 1)
    }
    
    /**
     * 清理 Markdown 格式符号，让文本更易读
     */
    private fun cleanMarkdown(text: String): String {
        var cleaned = text
        // 移除 Markdown 标题符号 (#)
        cleaned = cleaned.replace(Regex("^#{1,6}\\s+"), "")
        // 移除 Markdown 粗体符号 (**text** -> text)
        cleaned = cleaned.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        // 移除 Markdown 斜体符号 (*text* -> text)
        cleaned = cleaned.replace(Regex("(?<!\\*)\\*([^*]+?)\\*(?!\\*)"), "$1")
        // 移除 Markdown 表格分隔符 (|)
        cleaned = cleaned.replace(Regex("\\|"), " ")
        // 移除 Markdown 列表符号 (-, *, +)
        cleaned = cleaned.replace(Regex("^[-*+]\\s+"), "• ")
        // 移除 Markdown 代码块符号 (```)
        cleaned = cleaned.replace(Regex("```[\\w]*"), "")
        // 移除 Markdown 行内代码符号 (`)
        cleaned = cleaned.replace(Regex("`([^`]+)`"), "$1")
        // 清理多余的空格
        cleaned = cleaned.replace(Regex(" {2,}"), " ")
        // 清理多余的空行（保留最多一个空行）
        cleaned = cleaned.replace(Regex("\n{3,}"), "\n\n")
        return cleaned.trim()
    }

    /**
     * 获取当前登录用户的 ID
     */
    private fun getUserId(): Int {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        return sp.getInt("user_id", -1)
    }

    private fun sendMessageToBackend(message: String) {
        val userId = getUserId()
        if (userId == -1) {
            runOnUiThread {
                Toast.makeText(this, "用户信息失效，请重新登录", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            return
        }

        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(Config.CHAT_URL)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000  // 连接超时 10 秒
                    readTimeout = 120000    // 读取超时 120 秒（LLM 响应 + 后端重试可能需要更长时间）
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                val json = JSONObject().apply {
                    put("prompt", message)
                    put("user_id", userId)
                    conversationId?.let { put("conversation_id", it) }
                }

                BufferedWriter(OutputStreamWriter(conn.outputStream, "UTF-8")).use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val inputStream = if (responseCode in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val response = StringBuilder()
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        response.append(line)
                        line = reader.readLine()
                    }
                }

                val responseStr = response.toString()
                Log.d("Main", "后端响应: $responseStr")
                
                try {
                    val jsonResp = JSONObject(responseStr)
                    val isOk = jsonResp.optBoolean("ok", false)
                    Log.d("Main", "ok字段: $isOk")
                    
                    if (isOk) {
                        val content = jsonResp.optString("content", "")
                        val newConversationId = jsonResp.optString("conversation_id", "")
                        Log.d("Main", "content长度: ${content.length}, conversation_id: $newConversationId")
                        
                        if (!newConversationId.isNullOrEmpty()) {
                            conversationId = newConversationId
                        }

                        runOnUiThread {
                            // 移除"正在思考中..."的提示
                            if (messageList.isNotEmpty() && 
                                messageList.last().text == "正在思考中..." && 
                                !messageList.last().isUser) {
                                val lastIndex = messageList.size - 1
                                messageList.removeAt(lastIndex)
                                adapter.notifyItemRemoved(lastIndex)
                            }
                            
                            if (content.isNotEmpty()) {
                                // 移除"AI："前缀（如果存在）
                                val cleanedContent = content.removePrefix("AI：").trim()
                                addMessageToList(cleanedContent, isUser = false)
                            } else {
                                Log.w("Main", "后端返回的content为空")
                                Toast.makeText(this, "后端返回内容为空", Toast.LENGTH_LONG).show()
                            }
                            
                            // 重新启用发送按钮
                            buttonSend.isEnabled = true
                        }
                    } else {
                        val errorMsg = jsonResp.optString("error", "请求失败")
                        Log.e("Main", "后端返回错误: $errorMsg")
                        runOnUiThread {
                            // 移除"正在思考中..."的提示
                            if (messageList.isNotEmpty() && 
                                messageList.last().text == "正在思考中..." && 
                                !messageList.last().isUser) {
                                val lastIndex = messageList.size - 1
                                messageList.removeAt(lastIndex)
                                adapter.notifyItemRemoved(lastIndex)
                            }
                            Toast.makeText(this, "错误: $errorMsg", Toast.LENGTH_LONG).show()
                            buttonSend.isEnabled = true
                        }
                    }
                } catch (jsonEx: Exception) {
                    Log.e("Main", "JSON解析失败: ${jsonEx.message}", jsonEx)
                    Log.e("Main", "原始响应: $responseStr")
                    runOnUiThread {
                        // 移除"正在思考中..."的提示
                        if (messageList.isNotEmpty() && 
                            messageList.last().text == "正在思考中..." && 
                            !messageList.last().isUser) {
                            val lastIndex = messageList.size - 1
                            messageList.removeAt(lastIndex)
                            adapter.notifyItemRemoved(lastIndex)
                            adapter.notifyDataSetChanged()
                        }
                        Toast.makeText(this, "响应解析失败: ${jsonEx.message}", Toast.LENGTH_LONG).show()
                        buttonSend.isEnabled = true
                    }
                }
            } catch (e: SocketTimeoutException) {
                Log.e("Main", "请求超时: ${e.message}", e)
                runOnUiThread {
                    // 移除"正在思考中..."的提示
                    if (messageList.isNotEmpty() && 
                        messageList.last().text == "正在思考中..." && 
                        !messageList.last().isUser) {
                        val lastIndex = messageList.size - 1
                        messageList.removeAt(lastIndex)
                        adapter.notifyItemRemoved(lastIndex)
                    }
                    Toast.makeText(this, "请求超时，请稍后重试（LLM 响应可能需要更长时间）", Toast.LENGTH_LONG).show()
                    buttonSend.isEnabled = true
                }
            } catch (e: ConnectException) {
                Log.e("Main", "连接失败: ${e.message}", e)
                runOnUiThread {
                    // 移除"正在思考中..."的提示
                    if (messageList.isNotEmpty() && 
                        messageList.last().text == "正在思考中..." && 
                        !messageList.last().isUser) {
                        val lastIndex = messageList.size - 1
                        messageList.removeAt(lastIndex)
                        adapter.notifyItemRemoved(lastIndex)
                    }
                    Toast.makeText(this, "无法连接到服务器，请检查后端是否运行", Toast.LENGTH_LONG).show()
                    buttonSend.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e("Main", "网络错误: ${e.message}", e)
                e.printStackTrace()
                runOnUiThread {
                    // 移除"正在思考中..."的提示
                    if (messageList.isNotEmpty() && 
                        messageList.last().text == "正在思考中..." && 
                        !messageList.last().isUser) {
                        val lastIndex = messageList.size - 1
                        messageList.removeAt(lastIndex)
                        adapter.notifyItemRemoved(lastIndex)
                    }
                    Toast.makeText(this, "网络错误：${e.message}", Toast.LENGTH_LONG).show()
                    buttonSend.isEnabled = true
                }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
    
    private fun sendMessageWithFiles(message: String, files: List<SelectedFile>) {
        val userId = getUserId()
        if (userId == -1) {
            runOnUiThread {
                Toast.makeText(this, "用户信息失效，请重新登录", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            return
        }

        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(Config.CHAT_WITH_FILE_URL)
                val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
                
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connectTimeout = 10000
                    readTimeout = 120000
                }
                
                val outputStream = conn.outputStream
                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                
                // 写入 prompt
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n")
                writer.append("$message\r\n")
                
                // 写入 user_id
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"user_id\"\r\n\r\n")
                writer.append("$userId\r\n")
                
                // 写入 conversation_id（如果有）
                conversationId?.let {
                    writer.append("--$boundary\r\n")
                    writer.append("Content-Disposition: form-data; name=\"conversation_id\"\r\n\r\n")
                    writer.append("$it\r\n")
                }
                
                // 写入 file_count
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"file_count\"\r\n\r\n")
                writer.append("${files.size}\r\n")
                
                // 写入文件
                for (i in files.indices) {
                    val file = files[i]
                    val inputStream = contentResolver.openInputStream(file.uri)
                    val fileContent = inputStream?.readBytes() ?: continue
                    inputStream.close()
                    
                    writer.append("--$boundary\r\n")
                    writer.append("Content-Disposition: form-data; name=\"file_$i\"; filename=\"${file.fileName}\"\r\n")
                    writer.append("Content-Type: application/octet-stream\r\n\r\n")
                    writer.flush()
                    
                    outputStream.write(fileContent)
                    outputStream.flush()
                    
                    writer.append("\r\n")
                }
                
                writer.append("--$boundary--\r\n")
                writer.flush()
                writer.close()
                
                val responseCode = conn.responseCode
                val inputStream = if (responseCode in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }
                
                val response = StringBuilder()
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        response.append(line)
                        line = reader.readLine()
                    }
                }
                
                val responseStr = response.toString()
                Log.d("Main", "后端响应（带文件）: $responseStr")
                
                try {
                    val jsonResp = JSONObject(responseStr)
                    val isOk = jsonResp.optBoolean("ok", false)
                    
                    if (isOk) {
                        val content = jsonResp.optString("content", "")
                        val newConversationId = jsonResp.optString("conversation_id", "")
                        
                        if (!newConversationId.isNullOrEmpty()) {
                            conversationId = newConversationId
                        }
                        
                        runOnUiThread {
                            // 移除"正在思考中..."的提示
                            if (messageList.isNotEmpty() && 
                                messageList.last().text == "正在思考中..." && 
                                !messageList.last().isUser) {
                                val lastIndex = messageList.size - 1
                                messageList.removeAt(lastIndex)
                                adapter.notifyItemRemoved(lastIndex)
                            }
                            
                            if (content.isNotEmpty()) {
                                val cleanedContent = content.removePrefix("AI：").trim()
                                addMessageToList(cleanedContent, isUser = false)
                            } else {
                                Log.w("Main", "后端返回的content为空")
                                Toast.makeText(this, "后端返回内容为空", Toast.LENGTH_LONG).show()
                            }
                            
                            buttonSend.isEnabled = true
                        }
                    } else {
                        val errorMsg = jsonResp.optString("error", "请求失败")
                        runOnUiThread {
                            if (messageList.isNotEmpty() && 
                                messageList.last().text == "正在思考中..." && 
                                !messageList.last().isUser) {
                                val lastIndex = messageList.size - 1
                                messageList.removeAt(lastIndex)
                                adapter.notifyItemRemoved(lastIndex)
                            }
                            Toast.makeText(this, "错误: $errorMsg", Toast.LENGTH_LONG).show()
                            buttonSend.isEnabled = true
                        }
                    }
                } catch (jsonEx: Exception) {
                    Log.e("Main", "JSON解析失败: ${jsonEx.message}", jsonEx)
                    runOnUiThread {
                        if (messageList.isNotEmpty() && 
                            messageList.last().text == "正在思考中..." && 
                            !messageList.last().isUser) {
                            val lastIndex = messageList.size - 1
                            messageList.removeAt(lastIndex)
                            adapter.notifyItemRemoved(lastIndex)
                        }
                        Toast.makeText(this, "响应解析失败: ${jsonEx.message}", Toast.LENGTH_LONG).show()
                        buttonSend.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e("Main", "网络错误: ${e.message}", e)
                e.printStackTrace()
                runOnUiThread {
                    if (messageList.isNotEmpty() && 
                        messageList.last().text == "正在思考中..." && 
                        !messageList.last().isUser) {
                        val lastIndex = messageList.size - 1
                        messageList.removeAt(lastIndex)
                        adapter.notifyItemRemoved(lastIndex)
                    }
                    Toast.makeText(this, "网络错误：${e.message}", Toast.LENGTH_LONG).show()
                    buttonSend.isEnabled = true
                }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
    
    /**
     * 加载对话历史消息
     */
    private fun loadConversationHistory(convId: String) {
        val userId = getUserId()
        if (userId == -1) return

        Thread {
            try {
                val url = URL("${Config.BASE_URL}/api/conversation/$convId?user_id=$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    if (json.optBoolean("ok", false)) {
                        val messagesArray = json.getJSONArray("messages")
                        
                        runOnUiThread {
                            messageList.clear()
                            
                            for (i in 0 until messagesArray.length()) {
                                val msg = messagesArray.getJSONObject(i)
                                val role = msg.optString("role", "")
                                val content = msg.optString("content", "")
                                
                                if (role == "user") {
                                    addMessageToList(content, isUser = true)
                                } else if (role == "assistant") {
                                    val cleanedContent = cleanMarkdown(content)
                                    addMessageToList("AI：$cleanedContent", isUser = false)
                                }
                            }
                            
                            // 滚动到底部
                            if (messageList.isNotEmpty()) {
                                recyclerViewMessages.post {
                                    recyclerViewMessages.smoothScrollToPosition(messageList.size - 1)
                                }
                            }
                        }
                    } else {
                        val error = json.optString("error", "未知错误")
                        Log.e("Main", "加载对话历史失败: $error")
                    }
                } else {
                    Log.e("Main", "加载对话历史失败: HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.e("Main", "加载对话历史异常", e)
            }
        }.start()
    }
}
