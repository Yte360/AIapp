package com.example.myapplication3.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.myapplication3.ui.activity.CameraScreen

class HealthFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                // 直接嵌入相机界面，onBack 暂时为空，因为在 Tab 内没有返回操作
                CameraScreen(onBack = {})
            }
        }
    }
}
