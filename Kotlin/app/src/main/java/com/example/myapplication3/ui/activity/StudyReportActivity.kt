package com.example.myapplication3.ui.activity

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication3.data.QuestionRepository
import com.example.myapplication3.R
import com.example.myapplication3.ui.view.TopicBarChartView
import kotlin.math.max

class StudyReportActivity : AppCompatActivity() {

    private data class TopicStat(
        val topic: String,
        val wrongCount: Int,
        val totalCount: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_study_report)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val tvTotal = findViewById<TextView>(R.id.tvTotal)
        val tvWrong = findViewById<TextView>(R.id.tvWrong)
        val tvAccuracy = findViewById<TextView>(R.id.tvAccuracy)
        val tvFocusTopic = findViewById<TextView>(R.id.tvFocusTopic)
        val tvPlan = findViewById<TextView>(R.id.tvPlan)
        val barChart = findViewById<TopicBarChartView>(R.id.barChart)

        val btnRegenerate = findViewById<Button>(R.id.btnRegenerate)
        val btnStartPractice = findViewById<Button>(R.id.btnStartPractice)

        val topicStats = listOf(
            TopicStat(topic = "组成原理", wrongCount = 2, totalCount = 0),
            TopicStat(topic = "计算机网络", wrongCount = 0, totalCount = 0),
            TopicStat(topic = "算法分析", wrongCount = 1, totalCount = 0),
            TopicStat(topic = "数据库", wrongCount = 1, totalCount = 0)
        )

        val wrong = topicStats.sumOf { it.wrongCount }
        val total = wrong
        val accuracy = if (total == 0) 0 else ((total - wrong) * 100 / total)

        val focus = topicStats.filter { it.wrongCount > 0 }.maxByOrNull { it.wrongCount }?.topic ?: "无"

        tvTotal.text = "总题数：$total"
        tvWrong.text = "错题：$wrong"
        tvAccuracy.text = "正确率：$accuracy%"
        tvFocusTopic.text = "重点提升：$focus"

        // 图表：展示所有错题分布
        val chartEntries = topicStats
            .map {
                TopicBarChartView.Entry(
                    label = it.topic.take(4),
                    value = it.wrongCount
                )
            }
        barChart.setData(chartEntries)

        fun renderPlan() {
            tvPlan.text = buildRlPlanText(topicStats)
        }

        renderPlan()

        btnRegenerate.setOnClickListener {
            // 当前用模拟数据，重新生成仅体现“策略文案刷新”
            renderPlan()
        }

        btnStartPractice.setOnClickListener {
            // 回到刷题 Tab 的入口页：直接关闭即可回到主 Tab
            finish()
        }
    }

    /**
     * 参照题库问题（QuestionRepository + Main.kt 里的解析）粗略映射知识点。
     * 说明：QuestionRepository 当前没有显式 knowledgePoint 字段，因此这里做启发式映射。
     */
    private fun mapQuestionToTopic(questionId: Int): String {
        return when (questionId) {
            1 -> "排序" // 快排
            2 -> "网络" // ARP
            3 -> "数据结构" // 树
            4 -> "操作系统" // 进程调度
            5 -> "网络" // HTTP
            6 -> "数据库" // ACID
            7 -> "组成原理" // 寄存器
            8 -> "算法" // 分治
            9 -> "数据库" // MongoDB
            10 -> "编译原理" // 语法树
            else -> "综合"
        }
    }

    private fun buildTopicStats(wrongQuestionIds: Set<Int>): List<TopicStat> {
        val totalByTopic = mutableMapOf<String, Int>()
        val wrongByTopic = mutableMapOf<String, Int>()

        for (q in QuestionRepository.questions) {
            val topic = mapQuestionToTopic(q.id)
            totalByTopic[topic] = (totalByTopic[topic] ?: 0) + 1
            if (wrongQuestionIds.contains(q.id)) {
                wrongByTopic[topic] = (wrongByTopic[topic] ?: 0) + 1
            }
        }

        val topics = totalByTopic.keys
        return topics.map { t ->
            TopicStat(
                topic = t,
                wrongCount = wrongByTopic[t] ?: 0,
                totalCount = totalByTopic[t] ?: 0
            )
        }.sortedByDescending { it.wrongCount }
    }

    /**
     * 体现 RL 思路：
     * - 状态：知识点掌握度（由错题率近似）
     * - 动作：下一步出题分配（复习/刷题）
     * - 奖励：做对 +1，做错 -1（用来更新策略）
     * - 策略：对错题多的知识点提升采样概率（类似 bandit/策略梯度直觉）
     */
    private fun buildRlPlanText(stats: List<TopicStat>): String {
        if (stats.isEmpty()) return "暂无数据"

        val topWeak = stats.sortedByDescending { it.wrongCount }.take(3)

        val lines = mutableListOf<String>()
        lines.add("1）LLM 解析错题 → 提取薄弱知识点（状态 State）。")
        lines.add("2）基于错题率动态调整下一轮练习的知识点权重（策略 Policy）。")
        lines.add("3）你做对记为奖励 +1，做错记为惩罚 -1，LLM 会更新权重，让训练更聚焦。")
        lines.add("")

        lines.add("推荐学习顺序（优先级由 RL 权重决定）：")
        for ((index, s) in topWeak.withIndex()) {
            val total = max(1, s.totalCount)
            val wrongRate = (s.wrongCount * 100 / total)
            lines.add("- P${index + 1}：${s.topic}（错题 ${s.wrongCount}/$total，错题率 ${wrongRate}%）")
            lines.add("  建议：先看解析/概念 10min → 再做同类题 8~12 题 → 复盘错因")
        }

        lines.add("")
        lines.add("下一轮出题策略（示例）：")
        lines.add("- 薄弱知识点占 60%（强化）")
        lines.add("- 一般知识点占 30%（保持）")
        lines.add("- 随机综合占 10%（防遗忘）")

        return lines.joinToString("\n")
    }
}
