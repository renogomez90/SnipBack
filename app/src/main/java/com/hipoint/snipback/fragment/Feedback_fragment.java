package com.hipoint.snipback.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
import com.hipoint.snipback.VideoMode;

public class Feedback_fragment extends Fragment {
    private View rootView;
    private ImageView back;

    public  static  Feedback_fragment newInstance() {
        Feedback_fragment fragment = new Feedback_fragment();
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.feedback_layout, container, false);
        back=rootView.findViewById(R.id.back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(VideoMode.newInstance());
            }
        });
        return rootView;
    }
}
