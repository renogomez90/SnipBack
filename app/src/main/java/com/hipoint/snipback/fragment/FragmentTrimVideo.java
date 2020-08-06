package com.hipoint.snipback.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.R;
import com.hipoint.videotrim.ActVideoTrimmer;
import com.hipoint.videotrim.LogMessage;
import com.hipoint.videotrim.TrimmerConstants;

import static android.app.Activity.RESULT_OK;

public class FragmentTrimVideo extends  Fragment implements View.OnClickListener {
    public  static  FragmentTrimVideo newInstance() {
        FragmentTrimVideo fragment = new FragmentTrimVideo();
    return fragment;
}    private static final int REQUEST_TAKE_VIDEO = 552;

    private VideoView videoView;

    private MediaController mediaController;

    private EditText edtMinFrom, edtMAxTo;
    Button button;

    private int trimType;

    private static final String TAG = "FragmentTrimVideo";

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_timvideoactivity, null);
            videoView = view.findViewById(R.id.video_view);
            edtMinFrom =view.findViewById(R.id.edt_min_from);
            edtMAxTo =view.findViewById(R.id.edt_max_to);
            mediaController = new MediaController(getActivity());
            mediaController.setAnchorView(videoView);
            button=view.findViewById(R.id.btn_min_max_gap);
            button.setOnClickListener(this);

            return view;
        }
        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_min_max_gap:
                onMinToMaxTrimClicked();
                break;
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == TrimmerConstants.REQ_CODE_VIDEO_TRIMMER && data != null) {
                Uri uri = Uri.parse(data.getStringExtra(TrimmerConstants.TRIMMED_VIDEO_PATH));
                Log.d(TAG,"Trimmed path:: "+uri);
                videoView.setMediaController(mediaController);
                videoView.setVideoURI(uri);
                videoView.requestFocus();
                videoView.start();
            }else if (requestCode == REQUEST_TAKE_VIDEO && resultCode == RESULT_OK) {
            /*    //check video duration if needed
                if (TrimmerUtils.getVideoDuration(this,data.getData())<=30){
                    Toast.makeText(this,"Video should be larger than 30 sec",Toast.LENGTH_SHORT).show();
                    return;
                }*/
                if (data.getData()!=null){
                    LogMessage.v("Video path:: "+data.getData());
                    openTrimActivity(String.valueOf(data.getData()));
                }else{
                    Toast.makeText(getActivity(),"video uri is null",Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void onMinToMaxTrimClicked() {
        trimType=3;
        if (isEdtTxtEmpty(edtMinFrom))
            Toast.makeText(getActivity(),"Enter min gap duration",Toast.LENGTH_SHORT).show();
        else if (isEdtTxtEmpty(edtMAxTo))
            Toast.makeText(getActivity(),"Enter max gap duration",Toast.LENGTH_SHORT).show();
        else {
            showVideoOptions();
        }
    }

    public  void showVideoOptions() {
        openVideo();
    }


    public void openVideo() {
        try {
            Intent intent = new Intent();
            intent.setType("video/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Video"), REQUEST_TAKE_VIDEO);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isEdtTxtEmpty(EditText editText){
        return editText.getText().toString().trim().isEmpty();
    }

    private long getEdtValueLong(EditText editText){
        return Long.parseLong(editText.getText().toString().trim());
    }

    private void openTrimActivity(String data) {

        Intent intent=new Intent(getActivity(), ActVideoTrimmer.class);
        intent.putExtra(TrimmerConstants.TRIM_VIDEO_URI,data);
        intent.putExtra(TrimmerConstants.TRIM_TYPE,3);
        intent.putExtra(TrimmerConstants.MIN_FROM_DURATION,getEdtValueLong(edtMinFrom));
        intent.putExtra(TrimmerConstants.MAX_TO_DURATION,getEdtValueLong(edtMAxTo));
        startActivityForResult(intent, TrimmerConstants.REQ_CODE_VIDEO_TRIMMER);

    }
}
