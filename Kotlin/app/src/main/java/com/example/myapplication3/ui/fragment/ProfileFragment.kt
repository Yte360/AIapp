package com.example.myapplication3.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myapplication3.R
import com.example.myapplication3.ui.activity.AboutActivity
import com.example.myapplication3.ui.activity.AccountActivity
import com.example.myapplication3.ui.activity.LoginActivity
import com.example.myapplication3.ui.activity.Main
import com.example.myapplication3.ui.activity.SettingsActivity

class ProfileFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_profile, container, false)

        root.findViewById<View>(R.id.llAccount).setOnClickListener {
            startActivity(Intent(requireContext(), AccountActivity::class.java))
        }
        root.findViewById<View>(R.id.llSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        root.findViewById<View>(R.id.llAbout).setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        root.findViewById<View>(R.id.llLogout).setOnClickListener {
            Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
            // TODO: 退出登录逻辑，如清理 token、跳转到登录页等
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        return root
    }
}

class RagFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        startActivity(Intent(requireContext(), Main::class.java))
        return View(requireContext())
    }
}