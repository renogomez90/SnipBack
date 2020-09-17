package com.hipoint.snipback.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.R;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.Snip;

import java.util.List;

public class ParentSnipRecyclerAdapter extends RecyclerView.Adapter<ParentSnipRecyclerAdapter.ParentItemViewHolder> {
    private Context context;
    private ItemListener mListener;
    List<Snip> snipArrayList;
    private  String viewChangeValue;
    private  Integer orientationVal;


    public ParentSnipRecyclerAdapter(Context context, List<Snip> allParentSnips, String viewChange, Integer orientation) {
        this.context = context;
        this.snipArrayList = allParentSnips;
        this.viewChangeValue = viewChange;
        this.orientationVal=orientation;
    }

    @NonNull
    @Override
    public ParentItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ParentItemViewHolder(LayoutInflater.from(context).inflate(R.layout.parentsnip_row_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ParentItemViewHolder holder, int position) {

        if (snipArrayList != null) {

            int parentId = snipArrayList.get(position).getSnip_id();
            String viewChange = viewChangeValue;
            Integer orientation= orientationVal;
            List<Snip> childSnip = AppClass.getAppInstance().getChildSnipsByParentSnipId(snipArrayList.get(position).getEvent_id(), parentId);
            setCatItemRecycler(holder.itemRecycler, childSnip,viewChange,orientation);

        }

//        holder.itemImage.setImageResource(categoryItemList.get(position).getImageUrl());
    }


    private void setCatItemRecycler(RecyclerView recyclerView, List<Snip> allEventSnips, String viewChange, Integer orientation) {
        CategoryItemRecyclerAdapter itemRecyclerAdapter = new CategoryItemRecyclerAdapter(context, allEventSnips,viewChange,orientation);
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
