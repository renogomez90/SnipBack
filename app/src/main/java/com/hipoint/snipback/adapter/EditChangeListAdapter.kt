package com.hipoint.snipback.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.exozet.android.core.extensions.notificationManager
import com.hipoint.snipback.R
import com.hipoint.snipback.enums.UserEditType
import com.hipoint.snipback.listener.IJumpToEditPoint
import com.hipoint.snipback.videoControl.SpeedDetails

class EditChangeListAdapter(val context: Context, val editList: ArrayList<SpeedDetails>) : RecyclerView.Adapter<EditChangeListAdapter.PreviewTileVH>() {

    private var currentEditList = editList
    private var editPressListener: IJumpToEditPoint? = null

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
        /*when(editList[position]){
            UserEditType.SPEED_UP -> {
                Glide.with(context).load(R.drawable.speed).into(holder.previewTile)
            }
            UserEditType.SLOW_DOWN -> {
                Glide.with(context).load(R.drawable.ic_slow).into(holder.previewTile)
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
        }*/

        holder.previewTile.tag = position

        holder.previewTile.setOnClickListener {
            Toast.makeText(context, "${currentEditList[it.tag as Int].timeDuration!!.first}," +
                    " ${currentEditList[it.tag as Int].timeDuration!!.second}", Toast.LENGTH_SHORT).show()

            val pos = it.tag as Int
            editPressListener?.editPoint(pos, currentEditList[pos])
        }

        if(editList[position].isFast){
            holder.previewTile.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.blue))
            Glide.with(context).load(R.drawable.speed).into(holder.previewTile)
        }else{
            holder.previewTile.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.green))
            Glide.with(context).load(R.drawable.ic_slow).into(holder.previewTile)
        }
    }

    fun updateList(newList: List<SpeedDetails>){
        currentEditList.clear()
        currentEditList.addAll(newList)
        notifyDataSetChanged()
    }

    fun setEditPressListener(listener: IJumpToEditPoint){
        editPressListener = listener
    }

    class PreviewTileVH(view: View) : RecyclerView.ViewHolder(view) {
        val previewTile: ImageView = view.findViewById(R.id.edit_tile)
    }
}