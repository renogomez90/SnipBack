package com.hipoint.snipback.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
import com.hipoint.snipback.Utils.CommonUtils;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.fragment.FragmentPlayVideo;
import com.hipoint.snipback.fragment.FragmentPlayVideo2;
import com.hipoint.snipback.room.entities.Snip;

import java.io.File;
import java.util.List;

public class CategoryItemRecyclerAdapter extends RecyclerView.Adapter<CategoryItemRecyclerAdapter.CategoryItemViewHolder> {
    private Context context;
    private ItemListener mListener;
    List<Snip> snipArrayList;
    private String viewChangeValue;
    private Integer orientationVal;

    public CategoryItemRecyclerAdapter(Context context, List<Snip> allSnips, String viewChange, Integer orientation) {
        this.context = context;
        this.snipArrayList = allSnips;
        this.viewChangeValue = viewChange;
        this.orientationVal = orientation;
    }

    @NonNull
    @Override
    public CategoryItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CategoryItemViewHolder(LayoutInflater.from(context).inflate(R.layout.category_row_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryItemViewHolder holder, int position) {
        if (snipArrayList != null) {
            Snip snip = snipArrayList.get(position);
            try {
                int duration;
                ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(100,100);
                if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
                    int totalWidth = context.getResources().getDisplayMetrics().widthPixels;
                    params.setMargins(5, 0, 5, 0);
                    params.width = (totalWidth - 4)/4;
                    params.height = (totalWidth - 4)/4;
                    holder.relativeLayoutImage.setLayoutParams(params);
                }else {
                    int totalWidth = context.getResources().getDisplayMetrics().widthPixels;
                    params.setMargins(5, 0, 5, 0);
                    params.width = (totalWidth - 4)/8;
                    params.height = (totalWidth - 4)/8;
                    holder.relativeLayoutImage.setLayoutParams(params);
                }

                if (snip.getParent_snip_id() != 0 && snip.getIs_virtual_version() == 1) {
                    holder.tvVersionLabel.setVisibility(View.VISIBLE);
                    holder.tvVersionLabel.setText("V.VERSION " + position);
                    duration = (int) snipArrayList.get(position).getSnip_duration();
                } else if (snip.getParent_snip_id() != 0 && snip.getIs_virtual_version() == 0) {
                    holder.tvVersionLabel.setVisibility(View.VISIBLE);
                    holder.tvVersionLabel.setText("VERSION " + position);
                    duration = (int) snipArrayList.get(position).getSnip_duration();
                } else {
                    holder.tvVersionLabel.setVisibility(View.INVISIBLE);
                    duration = (int) snipArrayList.get(position).getTotal_video_duration();
                }
                int hours = duration / 3600;
                int minutes = (duration % 3600) / 60;
                int seconds = duration % 60;

                if (hours > 0) {
                    holder.tvDuration.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                } else {
                    holder.tvDuration.setText(String.format("%02d:%02d", minutes, seconds));
                }

                String filePath = AppClass.getAppInstance().getThumbFilePathRoot() + snip.getSnip_id() + ".png";
                File file = new File(filePath);
                if (file.exists()) {
                    Bitmap myBitmap = BitmapFactory.decodeFile(filePath);
//                    holder.itemImage.setImageBitmap(myBitmap);
                    CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(context);
                    circularProgressDrawable.setStrokeWidth(5F);
                    circularProgressDrawable.setCenterRadius(30F);
                    circularProgressDrawable.start();

                    Glide.with(context).load(myBitmap).placeholder(circularProgressDrawable).into(holder.itemImage);

                    if (viewChangeValue != null && orientationVal == null) {
                        enlargedPortraitView(holder);

                    } else if (viewChangeValue != null && orientationVal == 2) {
                        enlargedLandscapeMode(holder);
                    } else {
                        enlargedPortraitView(holder);
                    }

                } else {
                    CommonUtils.getVideoThumbnail(context, snip);
                }
                holder.itemImage.setOnClickListener(v -> {
                    /*Intent intent = new Intent(context, ActivityPlayExoVideo.class);
                    intent.putExtra("snip", snip);
                    context.startActivity(intent);*/

                    ((AppMainActivity) context).loadFragment(FragmentPlayVideo2.newInstance(snip), true);

                });
//                holder.itemImage.setOnClickListener(v -> mListener.onItemClick(snipArrayList.get(position)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

//        holder.itemImage.setImageResource(categoryItemList.get(position).getImageUrl());
    }

    private void enlargedPortraitView(CategoryItemViewHolder holder) {
        if (viewChangeValue != null) {
            if (viewChangeValue.equals("ENLARGED")) {
                RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 750);
                relativeParams.setMargins(15, 15, 15, 15);
                holder.relativeLayoutImage.setLayoutParams(relativeParams);
                holder.itemImage.setLayoutParams((new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 750)));
            }
        }
    }

    private void enlargedLandscapeMode(CategoryItemViewHolder holder) {
        if (viewChangeValue != null) {
            if (viewChangeValue.equals("ENLARGED")) {
                RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(950, 550);
                relativeParams.setMargins(15, 15, 15, 40);
                relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                relativeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
                holder.relativeLayoutImage.setLayoutParams(relativeParams);
                holder.itemImage.setLayoutParams((new RelativeLayout.LayoutParams(950, 550)));
            }
        }
    }

    @Override
    public int getItemCount() {
        return snipArrayList.size();
    }

    public class CategoryItemViewHolder extends RecyclerView.ViewHolder {
        ImageView itemImage;
        TextView tvVersionLabel;
        TextView tvDuration;
        ConstraintLayout relativeLayoutImage;

        public CategoryItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemImage = itemView.findViewById(R.id.image);
            tvVersionLabel = itemView.findViewById(R.id.tvVersionLabel);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            relativeLayoutImage = itemView.findViewById(R.id.rel_layout_image);
        }
    }

    public interface ItemListener {
        void onItemClick(Snip snipvideopath);
    }

}
