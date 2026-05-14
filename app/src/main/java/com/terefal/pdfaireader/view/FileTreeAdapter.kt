package com.terefal.pdfaireader.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.terefal.pdfaireader.R
import com.terefal.pdfaireader.data.NoteBook
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileTreeAdapter(
    private val onNoteBookClick: (NoteBook) -> Unit,
    private val onNoteBookDelete: (NoteBook) -> Unit
) : ListAdapter<NoteBook, FileTreeAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    class ViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_notebook, parent, false)
    ) {
        val icon: TextView = itemView.findViewById(R.id.itemIcon)
        val title: TextView = itemView.findViewById(R.id.itemTitle)
        val date: TextView = itemView.findViewById(R.id.itemDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val nb = getItem(position)
        holder.icon.text = if (nb.pdfUri != null) "📄" else "📝"
        holder.title.text = nb.title
        holder.date.text = dateFormat.format(Date(nb.updatedAt))
        holder.itemView.setOnClickListener { onNoteBookClick(nb) }
        holder.itemView.setOnLongClickListener {
            onNoteBookDelete(nb)
            true
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<NoteBook>() {
        override fun areItemsTheSame(old: NoteBook, new: NoteBook) = old.id == new.id
        override fun areContentsTheSame(old: NoteBook, new: NoteBook) = old == new
    }
}
