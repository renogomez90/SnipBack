package com.hipoint.snipback.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.R;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.Snip;

import java.util.ArrayList;
import java.util.List;

public class ParentSnipRecyclerAdapter extends RecyclerView.Adapter<ParentSnipRecyclerAdapter.ParentItemViewHolder> {
    private Context context;
    private ItemListener mListener;
    List<Snip> snipArrayList;
    private  String viewChangeValue;


    public ParentSnipRecyclerAdapter(Context context, List<Snip> allParentSnips,String viewChange) {
        this.context = context;
        this.snipArrayList = allParentSnips;
        this.viewChangeValue = viewChange;

    }



    @NonNull
    @Override
    public ParentItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ParentItemViewHolder(LayoutInflater.from(context).inflate(R.layout.parentsnip_row_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ParentItemViewHolder holder, int position) {

        if (snipArrayList != null) {
//            Log.d("action2",viewChangeValue+"");

            int parentId = snipArrayList.get(position).getSnip_id();
            String viewChange = viewChangeValue;
            List<Snip> childSnip = AppClass.getAppInsatnce().getChildSnipsByParentSnipId(snipArrayList.get(position).getEvent_id(), parentId);
            setCatItemRecycler(holder.itemRecycler, childSnip,viewChange);

        }

//        holder.itemImage.setImageResource(categoryItemList.get(position).getImageUrl());
    }


    private void setCatItemRecycler(RecyclerView recyclerView, List<Snip> allEventSnips,String viewChange) {
        CategoryItemRecyclerAdapter itemRecyclerAdapter = new CategoryItemRecyclerAdapter(context, allEventSnips,viewChange);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
        recyclerView.setAdapter(itemRecyclerAdapter);
    }

    @Override
    public int getItemCount() {
        return snipArrayList.size();
    }

    public class ParentItemViewHolder extends RecyclerView.ViewHolder {
        RecyclerView itemRecycler;
        public ParentItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemRecycler=itemView.findViewById(R.id.item_recycler);
        }
    }

    public interface ItemListener {
        void onItemClick(Snip snipvideopath);
    }
}
