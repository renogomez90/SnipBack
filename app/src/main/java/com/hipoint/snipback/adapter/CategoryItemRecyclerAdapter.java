package com.hipoint.snipback.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
import com.hipoint.snipback.Utils.CommonUtils;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.fragment.FragmentGallery;
import com.hipoint.snipback.fragment.FragmentGalleryNew;
import com.hipoint.snipback.fragment.FragmentPlayVideo;
import com.hipoint.snipback.fragment.Videoeditingfragment;
import com.hipoint.snipback.room.entities.Snip;

import java.io.File;
import java.util.List;

import static com.hipoint.snipback.CircularSeekBar.relativeLayout;

public class CategoryItemRecyclerAdapter extends RecyclerView.Adapter<CategoryItemRecyclerAdapter.CategoryItemViewHolder> {
    private Context context;
    private ItemListener mListener;
    List<Snip> snipArrayList;
    private  String viewChangeValue;

    public CategoryItemRecyclerAdapter(Context context, List<Snip> allSnips,String viewChange) {
        this.context = context;
        this.snipArrayList = allSnips;
        this.viewChangeValue= viewChange;
    }



    @NonNull
    @Override
    public CategoryItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CategoryItemViewHolder(LayoutInflater.from(context).inflate(R.layout.category_row_items,parent,false));
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryItemViewHolder holder, int position) {
        if (snipArrayList != null){
//            Log.d("action3",viewChangeValue+"");
            Snip snip = snipArrayList.get(position);
            try {
                int duration;
                if(snip.getParent_snip_id() != 0 && snip.getIs_virtual_version() == 1){
                    holder.tvVersionLabel.setVisibility(View.VISIBLE);
                    holder.tvVersionLabel.setText("VERSION "+position);
                    duration =  (int) snipArrayList.get(position).getSnip_duration();
                }else if(snip.getParent_snip_id() != 0 && snip.getIs_virtual_version() == 0){
                    holder.tvVersionLabel.setVisibility(View.VISIBLE);
                    holder.tvVersionLabel.setText("V.VERSION "+position);
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

                String filePath = AppClass.getAppInsatnce().getThumbFilePathRoot()+snip.getSnip_id()+".png";
                File file = new File(filePath);
                if(file.exists()) {
                    Bitmap myBitmap = BitmapFactory.decodeFile(filePath);
                    holder.itemImage.setImageBitmap(myBitmap);

                    //enlarged view
                    if (viewChangeValue.equals("VISIBLE")){
                        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 720);
                        relativeParams.setMargins(10, 10, 10, 10);
                        holder.relativeLayoutImage.setLayoutParams(relativeParams);
                        holder.itemImage.setLayoutParams((new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 720)));
                    }

                }else{
                    CommonUtils.getVideoThumbnail(context,snip);
                }
                holder.itemImage.setOnClickListener(v -> {
                    Intent intent = new Intent(context, ActivityPlayVideo.class);
                    intent.putExtra("snip", snip);
                    context.startActivity(intent);

//                    ((AppMainActivity) context).loadFragment(FragmentPlayVideo.newInstance(snip),true);

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
        RelativeLayout relativeLayoutImage;
        public CategoryItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemImage = itemView.findViewById(R.id.image);
            tvVersionLabel = itemView.findViewById(R.id.tvVersionLabel);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            relativeLayoutImage= itemView.findViewById(R.id.rel_layout_image);
        }
    }

    public interface ItemListener {
        void onItemClick(Snip snipvideopath);
    }

}
