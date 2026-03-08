package com.example.myapplication3.ui.activity

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication3.R
import com.example.myapplication3.service.FloatingWindowService
import com.example.myapplication3.service.MonitoringForegroundService
import com.example.myapplication3.ui.activity.CameraScreen
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
                            // 显示监测引导弹窗
                            showStartMonitoringDialog()
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

    private fun showStartMonitoringDialog() {
        val sp = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userId = sp.getInt("user_id", 1)

        AlertDialog.Builder(this)
            .setTitle("欢迎使用学习监测助手")
            .setMessage("是否立即开始人脸监测？\n\n开启后将持续在后台追踪您的学习状态，无需手动操作。")
            .setPositiveButton("开始监测") { _, _ ->
                // 请求必要权限后启动后台服务
                requestPermissionsAndStartMonitoring(userId)
            }
            .setNegativeButton("稍后再说") { _, _ ->
                // 跳转到首页
                startActivity(Intent(this, MainTabActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private var pendingUserId: Int = 1

    private fun requestPermissionsAndStartMonitoring(userId: Int) {
        // 检查悬浮窗权限（需要特殊处理）
        if (!android.provider.Settings.canDrawOverlays(this)) {
            // 引导用户开启悬浮窗权限
            android.widget.Toast.makeText(
                this,
                "需要开启悬浮窗权限才能使用监测功能",
                android.widget.Toast.LENGTH_LONG
            ).show()
            
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1001)
            pendingUserId = userId
            return
        }

        val permissionsNeeded = mutableListOf<String>()

        // 检查相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        // 检查通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            pendingUserId = userId
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            startMonitoringService(userId)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            // 检查悬浮窗权限是否已授权
            if (android.provider.Settings.canDrawOverlays(this)) {
                startMonitoringService(pendingUserId)
            } else {
                android.widget.Toast.makeText(
                    this,
                    "需要悬浮窗权限才能使用监测功能",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                // 跳转到首页
                startActivity(Intent(this, MainTabActivity::class.java))
                finish()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }

        if (allGranted) {
            startMonitoringService(pendingUserId)
        } else {
            Toast.makeText(this, "需要相机权限才能进行监测", Toast.LENGTH_SHORT).show()
        }

        // 无论权限是否授予，都跳转到首页
        startActivity(Intent(this, MainTabActivity::class.java))
        finish()
    }

    private fun startMonitoringService(userId: Int) {
        // 启动悬浮窗服务进行监测
        if (android.provider.Settings.canDrawOverlays(this)) {
            FloatingWindowService.start(this, userId)
            Toast.makeText(this, "监测服务已启动", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要悬浮窗权限才能使用监测功能", Toast.LENGTH_SHORT).show()
        }
        
        // 跳转到首页
        startActivity(Intent(this, MainTabActivity::class.java))
        finish()
    }
}