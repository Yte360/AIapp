package com.example.myapplication3.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication3.R
import com.example.myapplication3.utils.Config
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etAccount = findViewById<EditText>(R.id.etAccount)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val tvForgot = findViewById<TextView>(R.id.tvForgot)

        // 登录逻辑：对接后端数据库校验
        btnLogin.setOnClickListener {
            val account = etAccount.text.toString().trim()
            val pwd = etPassword.text.toString().trim()
            
            if (account.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(account, pwd)
        }

        // 这里仅做提示
        tvRegister.setOnClickListener {
            Toast.makeText(this, "注册功能暂未实现", Toast.LENGTH_SHORT).show()
        }
        tvForgot.setOnClickListener {
            Toast.makeText(this, "找回密码功能暂未实现", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 执行登录请求
     */
    private fun performLogin(account: String, pwd: String) {
        val client = OkHttpClient()
        
        // 构建请求体 JSON
        val json = JSONObject().apply {
            put("user_account", account)
            put("user_password", pwd)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        // 构建请求，使用 Config.BASE_URL
        val request = Request.Builder()
            .url("${Config.BASE_URL}/api/login")
            .post(requestBody)
            .build()

        updateLoginButtonState(false)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    updateLoginButtonState(true)
                    Toast.makeText(this@LoginActivity, "服务器连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    updateLoginButtonState(true)
                    try {
                        val jsonResponse = JSONObject(responseBody ?: "")
                        if (jsonResponse.optBoolean("ok")) {
                            // 登录成功，保存用户信息
                            val userObj = jsonResponse.optJSONObject("user")
                            if (userObj != null) {
                                saveUserInfo(
                                    userObj.optInt("user_id"),
                                    userObj.optString("user_account"),
                                    userObj.optString("user_name")
                                )
                            }
                            
                            Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                            // 跳转到主页
                            startActivity(Intent(this@LoginActivity, MainTabActivity::class.java))
                            finish()
                        } else {
                            val error = jsonResponse.optString("error", "账号或密码错误")
                            Toast.makeText(this@LoginActivity, error, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "登录解析异常", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    /**
     * 更新登录按钮状态
     */
    private fun updateLoginButtonState(enabled: Boolean) {
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        btnLogin.isEnabled = enabled
        btnLogin.text = if (enabled) "登录" else "登录中..."
    }

    /**
     * 持久化保存用户信息
     */
    private fun saveUserInfo(id: Int, account: String, name: String) {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        sp.edit().apply {
            putInt("user_id", id)
            putString("user_account", account)
            putString("user_name", name)
            putBoolean("is_logged_in", true)
            apply()
        }
    }
}