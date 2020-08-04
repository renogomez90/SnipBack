package com.hipoint.snipback.fragment;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.R;
import com.hipoint.snipback.adapter.AdapterGallery;
import com.hipoint.snipback.adapter.CategoryItemRecyclerAdapter;
import com.hipoint.snipback.adapter.MainRecyclerAdapter;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.AllCategory;
import com.hipoint.snipback.room.entities.CategoryItem;
import com.hipoint.snipback.room.entities.Snip;

import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class FragmentGalleryNew extends Fragment  {
    private View rootView;
    RecyclerView mainCategoryRecycler;
    MainRecyclerAdapter mainRecyclerAdapter;

    List<Snip> snipArrayList= new ArrayList<>();

    public static FragmentGalleryNew newInstance() {
        FragmentGalleryNew fragment = new FragmentGalleryNew();
        return fragment;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_gallery_new, container, false);
        (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        mainCategoryRecycler=rootView.findViewById(R.id.main_recycler);

        List<Snip> allSnips = AppClass.getAppInsatnce().getAllSnip();

        for (final Snip snip : allSnips) {
            List<AllCategory> allCategoryList=new ArrayList<>();
            allCategoryList.add(new AllCategory(snip.getVid_creation_date()));
            allCategoryList.add(new AllCategory(snip.getVid_creation_date()));
            setMainCategoryRecycler(allCategoryList);
        }

        return rootView;
    }
    private void setMainCategoryRecycler(List<AllCategory> allCategoriesList){

        RecyclerView.LayoutManager layoutManager=new LinearLayoutManager(getActivity());
        mainCategoryRecycler.setLayoutManager(layoutManager);
        mainRecyclerAdapter=new MainRecyclerAdapter(getActivity(),allCategoriesList);
        mainCategoryRecycler.setAdapter(mainRecyclerAdapter);
    }


//    @Override
//    public void onItemClick(Snip snipvideopath) {
//
//    }
}







