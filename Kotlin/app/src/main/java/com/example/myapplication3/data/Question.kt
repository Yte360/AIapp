package com.example.myapplication3.data

data class Question(
    val id: Int,
    val question: String,
    val options: List<String>,
    val correctAnswer: Int, // 0, 1, 2, 3 对应 A, B, C, D
    val category: String = "计算机考研"
)