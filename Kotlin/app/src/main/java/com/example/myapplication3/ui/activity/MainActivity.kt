package com.example.myapplication3.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import com.example.myapplication3.R


private lateinit var editTextTask: EditText
private lateinit var listViewTasks: ListView
private lateinit var taskList: MutableList<String>
private lateinit var adapter: ArrayAdapter<String>


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.m)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        editTextTask = findViewById(R.id.editTextTask)
        listViewTasks = findViewById(R.id.listViewTasks)
        taskList = mutableListOf()

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, taskList)
        listViewTasks.adapter = adapter

        val buttonAdd = findViewById<Button>(R.id.buttonAdd)
        buttonAdd.setOnClickListener { addTask(it) }
        val buttonDelete = findViewById<Button>(R.id.buttonDelete)
        buttonDelete.setOnClickListener { deleteTask(it) }
    }
    fun addTask(view: View) {
        val task = editTextTask.text.toString()
        if (task.isNotEmpty()) {
            taskList.add(task)
            adapter.notifyDataSetChanged()
            editTextTask.text.clear()
        }
    }
    fun deleteTask(view: View) {
        if (taskList.isNotEmpty()) {
            taskList.removeAt(taskList.size - 1)
            adapter.notifyDataSetChanged()
        }
    }

}