package com.hipoint.snipback.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.hipoint.snipback.R;
import com.hipoint.snipback.room.entities.Snip;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class AdapterGallery extends RecyclerView.Adapter<AdapterGallery.ViewHolder> {

    private Context mContext;
    List<Snip> snipArrayList = new ArrayList<>();
    private ItemListener mListener;

    public AdapterGallery(Context context, List<Snip> allSnips, ItemListener itemListener) {
        mContext = context;
        this.snipArrayList = allSnips;
        mListener = itemListener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        ImageView image1;
//        RecyclerView recyclerView;

        public ViewHolder(final View v) {
            super(v);
            image1 = v.findViewById(R.id.image);
//            recyclerView = v.findViewById(R.id.rv_horizontal);
        }

    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(mContext).inflate(R.layout.layout_adater_gallery, parent, false);
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Snip snip = snipArrayList.get(position);
        try {
            Bitmap myBitmap = BitmapFactory.decodeFile(snip.getThumbnailPath());

            CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(mContext);
            circularProgressDrawable.setStrokeWidth(5F);
            circularProgressDrawable.setCenterRadius(30F);
            circularProgressDrawable.start();

            Glide.with(mContext).load(myBitmap).placeholder(circularProgressDrawable).into(holder.image1);

//            holder.image1.setImageBitmap(myBitmap);
            holder.image1.setOnClickListener(v -> mListener.onItemClick(snipArrayList.get(position)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return snipArrayList.size();
    }

    public interface ItemListener {
        void onItemClick(Snip snipvideopath);
    }

}