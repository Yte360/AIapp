package com.example.myapplication3.ui.activity

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication3.R

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        supportActionBar?.title = "关于"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val versionName = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: ""
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }

        findViewById<TextView>(R.id.tvAboutAppName).text = getString(R.string.app_name)
        findViewById<TextView>(R.id.tvAboutVersion).text = "版本：$versionName"

        findViewById<TextView>(R.id.tvAboutDesc).text = "这是一款面向考研学习的应用，提供问答辅导、刷题练习与学情分析等能力，帮助你更高效地复习与查漏补缺。"
        findViewById<TextView>(R.id.tvAboutFeatures).text = "功能亮点：\n- 智能问答：结合知识库进行辅助解答\n- 刷题练习：按题库进行专项训练\n- 学情报告：统计薄弱点并给出复习建议\n- 文件解析：支持上传资料进行内容分析"
        findViewById<TextView>(R.id.tvAboutContact).text = "联系方式：\n- 邮箱：support@example.com\n- 反馈：我的 > 关于 > 意见反馈（后续可接入）"
        findViewById<TextView>(R.id.tvAboutDisclaimer).text = "免责声明：\n本应用提供的内容仅供学习参考，不构成任何保证或承诺。请以教材与课程要求为准。"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
