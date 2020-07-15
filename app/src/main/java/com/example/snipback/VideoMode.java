package com.example.snipback;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.snipback.fragment.FragmentGallery;

public class VideoMode extends Fragment {
    private View rootView;
    ImageButton gallery, settings;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_videomode, container, false);
        gallery = rootView.findViewById(R.id.r_1);
        settings = rootView.findViewById(R.id.r_5);
        gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(FragmentGallery.newInstance());
            }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogSettingsMain();
            }
        });
        return rootView;
    }

    protected void showDialogSettingsMain() {

        Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.fragment_settings);

        RelativeLayout con6 = dialog.findViewById(R.id.con6);
        con6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogSettingsResolution();
            }
        });

        dialog.show();
    }

    ;

    protected void showDialogSettingsResolution() {

        Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.fragment_resolution);

        dialog.show();
    }

    ;
}