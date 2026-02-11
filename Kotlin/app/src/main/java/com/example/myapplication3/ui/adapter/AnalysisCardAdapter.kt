package com.example.myapplication3.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication3.R

data class AnalysisCard(
    val title: String,
    val bullets: List<String>
)

class AnalysisCardAdapter : RecyclerView.Adapter<AnalysisCardAdapter.ViewHolder>() {

    private val items = mutableListOf<AnalysisCard>()

    fun submitList(newItems: List<AnalysisCard>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvTag: TextView = itemView.findViewById(R.id.tvTag)
        private val bulletContainer: LinearLayout = itemView.findViewById(R.id.bulletContainer)

        fun bind(card: AnalysisCard) {
            tvTitle.text = card.title
            tvTag.text = when {
                card.title.contains("考点") || card.title.contains("知识") -> "考点"
                card.title.contains("步骤") || card.title.contains("思路") -> "步骤"
                card.title.contains("陷阱") || card.title.contains("易错") -> "易错"
                card.title.contains("答案") || card.title.contains("结论") -> "结论"
                else -> "考研"
            }

            bulletContainer.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            for (b in card.bullets) {
                val tv = inflater.inflate(R.layout.item_bullet_point, bulletContainer, false) as TextView
                tv.text = b
                bulletContainer.addView(tv)
            }
        }
    }
}
