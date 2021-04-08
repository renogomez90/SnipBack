package com.hipoint.snipback.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.R;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.enums.TagColours;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.entities.Tags;

import java.util.ArrayList;
import java.util.List;

public class ParentSnipRecyclerAdapter extends RecyclerView.Adapter<ParentSnipRecyclerAdapter.ParentItemViewHolder> {
    private Context context;
    private ItemListener mListener;
    List<Snip> snipArrayList;
    List<Snip> filteredArrayList;
    private String viewChangeValue;
    private List<Integer> allowedIds = new ArrayList<>();
    private List<Tags> tagsList;

    public void setFilterIds(List<Integer> filterIds){
        allowedIds.clear();
        filteredArrayList.clear();
        filteredArrayList.addAll(snipArrayList);

        if (filterIds != null && !filterIds.isEmpty()) {
            allowedIds.addAll(filterIds);
            for (Snip item : snipArrayList) {
                if(!allowedIds.contains(item.getSnip_id())){
                    filteredArrayList.remove(item);
                }
            }
        }

        notifyDataSetChanged();
    }

    public ParentSnipRecyclerAdapter(Context context, List<Snip> allParentSnips, String viewChange, List<Tags> tagsList) {
        this.context = context;
        this.snipArrayList = allParentSnips;
        this.viewChangeValue = viewChange;
        this.tagsList = tagsList;

        this.filteredArrayList = new ArrayList<>();
        this.filteredArrayList.addAll(this.snipArrayList);
    }

    @Override
    public long getItemId(int position) {
        return (long) position;
    }

    @NonNull
    @Override
    public ParentItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ParentItemViewHolder(LayoutInflater.from(context).inflate(R.layout.parentsnip_row_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ParentItemViewHolder holder, int position) {

        if (filteredArrayList != null) {

            int orientation = context.getResources().getConfiguration().orientation;
            //  this is required here to make the tag icons appear properly when enlarged
            if (viewChangeValue != null && orientation == Configuration.ORIENTATION_PORTRAIT) {
                enlargedPortraitView(holder);

            } else if (viewChangeValue != null && orientation == Configuration.ORIENTATION_LANDSCAPE) {
                enlargedLandscapeMode(holder);
            } else {
                enlargedPortraitView(holder);
            }

            if(filteredArrayList.get(position).getVideoFilePath() == null){
                holder.loading.setVisibility(View.VISIBLE);
            } else {
                int parentId = filteredArrayList.get(position).getSnip_id();
                showTags(holder, filteredArrayList.get(position));
                String viewChange = viewChangeValue;
                List<Snip> childSnip = AppClass.getAppInstance().getChildSnipsByParentSnipId(filteredArrayList.get(position).getEvent_id(), parentId);
                setCatItemRecycler(holder.itemRecycler, childSnip, viewChange);
            }
        }

//        holder.itemImage.setImageResource(categoryItemList.get(position).getImageUrl());
    }


    private void setCatItemRecycler(RecyclerView recyclerView, List<Snip> allEventSnips, String viewChange) {
        CategoryItemRecyclerAdapter itemRecyclerAdapter = new CategoryItemRecyclerAdapter(context, allEventSnips, viewChange, tagsList);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
        recyclerView.setAdapter(itemRecyclerAdapter);
    }

    @Override
    public int getItemCount() {
        return filteredArrayList.size();
    }

    /**
     * sets up the UI with tag icons and colours
     *
     * @param holder
     * @param snip
     */
    private void showTags(@NonNull ParentItemViewHolder holder, Snip snip) {
        if(tagsList != null && !tagsList.isEmpty()){
            for (Tags tag : tagsList) {
                if(tag.getSnipId() == snip.getSnip_id()){

                    if(!tag.getTextTag().isEmpty()){
                        holder.textShareTag.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_edit_red));
                        holder.textShareTag.setVisibility(View.VISIBLE);
                    }
                    if(tag.getShareLater()){
                        holder.textShareTag.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_share));
                        holder.textShareTag.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.colorPrimaryRed)));
                        holder.textShareTag.setVisibility(View.VISIBLE);
                    }

                    if (tag.getColourId().equals(TagColours.BLUE.name())){
                        holder.colourFilter.setVisibility(View.VISIBLE);
                        holder.colourFilter.setBackgroundColor(context.getColor(R.color.filter_colour_one));
                        break;
                    } else if(tag.getColourId().equals(TagColours.RED.name())){
                        holder.colourFilter.setVisibility(View.VISIBLE);
                        holder.colourFilter.setBackgroundColor(context.getColor(R.color.filter_colour_two));
                        break;
                    }else if(tag.getColourId().equals(TagColours.ORANGE.name())){
                        holder.colourFilter.setVisibility(View.VISIBLE);
                        holder.colourFilter.setBackgroundColor(context.getColor(R.color.filter_colour_three));
                        break;
                    }else if(tag.getColourId().equals(TagColours.PURPLE.name())){
                        holder.colourFilter.setVisibility(View.VISIBLE);
                        holder.colourFilter.setBackgroundColor(context.getColor(R.color.filter_colour_four));
                        break;
                    }else if(tag.getColourId().equals(TagColours.GREEN.name())){
                        holder.colourFilter.setVisibility(View.VISIBLE);
                        holder.colourFilter.setBackgroundColor(context.getColor(R.color.filter_colour_five));
                        break;
                    }
                }
            }
        }
    }

    private void enlargedPortraitView(ParentItemViewHolder holder) {
        if (viewChangeValue != null) {
            if (viewChangeValue.equals("ENLARGED")) {
                RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 750);
                relativeParams.setMargins((int) dpToPx(context,4), 0, 0, (int) dpToPx(context,10));
                holder.parentHolder.setLayoutParams(relativeParams);
            }
        }
    }

    private void enlargedLandscapeMode(ParentItemViewHolder holder) {
        int totalWidth = context.getResources().getDisplayMetrics().widthPixels;

        if (viewChangeValue != null) {
            if (viewChangeValue.equals("ENLARGED")) {
                RelativeLayout.LayoutParams relativeParams =
                        new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT , RelativeLayout.LayoutParams.MATCH_PARENT);
                relativeParams.setMargins((int) dpToPx(context,4), 0, 0, (int) dpToPx(context,10));
                relativeParams.width = (totalWidth - (int) dpToPx(context,70)) / 2;
                relativeParams.height = (totalWidth - (int) dpToPx(context,70)) / 2;
                relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                relativeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
                holder.parentHolder.setLayoutParams(relativeParams);

            }
        }
    }

    public float dpToPx(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public class ParentItemViewHolder extends RecyclerView.ViewHolder {
        RelativeLayout parentHolder;
        RecyclerView itemRecycler;
        ProgressBar loading;
        ImageView colourFilter;
        ImageView textShareTag;

        public ParentItemViewHolder(@NonNull View itemView) {
            super(itemView);
            loading      = itemView.findViewById(R.id.gallery_item_loading);
            itemRecycler = itemView.findViewById(R.id.item_recycler);
            colourFilter = itemView.findViewById(R.id.color_tag);
            textShareTag = itemView.findViewById(R.id.tagged_overlay);
            parentHolder = itemView.findViewById(R.id.parent_holder);
        }
    }

    public interface ItemListener {
        void onItemClick(Snip snipvideopath);
    }
}
