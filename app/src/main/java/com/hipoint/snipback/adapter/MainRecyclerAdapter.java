package com.hipoint.snipback.adapter;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hipoint.snipback.R;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.AllCategory;
import com.hipoint.snipback.room.entities.CategoryItem;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.EventData;
import com.hipoint.snipback.room.entities.Snip;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainRecyclerAdapter extends RecyclerView.Adapter<MainRecyclerAdapter.MainViewHolder> {

    private Context context;
    private List<EventData> parentSnips;
    private List<EventData> allSnips;
    private  String viewChangeValue;
    private int eventId = -1;

    public MainRecyclerAdapter(Context context, List<EventData> allParentSnip, List<EventData> allEventSnip,String viewChange) {
        this.context = context;
        this.allSnips = allEventSnip;
        this.parentSnips = allParentSnip;
        this.viewChangeValue = viewChange;

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
        String viewChange =viewChangeValue;
        eventId = parentSnips.get(position).getEvent().getEvent_id();
        List<Snip> allParentSnip = parentSnips.get(position).getParentSnip();
        setCatItemRecycler(holder.itemRecycler,allParentSnip,viewChange);

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
            itemRecycler=itemView.findViewById(R.id.item_recycler);
        }
    }


    private void setCatItemRecycler(RecyclerView recyclerView, List<Snip> allEventSnips, String viewChange){
        ParentSnipRecyclerAdapter itemRecyclerAdapter = new ParentSnipRecyclerAdapter(context,allEventSnips,viewChange);
        itemRecyclerAdapter.notifyDataSetChanged();
        recyclerView.setLayoutManager(new LinearLayoutManager(context,RecyclerView.VERTICAL,false));
        recyclerView.setAdapter(itemRecyclerAdapter);
    }

    public static String getDate(long time) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(time * 1000);
        String date = DateFormat.format("dd MMM yyyy", cal).toString();
        String[] dateString = date.split(" ");
//        String dayWithSuffix = dateString[0] + getDaySuffix(Integer.parseInt(dateString[0]));
        return dateString[0] + " ," + dateString[1] + " " + dateString[2] ;
    }
}
