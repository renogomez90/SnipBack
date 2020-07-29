package com.hipoint.snipback.fragment;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.R;

public class IntroFragment1 extends Fragment {
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    public  static  IntroFragment1 newInstance() {
        IntroFragment1 fragment = new IntroFragment1();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_intro1, null);

        (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        return view;
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        //layoutManager = new LinearLayoutManager(getActivity());
//        pulltoRefresh();
    }
}
