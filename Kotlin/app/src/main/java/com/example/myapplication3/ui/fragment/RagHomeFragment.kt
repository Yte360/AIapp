package com.example.myapplication3.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication3.R
import com.example.myapplication3.ui.activity.DigitalHumanActivity
import com.example.myapplication3.ui.activity.Main

class RagHomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_rag_home, container, false)

        root.findViewById<View>(R.id.cardStartQA).setOnClickListener {
            startActivity(Intent(requireContext(), Main::class.java))
        }

        root.findViewById<View>(R.id.cardDigital).setOnClickListener {
            startActivity(Intent(requireContext(), DigitalHumanActivity::class.java))
        }

        return root
    }
}
