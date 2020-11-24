package com.hipoint.snipback.adapter

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.hipoint.snipback.R

class TimelinePreviewAdapter(val context: Context, val photoList: ArrayList<Bitmap>) : RecyclerView.Adapter<TimelinePreviewAdapter.PreviewTileVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewTileVH {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_preview_photos_small, parent, false)
        return PreviewTileVH(view)
    }

    override fun getItemCount(): Int {
        return photoList.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onBindViewHolder(holder: PreviewTileVH, position: Int) {
        Glide.with(context).load(photoList[position]).into(holder.previewTile)
    }

    class PreviewTileVH(view: View) : RecyclerView.ViewHolder(view) {
        val previewTile: ImageView = view.findViewById(R.id.previewTile)
    }
}