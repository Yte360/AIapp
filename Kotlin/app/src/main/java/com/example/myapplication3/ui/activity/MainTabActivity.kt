package com.example.myapplication3.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.myapplication3.ui.fragment.HealthFragment
import com.example.myapplication3.ui.fragment.ProfileFragment
import com.example.myapplication3.ui.fragment.QuestionFragment
import com.example.myapplication3.R
import com.example.myapplication3.ui.fragment.RagHomeFragment
import com.example.myapplication3.service.FloatingWindowService
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent

class MainTabActivity : AppCompatActivity() {

    private var isInCameraScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_tab)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            val selectedId = item.itemId

            // 如果从 CameraScreen 切换到其他页面，显示悬浮窗
            if (isInCameraScreen && selectedId != R.id.menu_health) {
                if (FloatingWindowService.isRunning()) {
                    FloatingWindowService.show(this)
                }
                isInCameraScreen = false
            }

            when (selectedId) {
                R.id.menu_quiz -> switchFragment(QuestionFragment())
                R.id.menu_health -> switchFragment(HealthFragment())
                R.id.menu_rag -> switchFragment(RagHomeFragment())
                R.id.menu_profile -> switchFragment(ProfileFragment())
            }
            true
        }

        // 检查是否需要打开 CameraScreen 全屏模式
        if (intent.getBooleanExtra("open_camera_fullscreen", false)) {
            intent.removeExtra("open_camera_fullscreen")
            isInCameraScreen = true
            bottomNav.selectedItemId = R.id.menu_health
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("open_camera_fullscreen", true)
                .apply()
        } else {
            bottomNav.selectedItemId = R.id.menu_quiz
        }
    }

    fun setInCameraScreen(inCamera: Boolean) {
        isInCameraScreen = inCamera
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}