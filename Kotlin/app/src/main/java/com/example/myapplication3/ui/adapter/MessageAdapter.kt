package com.example.myapplication3.ui.adapter

import android.os.Build
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication3.R

data class Message(
    val text: String,
    val isUser: Boolean
)

class MessageAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textUserMessage: TextView = itemView.findViewById(R.id.textUserMessage)
        val textAIMessage: TextView = itemView.findViewById(R.id.textAIMessage)
        val layoutUser: View = itemView.findViewById(R.id.layoutUser)
        val layoutAI: View = itemView.findViewById(R.id.layoutAI)
        val textUserLabel: TextView = itemView.findViewById(R.id.textUserLabel)
        val textAILabel: TextView = itemView.findViewById(R.id.textAILabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        if (message.isUser) {
            // 显示用户消息
            holder.layoutUser.visibility = View.VISIBLE
            holder.layoutAI.visibility = View.GONE
            holder.textUserMessage.text = formatText(message.text)
        } else {
            // 显示AI消息
            holder.layoutUser.visibility = View.GONE
            holder.layoutAI.visibility = View.VISIBLE
            holder.textAIMessage.text = formatText(message.text)
        }
    }

    override fun getItemCount(): Int = messages.size

    /**
     * 格式化文本，支持简单的富文本显示
     */
    private fun formatText(text: String): Spanned {
        var formatted = text
        
        // 移除 Markdown 标题符号
        formatted = formatted.replace(Regex("^#{1,6}\\s+"), "")
        
        // 转换粗体 **text** -> <b>text</b>
        formatted = formatted.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        
        // 转换斜体 *text* -> <i>text</i> (但避免与粗体冲突)
        formatted = formatted.replace(Regex("(?<!\\*)\\*([^*]+?)\\*(?!\\*)"), "<i>$1</i>")
        
        // 转换列表项 - item -> • item
        formatted = formatted.replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "• ")
        
        // 移除表格分隔符
        formatted = formatted.replace("|", " ")
        
        // 移除代码块符号
        formatted = formatted.replace(Regex("```[\\w]*"), "")
        formatted = formatted.replace(Regex("`([^`]+)`"), "$1")
        
        // 转换换行为 <br>
        formatted = formatted.replace("\n", "<br>")
        
        // 清理多余空格
        formatted = formatted.replace(Regex(" {2,}"), " ")
        
        // 使用 HTML 解析（支持 Android 8.0+）
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(formatted, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(formatted)
        }
    }
}
