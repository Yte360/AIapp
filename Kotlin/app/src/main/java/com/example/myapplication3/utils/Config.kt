package com.example.myapplication3.utils

/**
 * 应用配置类
 * 用于管理后端服务器地址等配置
 */
object Config {
    // 后端服务器配置
    // 模拟器使用：http://10.0.2.2:5000
    // 真机使用：http://你的电脑IP:5000（例如：http://192.168.138.65:5000）
    // 确保手机和电脑连接在同一个 WiFi 网络下

    // 自动检测：如果检测到是真机，使用真机地址；否则使用模拟器地址
    private const val USE_REAL_DEVICE = true  // 改为 true 使用真机地址，false 使用模拟器地址

    // 真机地址（需要根据你的实际网络环境修改）
    private const val REAL_DEVICE_HOST = "192.168.1.26"  // 你的电脑 IP 地址

    // 模拟器地址
    private const val EMULATOR_HOST = "10.0.2.2"

    private const val PORT = "5000"

    val BASE_URL: String
        get() {
            val host = if (USE_REAL_DEVICE) REAL_DEVICE_HOST else EMULATOR_HOST
            return "http://$host:$PORT"
        }

    val CHAT_URL: String
        get() = "$BASE_URL/api/chat"

    val CHAT_WITH_FILE_URL: String
        get() = "$BASE_URL/api/chat-with-file"

    val KNOWLEDGE_BASE_URL: String
        get() = BASE_URL
}