package com.hipoint.snipback.adapter;

import android.content.Context;
import android.content.res.Configuration;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.R;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.fragment.FragmentGalleryNew;
import com.hipoint.snipback.room.entities.EventData;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.entities.Tags;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainRecyclerAdapter extends RecyclerView.Adapter<MainRecyclerAdapter.MainViewHolder> {

    private Context context;
    private List<EventData> parentSnips;
    private List<EventData> allSnips;
    private EventData tempData = new EventData();
    private String viewChangeValue;
    private Integer orientationValue;
    private int eventId = -1;
    private List<Integer> allowedIds = new ArrayList<>();   //  to filter the gallery listing
    private List<Tags> tagsList;    //  to show the icons for associated tags

    public MainRecyclerAdapter(Context context, List<EventData> allParentSnip, List<EventData> allEventSnip, String viewChange, List<Tags> tagsList) {
        this.context = context;
        this.allSnips = new ArrayList<>();
        this.parentSnips = new ArrayList<>();
        this.allSnips.addAll(allEventSnip);
        this.parentSnips.addAll(allParentSnip);
        this.viewChangeValue = viewChange;
        this.tagsList = tagsList;
    }

    @Override
    public long getItemId(int position) {
        return (long) position;
    }

    public void updateData(List<EventData> updatedAllParentSnip, List<EventData> updatedAllEventSnip, String updatedViewChange, List<Tags> tagList){
        /*GalleryDiffUtlCallback parentDiffCallback = new GalleryDiffUtlCallback(this.parentSnips, updatedAllParentSnip);
        GalleryDiffUtlCallback snipDiffCallback = new GalleryDiffUtlCallback(this.allSnips, updatedAllEventSnip);
        DiffUtil.DiffResult parentDiffResult = DiffUtil.calculateDiff(parentDiffCallback);
        DiffUtil.DiffResult snipDiffResult = DiffUtil.calculateDiff(snipDiffCallback);*/
        this.allSnips.clear();
        this.parentSnips.clear();
        this.allSnips.addAll(updatedAllEventSnip);
        this.parentSnips.addAll(updatedAllParentSnip);
        this.viewChangeValue = updatedViewChange;
        this.tagsList = tagList;
        /*parentDiffResult.dispatchUpdatesTo(this);
        snipDiffResult.dispatchUpdatesTo(this);*/
        notifyDataSetChanged();
    }

    public void showLoading(boolean show){
        if(show) {
            tempData.setEvent(AppClass.getAppInstance().lastCreatedEvent);
            tempData.addEventParentSnip(new Snip());
            parentSnips.add(tempData);
            notifyItemInserted(parentSnips.lastIndexOf(tempData));
        } else {
            parentSnips.remove(tempData);
            notifyItemRangeChanged(0, parentSnips.size() - 1);
        }
    }

    @NonNull
    @Override
    public MainViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        return new MainViewHolder(LayoutInflater.from(context).inflate(R.layout.main_recycler_row_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MainViewHolder holder, int position) {
//        holder.categoryTitle.setText(getDate(parentSnips.get(position).getEvent_created()));
//        if(parentSnips.get(position).getEvent().getEvent_id() != eventId){
//            holder.categoryTitle.setVisibility(View.VISIBLE);
//            holder.categoryTitle.setText(parentSnips.get(position).getEvent().getEvent_title());
//        }else{
//            holder.categoryTitle.setVisibility(View.GONE);
//        }
        if(parentSnips.get(position).getParentSnip().get(0).getVideoFilePath() == null){
            holder.categoryTitle.setVisibility(View.INVISIBLE);
        } else {
            holder.categoryTitle.setVisibility(View.VISIBLE);
        }
        holder.categoryTitle.setText(parentSnips.get(position).getEvent().getEvent_title());
        String viewChange = viewChangeValue;
        eventId = parentSnips.get(position).getEvent().getEvent_id();
        List<Snip> allParentSnip = parentSnips.get(position).getParentSnip();
        setCatItemRecycler(holder.itemRecycler, allParentSnip, viewChange);
    }

    @Override
    public int getItemCount() {
        return parentSnips.size();
    }

    public class MainViewHolder extends RecyclerView.ViewHolder {
        TextView categoryTitle;
        RecyclerView itemRecycler;

        public MainViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryTitle = itemView.findViewById(R.id.cat_title);
            itemRecycler  = itemView.findViewById(R.id.item_recycler);
        }
    }


    private void setCatItemRecycler(RecyclerView recyclerView, List<Snip> allEventSnips, String viewChange) {
        int spanCount = getSpanCount();
        ParentSnipRecyclerAdapter itemRecyclerAdapter = new ParentSnipRecyclerAdapter(context, allEventSnips, viewChange, tagsList);
        itemRecyclerAdapter.setHasStableIds(true);
//        recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        GridLayoutManager layoutManager = new GridLayoutManager(context, spanCount, RecyclerView.VERTICAL, false);
        Set<Integer> hasChildren = new HashSet<>();
        for(Snip snip: allEventSnips){
            List<Snip> tmp = AppClass.getAppInstance().getChildSnipsByParentSnipId(eventId, snip.getSnip_id());
            if(tmp.size() > 1){
                hasChildren.add(snip.getSnip_id());
            }
        }

        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if(allEventSnips.get(position).getHas_virtual_versions() == 1 ||
                    hasChildren.contains(allEventSnips.get(position).getSnip_id())){
                    return spanCount;
                }

                return 1;
            }
        });
        itemRecyclerAdapter.setFilterIds(allowedIds);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(itemRecyclerAdapter);
        itemRecyclerAdapter.notifyDataSetChanged();
    }

    private int getSpanCount() {
        int orientation = context.getResources().getConfiguration().orientation;
        if(orientation == Configuration.ORIENTATION_PORTRAIT) {
            if(viewChangeValue == null || viewChangeValue.equals(FragmentGalleryNew.ViewType.NORMAL.name()))
                return 4;
            else
                return 1;
        }else {
            if(viewChangeValue == null || viewChangeValue.equals(FragmentGalleryNew.ViewType.NORMAL.name()))
                return 7;
            else
                return 2;
        }
    }

    public void setFilterIds(List<Integer> filterIds){
        allowedIds.clear();
        if(filterIds != null)
            allowedIds.addAll(filterIds);
        notifyDataSetChanged();
    }

    public static String getDate(long time) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(time * 1000);
        String date = DateFormat.format("dd MMM yyyy", cal).toString();
        String[] dateString = date.split(" ");
//        String dayWithSuffix = dateString[0] + getDaySuffix(Integer.parseInt(dateString[0]));
        return dateString[0] + " ," + dateString[1] + " " + dateString[2];
    }
}
