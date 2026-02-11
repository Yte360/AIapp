package com.example.myapplication3.ui.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication3.ui.adapter.AnalysisCard
import com.example.myapplication3.ui.adapter.AnalysisCardAdapter
import com.example.myapplication3.data.KgNode
import com.example.myapplication3.data.KnowledgeGraph
import com.example.myapplication3.data.KnowledgeGraphParser
import com.example.myapplication3.ui.view.KnowledgeGraphView
import com.example.myapplication3.data.MindMapNode
import com.example.myapplication3.ui.view.MindMapView
import com.example.myapplication3.R
import com.example.myapplication3.utils.Config
import com.google.android.material.tabs.TabLayout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmartAnalysisActivity : AppCompatActivity() {

    private lateinit var etQuestion: EditText
    private lateinit var btnAnalyze: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var tabLayout: TabLayout
    private lateinit var rvCards: RecyclerView
    private lateinit var scrollMindmap: View
    private lateinit var mindMapView: MindMapView
    private lateinit var knowledgeGraphView: KnowledgeGraphView

    private val adapter = AnalysisCardAdapter()

    private var lastMindMapRoot: MindMapNode? = null
    private var lastGraph: KnowledgeGraph? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_smart_analysis)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etQuestion = findViewById(R.id.etQuestion)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        progressBar = findViewById(R.id.progressBar)

        tabLayout = findViewById(R.id.tabLayout)
        rvCards = findViewById(R.id.rvCards)
        scrollMindmap = findViewById(R.id.scrollMindmap)
        mindMapView = findViewById(R.id.mindMapView)
        knowledgeGraphView = findViewById(R.id.knowledgeGraphView)

        rvCards.layoutManager = LinearLayoutManager(this)
        rvCards.adapter = adapter

        setupTabs()

        btnAnalyze.setOnClickListener {
            val q = etQuestion.text.toString().trim()
            if (q.isEmpty()) {
                Toast.makeText(this, "请输入考研题目或问题", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            analyzeQuestion(q)
        }
    }

    private fun setupTabs() {
        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText("知识卡片").setIcon(R.drawable.ic_tab_cards))
        tabLayout.addTab(tabLayout.newTab().setText("思维导图").setIcon(R.drawable.ic_tab_mindmap))
        tabLayout.addTab(tabLayout.newTab().setText("知识图谱").setIcon(R.drawable.ic_tab_graph))

        showCards()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showCards()
                    1 -> showMindMap()
                    2 -> showGraph()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) {
                onTabSelected(tab)
            }
        })
    }

    private fun crossfade(toShow: View, toHide1: View, toHide2: View) {
        // hide others immediately
        toHide1.visibility = View.GONE
        toHide2.visibility = View.GONE
        toHide1.alpha = 1f
        toHide2.alpha = 1f

        // fade in target
        toShow.alpha = 0f
        toShow.visibility = View.VISIBLE
        toShow.animate().alpha(1f).setDuration(180).start()
    }

    private fun showCards() {
        crossfade(rvCards, scrollMindmap, knowledgeGraphView)
    }

    private fun showMindMap() {
        mindMapView.setMindMap(lastMindMapRoot ?: MindMapNode(
            topic = "暂无导图数据",
            children = emptyList()
        )
        )
        crossfade(scrollMindmap, rvCards, knowledgeGraphView)
    }

    private fun showGraph() {
        knowledgeGraphView.setGraph(lastGraph ?: KnowledgeGraph(
            nodes = listOf(
                KgNode(
                    "-",
                    "暂无图谱数据"
                )
            ), edges = emptyList()
        )
        )
        crossfade(knowledgeGraphView, rvCards, scrollMindmap)
    }

    private fun analyzeQuestion(question: String) {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userId = sp.getInt("user_id", -1)
        
        if (userId == -1) {
            Toast.makeText(this, "用户信息失效，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnAnalyze.isEnabled = false

        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(Config.CHAT_URL)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 60000
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                val prompt = buildExamAnalysisPrompt(question)
                val reqJson = JSONObject().apply {
                    put("prompt", prompt)
                    put("user_id", userId)
                    put("save_history", false) // 分析请求不计入历史记录
                }

                BufferedWriter(OutputStreamWriter(conn.outputStream, "UTF-8")).use { writer ->
                    writer.write(reqJson.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseStr = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }

                val parsed = parseResponse(responseStr)

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnAnalyze.isEnabled = true

                    adapter.submitList(parsed.cards)
                    lastMindMapRoot = parsed.mindmap
                    lastGraph = parsed.graph

                    when (tabLayout.selectedTabPosition) {
                        1 -> showMindMap()
                        2 -> showGraph()
                        else -> showCards()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                    Toast.makeText(this, "解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                    adapter.submitList(listOf(
                        AnalysisCard(
                            "解析失败",
                            listOf(e.message ?: "未知错误")
                        )
                    ))
                    lastMindMapRoot = MindMapNode("暂无导图数据")
                    lastGraph = null
                    when (tabLayout.selectedTabPosition) {
                        1 -> showMindMap()
                        2 -> showGraph()
                        else -> showCards()
                    }
                }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private data class ParsedResult(
        val cards: List<AnalysisCard>,
        val mindmap: MindMapNode?,
        val graph: KnowledgeGraph?
    )

    private fun buildExamAnalysisPrompt(question: String): String {
        return """
你是一名计算机考研题目智能解析助手。请对用户题目进行解析，
并严格以 JSON 返回（不要包含任何多余文本、不要使用 Markdown）。

输出 JSON 格式：
{
  "cards": [
    {"title": "考点/知识点", "bullets": ["..."]},
    {"title": "解题思路（步骤）", "bullets": ["..."]},
    {"title": "易错点与陷阱", "bullets": ["..."]},
    {"title": "答案与结论", "bullets": ["..."]},
    {"title": "延伸练习建议", "bullets": ["..."]}
  ],
  "mindmap": {
    "root": {
      "topic": "题目解析",
      "children": [
        {"topic": "考点", "children": []},
        {"topic": "解题步骤", "children": []},
        {"topic": "易错点", "children": []},
        {"topic": "结论", "children": []}
      ]
    }
  },
  "graph": {
    "nodes": [
      {"id": "Q", "label": "题目/问题"},
      {"id": "K", "label": "考点"},
      {"id": "S", "label": "解题步骤"},
      {"id": "T", "label": "易错点"},
      {"id": "A", "label": "答案"}
    ],
    "edges": [
      {"from": "Q", "to": "K", "label": "涉及"},
      {"from": "Q", "to": "S", "label": "求解"},
      {"from": "S", "to": "A", "label": "得到"},
      {"from": "T", "to": "A", "label": "影响"}
    ]
  }
}

要求：
- cards 的 bullets 每条尽量简短（1~2 行），面向考研复习。
- mindmap.root.topic 为整题主题；children 为主要分支；每个分支 children 继续细分要点。
- graph.nodes/edges 必须与题目解析内容一致：node label 简短；edge label 用“涉及/导致/属于/对比/推导/条件”等。
- 若题目缺少选项/条件，请先给出需要补充的信息，并给出在常见设定下的通用解法。

用户题目：
$question
""".trimIndent()
    }

    private fun parseResponse(responseStr: String): ParsedResult {
        return try {
            val root = JSONObject(responseStr)
            val rawContent = root.optString("content", "")
            val content = sanitizeContentToJsonObject(rawContent)

            val cards = tryParseCardsFromJsonString(content) ?: fallbackCards(content.ifEmpty { responseStr })
            val mindmap = tryParseMindMapFromJsonString(content)
            val graph = tryParseGraphFromJsonString(content)

            ParsedResult(cards = cards, mindmap = mindmap, graph = graph)
        } catch (_: Exception) {
            ParsedResult(cards = fallbackCards(responseStr), mindmap = null, graph = null)
        }
    }

    private fun tryParseGraphFromJsonString(content: String): KnowledgeGraph? {
        if (content.isBlank()) return null
        return try {
            val obj = JSONObject(content)
            val graphObj = obj.optJSONObject("graph") ?: return null
            KnowledgeGraphParser.fromJson(graphObj)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseMindMapFromJsonString(content: String): MindMapNode? {
        if (content.isBlank()) return null
        return try {
            val obj = JSONObject(content)
            val mindmapObj = obj.optJSONObject("mindmap") ?: return null
            val rootObj = mindmapObj.optJSONObject("root") ?: return null
            MindMapNode.Companion.fromJson(rootObj)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseCardsFromJsonString(content: String): List<AnalysisCard>? {
        if (content.isBlank()) return null
        return try {
            val obj = JSONObject(content)
            if (!obj.has("cards")) return null
            val arr = obj.getJSONArray("cards")
            val cards = ArrayList<AnalysisCard>(arr.length())
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val title = c.optString("title", "")
                val bulletsArr = c.optJSONArray("bullets")
                val bullets = mutableListOf<String>()
                if (bulletsArr != null) {
                    for (j in 0 until bulletsArr.length()) {
                        val b = bulletsArr.optString(j, "").trim()
                        if (b.isNotEmpty()) bullets.add(b)
                    }
                }
                if (title.isNotEmpty() || bullets.isNotEmpty()) {
                    cards.add(AnalysisCard(if (title.isNotEmpty()) title else "要点", bullets))
                }
            }
            if (cards.isEmpty()) null else cards
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackCards(text: String): List<AnalysisCard> {
        val lines = text
            .replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(30)

        return listOf(
            AnalysisCard(
                title = "解析结果",
                bullets = if (lines.isEmpty()) listOf("暂无内容") else lines
            )
        )
    }

    /**
     * 清洗 LLM 返回的 content：
     * - 兼容 ```json ... ``` 代码块
     * - 兼容前后夹杂说明文字（提取第一个 { 到最后一个 }）
     */
    private fun sanitizeContentToJsonObject(raw: String): String {
        var s = raw.trim()

        // 去掉常见的 ```json / ``` 包裹
        if (s.startsWith("```")) {
            // 去掉开头第一行 ``` 或 ```json
            val firstLineEnd = s.indexOf('\n')
            s = if (firstLineEnd != -1) s.substring(firstLineEnd + 1) else ""
            // 去掉结尾 ```
            val endFence = s.lastIndexOf("```")
            if (endFence != -1) {
                s = s.substring(0, endFence)
            }
            s = s.trim()
        }

        // 提取最外层 JSON 对象
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            s.substring(start, end + 1).trim()
        } else {
            raw.trim()
        }
    }
}

data class ConversationItem(
    val id: Int,
    val conversationId: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val messageCount: Int
)

class ConversationListActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationAdapter
    private val conversations = mutableListOf<ConversationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_conversation_list)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerView = findViewById(R.id.recyclerViewConversations)
        adapter = ConversationAdapter(conversations,
            onItemClick = { conversation ->
                // 点击对话，跳转到聊天界面
                val intent = Intent(this, Main::class.java).apply {
                    putExtra("conversation_id", conversation.conversationId)
                }
                startActivity(intent)
                finish()
            },
            onDeleteClick = { conversation ->
                // 删除对话
                deleteConversation(conversation)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 加载对话列表
        loadConversations()
    }

    /**
     * 获取当前登录用户的 ID
     */
    private fun getUserId(): Int {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        return sp.getInt("user_id", -1)
    }

    private fun loadConversations() {
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
            try {
                val url = URL("${Config.BASE_URL}/api/conversations?user_id=$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)

                    if (json.optBoolean("ok", false)) {
                        val conversationsArray = json.getJSONArray("conversations")
                        conversations.clear()

                        for (i in 0 until conversationsArray.length()) {
                            val item = conversationsArray.getJSONObject(i)
                            conversations.add(
                                ConversationItem(
                                    id = item.optInt("id", 0),
                                    conversationId = item.optString("conversation_id", ""),
                                    title = item.optString("title", "未命名对话"),
                                    createdAt = item.optString("created_at", ""),
                                    updatedAt = item.optString("updated_at", ""),
                                    messageCount = item.optInt("message_count", 0)
                                )
                            )
                        }

                        runOnUiThread {
                            adapter.notifyDataSetChanged()
                            if (conversations.isEmpty()) {
                                Toast.makeText(this, "还没有对话记录", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val error = json.optString("error", "未知错误")
                        runOnUiThread {
                            Toast.makeText(this, "加载失败: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "网络错误: $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ConversationList", "加载对话列表失败", e)
                runOnUiThread {
                    Toast.makeText(this, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    fun onCreateConversation(view: View) {
        // 创建新对话
        val intent = Intent(this, Main::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * 删除对话
     */
    private fun deleteConversation(conversation: ConversationItem) {
        // 显示确认对话框
        AlertDialog.Builder(this)
            .setTitle("删除对话")
            .setMessage("确定要删除对话「${conversation.title}」吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                performDelete(conversation)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行删除操作
     */
    private fun performDelete(conversation: ConversationItem) {
        val userId = getUserId()
        if (userId == -1) return

        Thread {
            try {
                val url = URL("${Config.BASE_URL}/api/conversation/${conversation.conversationId}?user_id=$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)

                    runOnUiThread {
                        if (json.optBoolean("ok", false)) {
                            Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show()
                            // 重新加载对话列表
                            loadConversations()
                        } else {
                            val error = json.optString("error", "未知错误")
                            Toast.makeText(this, "删除失败: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "删除失败: HTTP $responseCode", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ConversationList", "删除对话失败", e)
                runOnUiThread {
                    Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}

class ConversationAdapter(
    private val conversations: List<ConversationItem>,
    private val onItemClick: (ConversationItem) -> Unit,
    private val onDeleteClick: (ConversationItem) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.textViewTitle)
        val timeText: TextView = itemView.findViewById(R.id.textViewTime)
        val countText: TextView = itemView.findViewById(R.id.textViewMessageCount)
        val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]

        holder.titleText.text = conversation.title
        holder.countText.text = "${conversation.messageCount} 条消息"

        // 格式化时间
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = dateFormat.parse(conversation.updatedAt)
            val displayFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            holder.timeText.text = displayFormat.format(date ?: Date())
        } catch (e: Exception) {
            holder.timeText.text = conversation.updatedAt
        }

        // 点击整个项，跳转到聊天界面
        holder.itemView.setOnClickListener {
            onItemClick(conversation)
        }

        // 点击删除按钮，删除对话
        holder.deleteButton.setOnClickListener {
            // 阻止事件冒泡，避免触发 itemView 的点击事件
            it.isEnabled = false
            onDeleteClick(conversation)
            // 延迟恢复按钮状态
            it.postDelayed({ it.isEnabled = true }, 500)
        }
    }

    override fun getItemCount(): Int = conversations.size
}