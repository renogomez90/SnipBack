package com.hipoint.snipback.fragment;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
import com.hipoint.snipback.room.entities.Snip;

import java.io.File;
import java.io.IOException;
import java.util.Objects;


public class CreateTag extends Fragment {
    private View rootView;
    ImageButton edit,mic;
    private static final String AUDIO_RECORDER_FILE_EXT_3GP = ".3gp";
    private static final String AUDIO_RECORDER_FILE_EXT_MP4 = ".mp4";
    private static final String AUDIO_RECORDER_FOLDER = "SnipRec";
    private MediaRecorder recorder = null;
    private int currentFormat = 0;
    private int output_formats[] = { MediaRecorder.OutputFormat.MPEG_4,MediaRecorder.OutputFormat.THREE_GPP };
    private String file_exts[] = { AUDIO_RECORDER_FILE_EXT_MP4, AUDIO_RECORDER_FILE_EXT_3GP };
    boolean isAudioPlaying=false;
    private Snip snip;


    public  static CreateTag newInstance(Snip snip) {
        CreateTag fragment = new CreateTag();
        Bundle bundle = new Bundle();
        bundle.putParcelable("snip", snip);
        fragment.setArguments(bundle);
        return fragment;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.create_tag_fragment, container, false);

        snip = Objects.requireNonNull(getArguments()).getParcelable("snip");

        // record audio
        mic=rootView.findViewById(R.id.mic);
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isAudioPlaying){
                    isAudioPlaying=false;
                    stopRecording();
                    Toast.makeText(getActivity(), "stop", Toast.LENGTH_LONG).show();
//                    Intent intent=new Intent(getContext(),ActivityPlayVideo.class);
//                    startActivity(intent);
//                    getActivity().onBackPressed();


                }else {
                    isAudioPlaying=true;
                    Toast.makeText(getActivity(), "start", Toast.LENGTH_LONG).show();
                    startRecording();
                }

            }
        });

        edit=rootView.findViewById(R.id.edit);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((AppMainActivity) getActivity()).loadFragment(Videoeditingfragment.newInstance(),true);
            }
        });

        return rootView;
    }

    private void startRecording(){
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(output_formats[currentFormat]);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile(getFilename());
        recorder.setOnErrorListener(errorListener);
        recorder.setOnInfoListener(infoListener);

        try {
            recorder.prepare();
            recorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdirs();
        }

//        return (file.getAbsolutePath() + "/" +snip.getSnip_id()+ file_exts[currentFormat]);\
        return (file.getAbsolutePath() + "/" +snip.getSnip_id()+".mp3");
    }

    private MediaRecorder.OnErrorListener errorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
//            AppLog.logString("Error: " + what + ", " + extra);
        }
    };

    private MediaRecorder.OnInfoListener infoListener = new MediaRecorder.OnInfoListener() {
        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
//            AppLog.logString("Warning: " + what + ", " + extra);
        }
    };

    private void stopRecording(){
        if(null != recorder){
            recorder.stop();
            recorder.reset();
            recorder.release();

            recorder = null;
        }
    }

}
