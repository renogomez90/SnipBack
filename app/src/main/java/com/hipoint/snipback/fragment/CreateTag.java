package com.hipoint.snipback.fragment;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hipoint.snipback.ActivityPlayVideo;
import com.hipoint.snipback.AppMainActivity;
import com.hipoint.snipback.R;
import com.hipoint.snipback.Utils.CommonUtils;
import com.hipoint.snipback.room.entities.Snip;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


public class CreateTag extends Fragment {
    private View rootView;
    ImageButton edit,mic,tick;
    private static final String AUDIO_RECORDER_FILE_EXT_3GP = ".3gp";
    private static final String AUDIO_RECORDER_FILE_EXT_MP4 = ".mp4";
    private static final String AUDIO_RECORDER_FOLDER = "SnipRec";
    private MediaRecorder recorder = null;
    private int currentFormat = 0;
    private int output_formats[] = { MediaRecorder.OutputFormat.MPEG_4,MediaRecorder.OutputFormat.THREE_GPP };
    private String file_exts[] = { AUDIO_RECORDER_FILE_EXT_MP4, AUDIO_RECORDER_FILE_EXT_3GP };
    boolean isAudioPlaying=false;
    private Snip snip;
    private Chronometer mChronometer;
    private int timerSecond = 0;
    private static final String TAG = "CreateTag";
    private Switch img3,img4;
    private TextView after,before;
    int posToChoose;


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

        after=rootView.findViewById(R.id.after);
        before=rootView.findViewById(R.id.before);
        img3=rootView.findViewById(R.id.img3);
        img3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled

                    after.setTextColor(getResources().getColor(R.color.red_tag));
                    img4.setChecked(false);
                    before.setTextColor(getResources().getColor(R.color.colorPrimaryWhite));
                    posToChoose=1;
                    CommonUtils.setPreferencesInt(getActivity(),"poaition", posToChoose);


                }

            }
        });
        img4=rootView.findViewById(R.id.img4);
        img4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {

                    before.setTextColor(getResources().getColor(R.color.red_tag));
                    img3.setChecked(false);
                    after.setTextColor(getResources().getColor(R.color.colorPrimaryWhite));
                    posToChoose=2;
                    CommonUtils.setPreferencesInt(getActivity(),"poaition", posToChoose);

                }

            }
        });
        mChronometer = rootView.findViewById(R.id.chronometer);
        mChronometer.setOnChronometerTickListener(arg0 -> {
//                if (!resume) {
            long time = SystemClock.elapsedRealtime() - mChronometer.getBase();
            int h = (int) (time / 3600000);
            int m = (int) (time - h * 3600000) / 60000;
            int s = (int) (time - h * 3600000 - m * 60000) / 1000;
            String t = (h < 10 ? "0" + h : h) + ":" + (m < 10 ? "0" + m : m) + ":" + (s < 10 ? "0" + s : s);
            mChronometer.setText(t);

            long minutes = ((SystemClock.elapsedRealtime() - mChronometer.getBase()) / 1000) / 60;
            long seconds = ((SystemClock.elapsedRealtime() - mChronometer.getBase()) / 1000) % 60;
            int elapsedMillis = (int) (SystemClock.elapsedRealtime() - mChronometer.getBase());
            timerSecond = (int) TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);
//                    elapsedTime = SystemClock.elapsedRealtime();
            Log.d(TAG, "onChronometerTick: " + minutes + " : " + seconds);
//                } else {
//                    long minutes = ((elapsedTime - cmTimer.getBase())/1000) / 60;
//                    long seconds = ((elapsedTime - cmTimer.getBase())/1000) % 60;
//                    elapsedTime = elapsedTime + 1000;
//                    Log.d(TAG, "onChronometerTick: " + minutes + " : " + seconds);
//                }
        });
        tick=rootView.findViewById(R.id.tick);
        tick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ActivityPlayVideo.class);
                intent.putExtra("snip", snip);
                startActivity(intent);
                getActivity().finish();
            }
        });


        // record audio
        mic=rootView.findViewById(R.id.mic);
        mic.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                {
                    startRecording();
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP)
                {
                    stopRecording();
                    img3.setChecked(true);

                    return true;
                }

                return true;
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

        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.start();
        mChronometer.setVisibility(View.VISIBLE);
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
        mChronometer.stop();
        mChronometer.setVisibility(View.INVISIBLE);
        mChronometer.setText("");

    }

}
