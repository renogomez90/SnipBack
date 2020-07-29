package com.hipoint.snipback.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.R;

public class AdapterGallery extends RecyclerView.Adapter<AdapterGallery.ViewHolder> {

    private Context mContext;

    public AdapterGallery(Context context) {
        mContext = context;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        ImageButton image1;


        private LinearLayout llWrapper;

        public ViewHolder(final View v) {
            super(v);
            image1 = v.findViewById(R.id.image1);


        }


    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(mContext).inflate(R.layout.layout_adater_gallery, parent, false);
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return 5;
    }


}