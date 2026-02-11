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
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainTabActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_tab)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.menu_quiz -> switchFragment(QuestionFragment())
                R.id.menu_health -> switchFragment(HealthFragment())
                R.id.menu_rag -> switchFragment(RagHomeFragment())
                R.id.menu_profile -> switchFragment(ProfileFragment())
            }
            true
        }

        // 默认选中刷题
        bottomNav.selectedItemId = R.id.menu_quiz
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}