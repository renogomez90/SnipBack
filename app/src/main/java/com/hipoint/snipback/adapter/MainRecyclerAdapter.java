package com.hipoint.snipback.adapter;

import android.content.Context;
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
import com.hipoint.snipback.room.entities.EventData;
import com.hipoint.snipback.room.entities.Snip;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainRecyclerAdapter extends RecyclerView.Adapter<MainRecyclerAdapter.MainViewHolder> {

    private Context context;
    private List<EventData> parentSnips;
    private List<EventData> allSnips;
    private String viewChangeValue;
    private Integer orientationValue;
    private int eventId = -1;

    public MainRecyclerAdapter(Context context, List<EventData> allParentSnip, List<EventData> allEventSnip, String viewChange, Integer orientation) {
        this.context = context;
        this.allSnips = allEventSnip;
        this.parentSnips = allParentSnip;
        this.viewChangeValue = viewChange;
        this.orientationValue = orientation;

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
        holder.categoryTitle.setVisibility(View.VISIBLE);
        holder.categoryTitle.setText(parentSnips.get(position).getEvent().getEvent_title());
        String viewChange = viewChangeValue;
        Integer orientation = orientationValue;

        eventId = parentSnips.get(position).getEvent().getEvent_id();
        List<Snip> allParentSnip = parentSnips.get(position).getParentSnip();
        setCatItemRecycler(holder.itemRecycler, allParentSnip, viewChange, orientation);

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
            itemRecycler = itemView.findViewById(R.id.item_recycler);
        }
    }


    private void setCatItemRecycler(RecyclerView recyclerView, List<Snip> allEventSnips, String viewChange, Integer orientation) {
        ParentSnipRecyclerAdapter itemRecyclerAdapter = new ParentSnipRecyclerAdapter(context, allEventSnips, viewChange, orientation);
        itemRecyclerAdapter.notifyDataSetChanged();
//        recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        GridLayoutManager layoutManager = new GridLayoutManager(context, 4, RecyclerView.VERTICAL, false);

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
                    return 4;
                }

                return 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(itemRecyclerAdapter);
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
