package com.example.snipback.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snipback.AppMainActivity;
import com.example.snipback.R;

public class FragmentPlayVideo extends Fragment {
    private View rootView;
    LinearLayout bottom_menu;

    public  static  FragmentPlayVideo newInstance() {
        FragmentPlayVideo fragment = new FragmentPlayVideo();
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.layout_play_video, container, false);

        bottom_menu=rootView.findViewById(R.id.bottom_menu);
        bottom_menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(CreateTag.newInstance());
            }
        });

        return rootView;
    }


}