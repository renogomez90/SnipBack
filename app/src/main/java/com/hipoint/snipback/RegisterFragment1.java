package com.hipoint.snipback;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.R;
import com.hipoint.snipback.fragment.IntroFragmentViewPager;

public class RegisterFragment1 extends Fragment {
    Intent intent;
    Button button_register;
    private View rootView;


    public static RegisterFragment1 newInstance() {
        return new RegisterFragment1();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.activty_register, container, false);

        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        button_register = rootView.findViewById(R.id.button_register);
        button_register.setOnClickListener(v -> ((AppMainActivity) requireActivity()).loadFragment(IntroFragmentViewPager.newInstance(), true));

        return rootView;
    }
}


