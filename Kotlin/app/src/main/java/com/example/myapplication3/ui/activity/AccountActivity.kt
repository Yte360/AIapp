package com.example.myapplication3.ui.activity

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication3.R

class AccountActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        supportActionBar?.title = "账号信息"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 当前仅做前端展示/编辑（未接入后端持久化）
        findViewById<ImageView>(R.id.imgAvatarAccount).setImageResource(R.drawable.ic_launcher_foreground)

        val tvUsername = findViewById<TextView>(R.id.tvAccountUsername)
        val tvEmail = findViewById<TextView>(R.id.tvAccountEmail)

        tvUsername.text = "11111"
        tvEmail.text = ""

        findViewById<Button>(R.id.btnEditAccount).setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_account, null)
            val editUsername = dialogView.findViewById<EditText>(R.id.editUsername)
            val editEmail = dialogView.findViewById<EditText>(R.id.editEmail)

            editUsername.setText(tvUsername.text.toString())
            editEmail.setText(tvEmail.text.toString())

            AlertDialog.Builder(this)
                .setTitle("编辑账号信息")
                .setView(dialogView)
                .setPositiveButton("保存") { _, _ ->
                    tvUsername.text = editUsername.text.toString().trim().ifEmpty { "11111" }
                    tvEmail.text = editEmail.text.toString().trim()
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
