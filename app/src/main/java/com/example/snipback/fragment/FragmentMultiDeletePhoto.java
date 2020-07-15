package com.example.snipback.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snipback.R;
import com.example.snipback.adapter.AdapterPhotos;

public class FragmentMultiDeletePhoto extends Fragment {
    private View rootView;
   RecyclerView recycler_view;
    public  static  FragmentMultiDeletePhoto newInstance() {
        FragmentMultiDeletePhoto fragment = new FragmentMultiDeletePhoto();
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_multideletephoto, container, false);
        recycler_view= rootView.findViewById(R.id.recycler_view);
        recycler_view.setLayoutManager(new LinearLayoutManager(getActivity()));
        AdapterPhotos adapterPhotos = new AdapterPhotos(getActivity());
        recycler_view.setAdapter(adapterPhotos);
        return rootView;
    }


}
