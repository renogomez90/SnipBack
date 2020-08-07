package com.hipoint.snipback.fragment;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
import com.hipoint.snipback.VideoMode;



public class TrialOver extends Fragment {
    private TextView tv_1, tv_2, tv_4, tv_6,tv_1_1;
    private Context mContext;
    private ImageView close;
    RelativeLayout rL1,rL2;
    public  static TrialOver newInstance() {
        TrialOver fragment = new TrialOver();
        return fragment;
    }
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.trial_over_fragment, null);
        (getActivity()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        tv_1 = view.findViewById(R.id.tv_1);
        tv_2 = view.findViewById(R.id.tv_2);
        tv_4 = view.findViewById(R.id.tv_4);
        tv_6 = view.findViewById(R.id.tv_6);
        tv_1_1=view.findViewById(R.id.tv_1_1);

        close=view.findViewById(R.id.close);
        rL1=view.findViewById(R.id.rlAmtChange1);
        rL2=view.findViewById(R.id.rlAmtChange2);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);


        String text1 = " <font color='#FFFFFF'>YOUR </font>" +"<font color='#EA3C2A'>FREE</font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tv_1.setText(Html.fromHtml(text1, Html.FROM_HTML_MODE_LEGACY), TextView.BufferType.SPANNABLE);
        } else {
            tv_1.setText(Html.fromHtml(text1), TextView.BufferType.SPANNABLE);
        }

        String text1_1 = "<font color='#EA3C2A'>TRIAL </font>" + "<font color='#FFFFFF'>IS OVER</font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tv_1_1.setText(Html.fromHtml(text1_1, Html.FROM_HTML_MODE_LEGACY), TextView.BufferType.SPANNABLE);
        } else {
            tv_1_1.setText(Html.fromHtml(text1_1), TextView.BufferType.SPANNABLE);
        }
        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });
        rL1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(VideoMode.newInstance(),true);
            }
        });
        rL2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(VideoMode.newInstance(),true);
            }
        });

    }
}
