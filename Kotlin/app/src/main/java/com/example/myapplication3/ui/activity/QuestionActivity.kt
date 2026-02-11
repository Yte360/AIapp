package com.example.myapplication3.ui.activity

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import com.example.myapplication3.data.QuestionRepository
import com.example.myapplication3.R

class QuestionActivity : AppCompatActivity() {

    private lateinit var tvQuestion: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvQuestionCount: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnNext: Button
    private lateinit var btnPrev: Button
    private lateinit var radioGroup: RadioGroup
    private lateinit var cardQuestion: CardView
    private lateinit var optionA: RadioButton
    private lateinit var optionB: RadioButton
    private lateinit var optionC: RadioButton
    private lateinit var optionD: RadioButton
    private lateinit var cardProgress: CardView
    private lateinit var cardSummary: CardView
    private lateinit var layoutQuestionDetails: LinearLayout
    private lateinit var layoutExplanations: LinearLayout
    private lateinit var tvTotalScore: TextView

    private lateinit var loadingDialog: Dialog
    private lateinit var scrollView: NestedScrollView

    private var currentQuestionId = 1
    private var score = 0
    private var answeredQuestions = mutableSetOf<Int>()
    private var userAnswers = mutableMapOf<Int, Int>() // 记录用户答案
    private var isExamFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_question)

        initViews()
        setupListeners()
        loadQuestion(currentQuestionId)
        updateProgress()
    }

    private fun initViews() {
        tvQuestion = findViewById(R.id.tvQuestion)
        tvFeedback = findViewById(R.id.tvFeedback)
        tvProgress = findViewById(R.id.tvProgress)
        tvQuestionCount = findViewById(R.id.tvQuestionCount)
        tvScore = findViewById(R.id.tvScore)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        radioGroup = findViewById(R.id.radioGroup)
        cardQuestion = findViewById(R.id.cardQuestion)
        optionA = findViewById(R.id.optionA)
        optionB = findViewById(R.id.optionB)
        optionC = findViewById(R.id.optionC)
        optionD = findViewById(R.id.optionD)
        cardProgress = findViewById(R.id.cardProgress)
        cardSummary = findViewById(R.id.cardSummary)
        layoutQuestionDetails = findViewById(R.id.layoutQuestionDetails)
        layoutExplanations = findViewById(R.id.layoutExplanations)
        tvTotalScore = findViewById(R.id.tvTotalScore)

        // 初始化加载对话框
        loadingDialog = Dialog(this)
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        loadingDialog.setContentView(R.layout.dialog_loading)
        loadingDialog.setCancelable(false)
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 初始化滚动视图
        scrollView = findViewById(R.id.scrollView)
    }

    private fun setupListeners() {
        btnSubmit.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            if (selectedId != -1) {
                val selectedIndex = when (selectedId) {
                    R.id.optionA -> 0
                    R.id.optionB -> 1
                    R.id.optionC -> 2
                    R.id.optionD -> 3
                    else -> -1
                }

                // 显示加载对话框
                showLoadingDialog()

                // 延迟1秒后显示答案
                Handler(Looper.getMainLooper()).postDelayed({
                    // 隐藏对话框
                    hideLoadingDialog()

                    // 检查答案并立即更新反馈
                    checkAnswer(selectedIndex)
                    answeredQuestions.add(currentQuestionId)
                    userAnswers[currentQuestionId] = selectedIndex
                    updateProgress()

                    // 显示反馈（checkAnswer() 已经设置好了）
                    tvFeedback.visibility = View.VISIBLE

                    // 自动滚动到底部
                    scrollToBottom()
                }, 1000)

            } else {
                tvFeedback.text = "请选择一个答案"
                tvFeedback.setTextColor(Color.RED)
                tvFeedback.visibility = View.VISIBLE

                // 自动滚动到反馈区域
                scrollToBottom()
            }
        }

        btnNext.setOnClickListener {
            if (isExamFinished) {
                // 显示答题汇总
                showExamSummary()
            } else if (currentQuestionId < QuestionRepository.totalQuestions) {
                currentQuestionId++
                loadQuestion(currentQuestionId)
            } else {
                // 最后一题，准备交卷
                prepareForSubmission()
            }
        }

        btnPrev.setOnClickListener {
            if (currentQuestionId > 1) {
                currentQuestionId--
                loadQuestion(currentQuestionId)
            }
        }
    }

    private fun showLoadingDialog() {
        if (!loadingDialog.isShowing) {
            loadingDialog.show()
            // 禁用按钮防止重复点击
            btnSubmit.isEnabled = false
            btnPrev.isEnabled = false
            btnNext.isEnabled = false
        }
    }

    private fun hideLoadingDialog() {
        if (loadingDialog.isShowing) {
            loadingDialog.dismiss()
            // 重新启用按钮
            btnSubmit.isEnabled = true
            btnPrev.isEnabled = true
            btnNext.isEnabled = true
        }
    }

    private fun scrollToBottom() {
        Handler(Looper.getMainLooper()).postDelayed({
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }, 150)
    }

    private fun loadQuestion(questionId: Int) {
        val question = QuestionRepository.getQuestionById(questionId)
        question?.let {
            tvQuestion.text = "Q${questionId}: ${it.question}"
            optionA.text = "A: ${it.options[0]}"
            optionB.text = "B: ${it.options[1]}"
            optionC.text = "C: ${it.options[2]}"
            optionD.text = "D: ${it.options[3]}"

            // 更新题目计数
            tvQuestionCount.text = "题目: $questionId/${QuestionRepository.totalQuestions}"

            // 不清除反馈区域，只清除选择（如果需要的话）
            // tvFeedback.text = ""  // 移除这行

            // 如果是最后一题，更新按钮文本
            if (questionId == QuestionRepository.totalQuestions) {
                btnNext.text = "交卷"
            } else {
                btnNext.text = "下一题"
            }

            // 恢复用户之前的选择
            restoreUserAnswer()

            // 如果这道题已经回答过，显示反馈
            if (userAnswers.containsKey(questionId)) {
                val userAnswer = userAnswers[questionId]
                if (userAnswer != null) {
                    // 显示之前提交的反馈
                    showPreviousFeedback(questionId, userAnswer)
                }
            } else {
                // 如果这道题还没回答过，清空反馈
                tvFeedback.text = ""
                tvFeedback.visibility = View.GONE
            }
        }
    }

    private fun restoreUserAnswer() {
        val previousAnswer = userAnswers[currentQuestionId]
        if (previousAnswer != null) {
            when (previousAnswer) {
                0 -> radioGroup.check(R.id.optionA)
                1 -> radioGroup.check(R.id.optionB)
                2 -> radioGroup.check(R.id.optionC)
                3 -> radioGroup.check(R.id.optionD)
            }
        } else {
            radioGroup.clearCheck()
        }
    }
    private fun showPreviousFeedback(questionId: Int, userAnswer: Int) {
        val question = QuestionRepository.getQuestionById(questionId)
        question?.let {
            val isCorrect = userAnswer == it.correctAnswer

            if (isCorrect) {
                tvFeedback.text = "✅ 正确！\n${getExplanation(questionId)}"
                tvFeedback.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                val correctOption = when (it.correctAnswer) {
                    0 -> "A"
                    1 -> "B"
                    2 -> "C"
                    3 -> "D"
                    else -> ""
                }
                tvFeedback.text = "❌ 错误！正确答案是 $correctOption: ${it.options[it.correctAnswer]}\n${getExplanation(questionId)}"
                tvFeedback.setTextColor(Color.parseColor("#F44336"))
            }

            tvFeedback.visibility = View.VISIBLE
        }
    }
    private fun checkAnswer(userAnswer: Int) {
        val question = QuestionRepository.getQuestionById(currentQuestionId)
        question?.let {
            val isCorrect = userAnswer == it.correctAnswer

            if (isCorrect) {
                tvFeedback.text = "✅ 正确！\n${getExplanation(currentQuestionId)}"
                tvFeedback.setTextColor(Color.parseColor("#4CAF50"))
                score++
            } else {
                val correctOption = when (it.correctAnswer) {
                    0 -> "A"
                    1 -> "B"
                    2 -> "C"
                    3 -> "D"
                    else -> ""
                }
                tvFeedback.text = "❌ 错误！正确答案是 $correctOption: ${it.options[it.correctAnswer]}\n${getExplanation(currentQuestionId)}"
                tvFeedback.setTextColor(Color.parseColor("#F44336"))
            }

            // 更新进度
            updateProgress()
        }
    }

    private fun getExplanation(questionId: Int): String {
        return when (questionId) {
            1 -> "快速排序的平均时间复杂度为O(n log n)，是最快的排序算法之一"
            2 -> "ARP协议负责将IP地址解析为MAC地址"
            3 -> "树是非线性数据结构，其他都是线性结构"
            4 -> "时间片用完是进程从运行态转为就绪态的主要原因"
            5 -> "HTTP是应用层协议，其他都是网络层或传输层协议"
            6 -> "ACID分别代表原子性、一致性、隔离性、持久性"
            7 -> "寄存器是CPU内部存储器，访问速度最快"
            8 -> "分治法的核心思想是'分而治之'"
            9 -> "MongoDB是文档型数据库，不是关系数据库"
            10 -> "语法分析的主要任务是生成语法树"
            else -> ""
        }
    }

    private fun updateProgress() {
        val progress = (answeredQuestions.size.toFloat() / QuestionRepository.totalQuestions * 100).toInt()

        // 更新进度条
        findViewById<ProgressBar>(R.id.progressBar).progress = progress

        // 更新进度文本
        tvProgress.text = "学习进度 $progress%"

        // 更新得分显示（显示得分/10）
        tvScore.text = "得分: $score/${QuestionRepository.totalQuestions}"

        // 如果已经回答完所有题目
        if (answeredQuestions.size == QuestionRepository.totalQuestions) {
            tvRecommendation.text = "🎉 恭喜！已完成所有题目"
        } else {
            // 基于RL的学习推荐
            val unansweredCount = QuestionRepository.totalQuestions - answeredQuestions.size
            tvRecommendation.text = "基于你的学习进度推荐题目"
        }
    }

    private fun prepareForSubmission() {
        // 检查是否有未答的题目
        val unansweredCount = QuestionRepository.totalQuestions - answeredQuestions.size
        if (unansweredCount > 0) {
            // 提示还有题目未答
            val alertDialog = AlertDialog.Builder(this)
                .setTitle("交卷确认")
                .setMessage("还有 $unansweredCount 题未回答，确定要交卷吗？")
                .setPositiveButton("确定交卷") { _, _ ->
                    isExamFinished = true
                    btnNext.text = "查看成绩"
                    showExamSummary()
                }
                .setNegativeButton("继续答题", null)
                .create()
            alertDialog.show()
        } else {
            isExamFinished = true
            btnNext.text = "查看成绩"
            showExamSummary()
        }
    }

    private fun showExamSummary() {
        // 隐藏题目和反馈区域
        cardQuestion.visibility = View.GONE
        tvFeedback.visibility = View.GONE
        btnSubmit.visibility = View.GONE
        btnPrev.visibility = View.GONE
        cardProgress.visibility = View.GONE

        // 显示汇总区域
        cardSummary.visibility = View.VISIBLE

        // 显示总分
        tvTotalScore.text = "总分: $score/${QuestionRepository.totalQuestions}"

        // 清空之前的内容
        layoutQuestionDetails.removeAllViews()

        // 添加各题详情和解析
        for (i in 1..QuestionRepository.totalQuestions) {
            val question = QuestionRepository.getQuestionById(i)
            question?.let {
                // 创建题目详情视图（使用新的包含选项的布局）
                val detailView = LayoutInflater.from(this).inflate(R.layout.item_question_detail_with_explanation, null)

                val tvQuestionNum = detailView.findViewById<TextView>(R.id.tvQuestionNum)
                val tvQuestionContent = detailView.findViewById<TextView>(R.id.tvQuestionContent)
                val tvOptionA = detailView.findViewById<TextView>(R.id.tvOptionA)
                val tvOptionB = detailView.findViewById<TextView>(R.id.tvOptionB)
                val tvOptionC = detailView.findViewById<TextView>(R.id.tvOptionC)
                val tvOptionD = detailView.findViewById<TextView>(R.id.tvOptionD)
                val tvQuestionStatus = detailView.findViewById<TextView>(R.id.tvQuestionStatus)
                val tvUserAnswer = detailView.findViewById<TextView>(R.id.tvUserAnswer)
                val tvCorrectAnswer = detailView.findViewById<TextView>(R.id.tvCorrectAnswer)
                val tvExplanation = detailView.findViewById<TextView>(R.id.tvExplanation)

                // 显示题目编号
                tvQuestionNum.text = "第${i}题"

                // 显示题目内容
                tvQuestionContent.text = "题目: ${it.question}"

                // 显示选项
                tvOptionA.text = "A. ${it.options[0]}"
                tvOptionB.text = "B. ${it.options[1]}"
                tvOptionC.text = "C. ${it.options[2]}"
                tvOptionD.text = "D. ${it.options[3]}"

                val userAnswer = userAnswers[i]
                val isCorrect = userAnswer == it.correctAnswer

                if (userAnswer != null) {
                    // 显示用户答案
                    val userAnswerText = when (userAnswer) {
                        0 -> "A. ${it.options[0]}"
                        1 -> "B. ${it.options[1]}"
                        2 -> "C. ${it.options[2]}"
                        3 -> "D. ${it.options[3]}"
                        else -> "未答"
                    }
                    val correctAnswerText = when (it.correctAnswer) {
                        0 -> "A. ${it.options[0]}"
                        1 -> "B. ${it.options[1]}"
                        2 -> "C. ${it.options[2]}"
                        3 -> "D. ${it.options[3]}"
                        else -> ""
                    }

                    if (isCorrect) {
                        tvQuestionStatus.text = "✅ 正确"
                        tvQuestionStatus.setBackgroundResource(R.drawable.status_bg_correct)
                        tvQuestionStatus.setTextColor(Color.WHITE)
                    } else {
                        tvQuestionStatus.text = "❌ 错误"
                        tvQuestionStatus.setBackgroundResource(R.drawable.status_bg_wrong)
                        tvQuestionStatus.setTextColor(Color.WHITE)
                    }

                    tvUserAnswer.text = "👤 你的答案: $userAnswerText"
                    tvCorrectAnswer.text = "✅ 正确答案: $correctAnswerText"

                } else {
                    // 未答题目的情况
                    tvQuestionStatus.text = "⭕ 未答"
                    tvQuestionStatus.setBackgroundResource(R.drawable.status_bg_unanswered)
                    tvQuestionStatus.setTextColor(Color.WHITE)
                    tvUserAnswer.text = "👤 你的答案: 未答"

                    val correctAnswerText = when (it.correctAnswer) {
                        0 -> "A. ${it.options[0]}"
                        1 -> "B. ${it.options[1]}"
                        2 -> "C. ${it.options[2]}"
                        3 -> "D. ${it.options[3]}"
                        else -> ""
                    }
                    tvCorrectAnswer.text = "✅ 正确答案: $correctAnswerText"
                }

                // 显示解析
                tvExplanation.text = "💡 解析: ${getExplanation(i)}"

                layoutQuestionDetails.addView(detailView)
            }
        }

        // 滚动到顶部
        scrollView.scrollTo(0, 0)

        // 更新按钮
        btnNext.text = "返回首页"
        btnNext.setOnClickListener {
            finish() // 返回主页面
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (loadingDialog.isShowing) {
            loadingDialog.dismiss()
        }
    }
}