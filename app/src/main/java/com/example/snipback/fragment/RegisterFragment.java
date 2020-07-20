package com.example.snipback.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.snipback.AppMainActivity;
import com.example.snipback.R;
import com.example.snipback.VideoMode;

public class RegisterFragment extends Fragment {
    private View rootView;
    private Button button_register;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.register_layout, container, false);

        button_register=rootView.findViewById(R.id.button_register);
        button_register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(VideoMode.newInstance());
            }
        });
        return rootView;
    }
}
