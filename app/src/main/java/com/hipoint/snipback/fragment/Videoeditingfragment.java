package com.hipoint.snipback.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.R;

public class Videoeditingfragment extends Fragment {
    private View rootView;
    ImageView back, back1, save, close;
    RelativeLayout layout_extent, play_con;
    LinearLayout play_con1, play_con2;
    ImageButton extent;
    TextView extent_text, end, start;

    public static Videoeditingfragment newInstance() {
        Videoeditingfragment fragment = new Videoeditingfragment();
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.video_editing_fragment_main, container, false);

        layout_extent = rootView.findViewById(R.id.layout_extent);
        play_con = rootView.findViewById(R.id.play_con);
        play_con1 = rootView.findViewById(R.id.play_con1);
        play_con2 = rootView.findViewById(R.id.play_con2);
        extent = rootView.findViewById(R.id.extent);
        extent_text = rootView.findViewById(R.id.extent_text);
        save = rootView.findViewById(R.id.save);

        end = rootView.findViewById(R.id.end);
        start = rootView.findViewById(R.id.start);

        close = rootView.findViewById(R.id.close);

        end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start.setBackgroundResource(R.drawable.end_curve);
                end.setBackgroundResource(R.drawable.end_curve_red);
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start.setBackgroundResource(R.drawable.start_curve);
                end.setBackgroundResource(R.drawable.end_curve);
            }
        });


        extent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extent.setImageResource(R.drawable.ic_extent_red);
                extent_text.setTextColor(getResources().getColor(R.color.colorPrimaryDimRed));
                play_con1.setVisibility(View.VISIBLE);
                play_con2.setVisibility(View.GONE);
            }
        });
        back = rootView.findViewById(R.id.back);
        back1 = rootView.findViewById(R.id.back1);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogConformation();
            }
        });
        back1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogConformation();
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogSave();
            }
        });

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogdelete();
            }
        });


        return rootView;
    }

    protected void showDialogConformation() {

        final Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.warningdialog_savevideodiscardchanges);

        dialog.show();
    }

    protected void showDialogSave() {

        final Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.warningdialog_savevideo);

        dialog.show();
    }

    protected void showDialogdelete() {

        final Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.warningdialog_deletevideo);

        dialog.show();
    }
}
