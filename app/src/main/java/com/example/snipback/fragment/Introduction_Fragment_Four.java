package com.example.snipback.fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.snipback.AppMainActivity;
import com.example.snipback.R;
import com.example.snipback.RegisterFragment1;
import com.example.snipback.VideoMode;

public class Introduction_Fragment_Four extends Fragment {
    LinearLayout dontshow;
    Button watch_later;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    public static Introduction_Fragment_Four newInstance() {
        Introduction_Fragment_Four fragment = new Introduction_Fragment_Four();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_intro4, null);

        dontshow=view.findViewById(R.id.dontshow);
        dontshow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogdontShow();
            }
        });

        watch_later=view.findViewById(R.id.watch_later);
        watch_later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(StartTrial.newInstance());
            }
        });
        return view;
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    protected void showDialogdontShow() {

        final Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dont_show_layout);

        dialog.show();
    }
}
