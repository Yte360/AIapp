package com.example.myapplication3.data

object QuestionRepository {

    val questions = listOf(
        Question(
            id = 1,
            question = "下列排序算法中，平均时间复杂度为O(n log n)的是？",
            options = listOf("冒泡排序", "插入排序", "快速排序", "选择排序"),
            correctAnswer = 2 // C
        ),
        Question(
            id = 2,
            question = "在TCP/IP协议中，负责将IP地址转换为MAC地址的是？",
            options = listOf("DNS", "ARP", "RARP", "ICMP"),
            correctAnswer = 1 // B
        ),
        Question(
            id = 3,
            question = "下列数据结构中，属于非线性结构的是？",
            options = listOf("队列", "栈", "树", "线性表"),
            correctAnswer = 2 // C
        ),
        Question(
            id = 4,
            question = "在操作系统中，进程状态从运行态到就绪态的原因是？",
            options = listOf("时间片用完", "等待I/O", "进程终止", "进程创建"),
            correctAnswer = 0 // A
        ),
        Question(
            id = 5,
            question = "下列协议中，工作在应用层的是？",
            options = listOf("IP", "TCP", "HTTP", "ARP"),
            correctAnswer = 2 // C
        ),
        Question(
            id = 6,
            question = "在数据库系统中，事务的ACID特性中，C代表的是？",
            options = listOf("原子性", "一致性", "隔离性", "持久性"),
            correctAnswer = 1 // B
        ),
        Question(
            id = 7,
            question = "下列存储器中，访问速度最快的是？",
            options = listOf("硬盘", "内存", "缓存", "寄存器"),
            correctAnswer = 3 // D
        ),
        Question(
            id = 8,
            question = "在算法设计中，分治法的基本思想是？",
            options = listOf("自顶向下", "自底向上", "分而治之", "贪心选择"),
            correctAnswer = 2 // C
        ),
        Question(
            id = 9,
            question = "下列不是关系数据库的是？",
            options = listOf("MySQL", "Oracle", "MongoDB", "PostgreSQL"),
            correctAnswer = 2 // C
        ),
        Question(
            id = 10,
            question = "在编译过程中，语法分析的任务是？",
            options = listOf("词法分析", "语义分析", "中间代码生成", "生成语法树"),
            correctAnswer = 3 // D
        )
    )

    val totalQuestions = questions.size

    fun getQuestionById(id: Int): Question? {
        return questions.find { it.id == id }
    }
}