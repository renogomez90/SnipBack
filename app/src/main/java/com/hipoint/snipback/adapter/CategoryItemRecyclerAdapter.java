package com.hipoint.snipback.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.R;
import com.hipoint.snipback.room.entities.CategoryItem;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Snip;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CategoryItemRecyclerAdapter extends RecyclerView.Adapter<CategoryItemRecyclerAdapter.CategoryItemViewHolder> {
    private Context context;
    private ItemListener mListener;
    List<Snip> snipArrayList;

    public CategoryItemRecyclerAdapter(Context context, List<Snip> allSnips) {
        this.context = context;
        this.snipArrayList = allSnips;
    }

    @NonNull
    @Override
    public CategoryItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CategoryItemViewHolder(LayoutInflater.from(context).inflate(R.layout.category_row_items,parent,false));
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryItemViewHolder holder, int position) {
        if (snipArrayList != null){
            Snip snip = snipArrayList.get(position);
            try {
                int duration;
                if(snip.getIs_virtual_version() == 1){
                    holder.tvVersionLabel.setVisibility(View.VISIBLE);
                    holder.tvVersionLabel.setText("VERSION "+position);
                    duration =  (int) snipArrayList.get(position).getSnip_duration();
                }else{
                    holder.tvVersionLabel.setVisibility(View.INVISIBLE);
                    duration =  (int) snipArrayList.get(position).getTotal_video_duration();
                }
                int hours = duration / 3600;
                int minutes = (duration % 3600) / 60;
                int seconds = duration % 60;

                if(hours > 0) {
                    holder.tvDuration.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                }else{
                    holder.tvDuration.setText(String.format("%02d:%02d", minutes, seconds));
                }
                Bitmap myBitmap = BitmapFactory.decodeFile(snip.getThumbnailPath());
                holder.itemImage.setImageBitmap(myBitmap);
                holder.itemImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(context, ActivityPlayVideo.class);
                        intent.putExtra("snip", snip);
                        context.startActivity(intent);
                    }
                });
//                holder.itemImage.setOnClickListener(v -> mListener.onItemClick(snipArrayList.get(position)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

//        holder.itemImage.setImageResource(categoryItemList.get(position).getImageUrl());
    }

    @Override
    public int getItemCount() {
        return snipArrayList.size();
    }

    public class CategoryItemViewHolder extends RecyclerView.ViewHolder {
        ImageView itemImage;
        TextView tvVersionLabel;
        TextView tvDuration;
        public CategoryItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemImage = itemView.findViewById(R.id.image);
            tvVersionLabel = itemView.findViewById(R.id.tvVersionLabel);
            tvDuration = itemView.findViewById(R.id.tvDuration);
        }
    }

    public interface ItemListener {
        void onItemClick(Snip snipvideopath);
    }
}
