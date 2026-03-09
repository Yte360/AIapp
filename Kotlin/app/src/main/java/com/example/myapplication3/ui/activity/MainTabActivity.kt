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
import com.example.myapplication3.viewmodel.CameraViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent

class MainTabActivity : AppCompatActivity() {

    private var isInCameraScreen = false
    val cameraViewModel by lazy { CameraViewModel() }

    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_tab)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            val selectedId = item.itemId

            // 如果从 CameraScreen 切换到其他页面，根据进入前是否有悬浮窗决定是否显示
            if (isInCameraScreen && selectedId != R.id.menu_health) {
                val shouldShowFloating = FloatingWindowService.isRunning() || 
                    !getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("had_floating_before_fullscreen", false)
                if (shouldShowFloating) {
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
        val tag = when (fragment) {
            is HealthFragment -> "health"
            is QuestionFragment -> "question"
            is RagHomeFragment -> "rag"
            is ProfileFragment -> "profile"
            else -> null
        }
        
        val existingFragment = tag?.let { supportFragmentManager.findFragmentByTag(it) }
        
        val transaction = supportFragmentManager.beginTransaction()
        
        currentFragment?.let { transaction.hide(it) }
        
        if (existingFragment != null) {
            transaction.show(existingFragment)
            currentFragment = existingFragment
        } else {
            transaction.add(R.id.fragmentContainer, fragment, tag)
            currentFragment = fragment
        }
        
        transaction.commit()
    }
}