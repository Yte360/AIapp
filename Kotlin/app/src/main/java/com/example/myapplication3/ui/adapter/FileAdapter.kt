package com.example.myapplication3.ui.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication3.R

data class SelectedFile(
    val uri: Uri,
    val fileName: String
)

class FileAdapter(
    private val files: MutableList<SelectedFile>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textFileName: TextView = itemView.findViewById(R.id.textFileName)
        val buttonRemove: View = itemView.findViewById(R.id.buttonRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.textFileName.text = file.fileName
        holder.buttonRemove.setOnClickListener {
            onRemove(position)
        }
    }

    override fun getItemCount(): Int = files.size
}
