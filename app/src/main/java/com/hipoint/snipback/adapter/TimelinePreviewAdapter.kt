package com.hipoint.snipback.adapter

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.hipoint.snipback.R


class TimelinePreviewAdapter(val context: Context, val photoList: ArrayList<Bitmap>) : RecyclerView.Adapter<TimelinePreviewAdapter.PreviewTileVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewTileVH {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_preview_photos_small,
            parent,
            false)
        return PreviewTileVH(view)
    }

    override fun getItemCount(): Int {
        return photoList.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onBindViewHolder(holder: PreviewTileVH, position: Int) {
        if(photoList.size <= 9){

            val currentWidth = holder.previewTile.width
            val displaymetrics = DisplayMetrics()
            (context as Activity).windowManager.defaultDisplay.getMetrics(displaymetrics)
            val devicewidth = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                displaymetrics.widthPixels / photoList.size
            } else {
                displaymetrics.heightPixels / photoList.size
            }

            holder.previewTile.layoutParams.width = devicewidth
        }
        Glide.with(context)
            .load(photoList[position])
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(holder.previewTile)
    }

    class PreviewTileVH(view: View) : RecyclerView.ViewHolder(view) {
        val previewTile: ImageView = view.findViewById(R.id.previewTile)
    }
}