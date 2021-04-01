package com.hipoint.snipback.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.hipoint.snipback.R

class TagsRecyclerAdapter(val context: Context, val tagsList: List<String>): RecyclerView.Adapter<TagsRecyclerAdapter.TagViewHolder>() {

    private val selectedList = arrayListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.text_tag_item,
            parent,
            false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.tagText.text = tagsList[position]
        holder.tagHolder.tag = tagsList[position]

        if(selectedList.contains(tagsList[position])){
            holder.tagHolder.background = ResourcesCompat.getDrawable(context.resources, R.drawable.rounded_white, context.theme)
        } else {
            holder.tagHolder.background = ResourcesCompat.getDrawable(context.resources, R.drawable.rounded_corners_white, context.theme)
        }

        holder.tagHolder.setOnClickListener {
            if(selectedList.contains(it.tag)){
                selectedList.remove(it.tag)
            } else {
                selectedList.add(it.tag as String)
            }
            notifyItemChanged(position)
        }

        holder.deleteTag.setOnClickListener {

        }
    }

    override fun getItemCount(): Int = tagsList.size

    override fun getItemId(position: Int): Long = position.toLong()

    fun getSelectedItems(): List<String> = selectedList

    class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tagHolder: TextView    = itemView.findViewById(R.id.tagHolder)
        val tagText  : TextView    = itemView.findViewById(R.id.tagText)
        val deleteTag: ImageButton = itemView.findViewById(R.id.deleteTag)
    }
}