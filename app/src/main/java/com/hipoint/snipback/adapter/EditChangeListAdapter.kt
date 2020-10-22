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
import com.hipoint.snipback.enums.UserEditType

class EditChangeListAdapter(val context: Context, val editList: ArrayList<UserEditType>) : RecyclerView.Adapter<EditChangeListAdapter.PreviewTileVH>() {



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewTileVH {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_edit_list_item, parent, false)
        return PreviewTileVH(view)
    }

    override fun getItemCount(): Int {
        return editList.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onBindViewHolder(holder: PreviewTileVH, position: Int) {
        when(editList[position]){
            UserEditType.SPEED_UP,
            UserEditType.SLOW_DOWN -> {
                Glide.with(context).load(R.drawable.speed).into(holder.previewTile)
            }
            UserEditType.EXTEND -> {
                Glide.with(context).load(R.drawable.ic_extend).into(holder.previewTile)
            }
            UserEditType.CUT -> {
                Glide.with(context).load(R.drawable.cut).into(holder.previewTile)
            }
            UserEditType.HIGHLIGHT -> {
                Glide.with(context).load(R.drawable.ic_highlight).into(holder.previewTile)
            }
        }
    }

    class PreviewTileVH(view: View) : RecyclerView.ViewHolder(view) {
        val previewTile: ImageView = view.findViewById(R.id.edit_tile)
    }
}