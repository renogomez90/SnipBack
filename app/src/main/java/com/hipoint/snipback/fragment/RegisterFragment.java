package com.hipoint.snipback.fragment;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.RegisterFragment1;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;

public class RegisterFragment extends Fragment {
    private View rootView;
    private Button button_register;

    public  static  RegisterFragment newInstance() {
        RegisterFragment fragment = new RegisterFragment();
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.register_layout, container, false);

        (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        button_register=rootView.findViewById(R.id.button_register);
        button_register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ((AppMainActivity) getActivity()).loadFragment(RegisterFragment1.newInstance());
            }
        });
        return rootView;
    }
}
