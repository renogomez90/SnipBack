package com.hipoint.snipback.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;

public class StartTrial extends Fragment {
    private Context mContext;
    private Button bt1;
    private ImageView close;
    private TextView tv_1, tv_2;

    public static StartTrial newInstance() {
        StartTrial fragment = new StartTrial();
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
        View view = inflater.inflate(R.layout.trial_start, null);
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        bt1 = view.findViewById(R.id.bt1);
        close = view.findViewById(R.id.close);
        tv_1 = view.findViewById(R.id.tv_1);
        tv_2 = view.findViewById(R.id.tv_2);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);


        String text1 = "<font color='#EA3C2A'>FREE</font>" + " " + "<font color='#FFFFFF'>TOTAL </font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tv_1.setText(Html.fromHtml(text1, Html.FROM_HTML_MODE_LEGACY), TextView.BufferType.SPANNABLE);
        } else {
            tv_1.setText(Html.fromHtml(text1), TextView.BufferType.SPANNABLE);
        }

        String text2 = " <font color='#FFFFFF'>ACCESS </font>" + "<font color='#EA3C2A'> FOR 14 DAYS</font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tv_2.setText(Html.fromHtml(text2, Html.FROM_HTML_MODE_LEGACY), TextView.BufferType.SPANNABLE);
        } else {
            tv_2.setText(Html.fromHtml(text2), TextView.BufferType.SPANNABLE);
        }

        bt1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialogEligible();
            }
        });

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requireActivity().onBackPressed();
            }
        });
    }

    protected void showDialogEligible() {

        final Dialog dialog = new Dialog(requireActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.enjoy_freetrial_layout);

        TextView dialog_subtitle = dialog.findViewById(R.id.dialog_subtitle);
        TextView dialog_cancel = dialog.findViewById(R.id.dialog_cancel);
        TextView dialog_ok = dialog.findViewById(R.id.dialog_ok);
        String text2 = " <font color='#EA3C2A'>30 DAYS FREE </font>" + "<font color='#000'> FOR REGISTERING</font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dialog_subtitle.setText(Html.fromHtml(text2, Html.FROM_HTML_MODE_LEGACY), TextView.BufferType.SPANNABLE);
        } else {
            dialog_subtitle.setText(Html.fromHtml(text2), TextView.BufferType.SPANNABLE);
        }
        dialog_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog_ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) requireActivity()).loadFragment(TrialOver.newInstance(), true);
                dialog.dismiss();
            }
        });
        dialog.show();
    }
}
