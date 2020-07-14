package com.example.snipback.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snipback.R;
import com.example.snipback.adapter.AdapterPhotos;

public class FragmentGallery extends Fragment  implements View.OnClickListener {
    private View rootView;
    ImageButton gallery;
    RecyclerView recycler_view;
    RelativeLayout relativeLayout_menu,relativeLayout_autodeleteactions;
    public  static  FragmentGallery newInstance() {
        FragmentGallery fragment = new FragmentGallery();
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_gallery, container, false);
        recycler_view= rootView.findViewById(R.id.recycler_view);
        relativeLayout_menu= rootView.findViewById(R.id.layout_menu);
        recycler_view.setLayoutManager(new LinearLayoutManager(getActivity()));
        AdapterPhotos adapterPhotos = new AdapterPhotos(getActivity());
        recycler_view.setAdapter(adapterPhotos);
        relativeLayout_menu.setOnClickListener(this);
        return rootView;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.layout_menu:
                Dialog dialog = new Dialog(getActivity());
                dialog.setContentView(R.layout.menu_layout);
                dialog.show();
                break;
        }
    }
}
