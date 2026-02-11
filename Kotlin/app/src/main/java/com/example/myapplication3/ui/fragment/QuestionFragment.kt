package com.example.myapplication3.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication3.R
import com.example.myapplication3.ui.activity.QuestionActivity
import com.example.myapplication3.ui.activity.SmartAnalysisActivity
import com.example.myapplication3.ui.activity.StudyReportActivity

class QuestionFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_question, container, false)

        // 现在 fragment_question.xml 中三块都是 MaterialCardView（点击区域），不是 Button
        root.findViewById<View>(R.id.btnStartQuiz).setOnClickListener {
            startActivity(Intent(requireContext(), QuestionActivity::class.java))
        }
        root.findViewById<View>(R.id.btnSmartAnalysis).setOnClickListener {
            startActivity(Intent(requireContext(), SmartAnalysisActivity::class.java))
        }
        root.findViewById<View>(R.id.btnStudyReport).setOnClickListener {
            startActivity(Intent(requireContext(), StudyReportActivity::class.java))
        }

        return root
    }
}
