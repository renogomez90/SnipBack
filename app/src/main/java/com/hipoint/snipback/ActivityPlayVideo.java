package com.hipoint.snipback;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.lifecycle.ViewModelProviders;

import com.hipoint.snipback.Utils.CommonUtils;
import com.hipoint.snipback.Utils.TrimmerUtils;
import com.hipoint.snipback.application.AppClass;
import com.hipoint.snipback.room.entities.Event;
import com.hipoint.snipback.room.entities.Hd_snips;
import com.hipoint.snipback.room.entities.Snip;
import com.hipoint.snipback.room.repository.AppRepository;
import com.hipoint.snipback.room.repository.AppViewModel;
import com.kaopiz.kprogresshud.KProgressHUD;

import java.io.File;

import Jni.FFmpegCmd;
import VideoHandle.OnEditorListener;

public class ActivityPlayVideo extends Swipper {
    private String VIDEO_DIRECTORY_NAME = "Snipback";
    VideoView videoView;
    private Snip snip;
    private SeekBar seek;
    double current_pos, total_duration;
    private TextView exo_duration;
    private RelativeLayout rlPlayPause;
    private Switch play_pause;
    boolean paused = false;
    private RelativeLayout play_forwardbutton,back_arrow,button_camera;
    private ImageButton tvConvertToReal;
    private Event event;
    private AppRepository appRepository;
    private AppViewModel appViewModel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        seek = findViewById(R.id.seek);
        exo_duration = findViewById(R.id.exo_duration);
        play_pause = findViewById(R.id.play_pause);
        rlPlayPause = findViewById(R.id.play_pouse);
        videoView = (VideoView) findViewById(R.id.videoView);

        tvConvertToReal = findViewById(R.id.tvConvertToReal);
        appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
        back_arrow=findViewById(R.id.back_arrow);
        button_camera=findViewById(R.id.button_camera);

        Intent intent = getIntent();
        snip = intent.getParcelableExtra("snip");
        appRepository = new AppRepository(getApplicationContext());
        appViewModel.getEventByIdLiveData(snip.getEvent_id()).observe(this, snipevent -> {
                    event = snipevent;
            });
        Uri video1 = Uri.parse(snip.getVideoFilePath());
        videoView.setVideoURI(video1);
        videoView.requestFocus();
        play_pause.setChecked(true);

        back_arrow.setOnClickListener(v -> {
            onBackPressed();
        });

        button_camera.setOnClickListener(v -> {
//            (AppMainActivity).loadFragment(VideoMode.newInstance(),true);
            Intent intent1 = new Intent(this, AppMainActivity.class);
            startActivity(intent1);
            finishAffinity();
        });

        // play forward and backward
        play_forwardbutton = findViewById(R.id.play_forwardbutton);
        play_forwardbutton.setOnClickListener(v -> {
//            videoView.stopPlayback();
//            videoView.resume();
//            List<Snip> allSnips = AppClass.getAppInsatnce().getAllSnip();

//                Uri video = Uri.parse(allSnips.get(1)+"");
//                videoView.setVideoURI(video);

        });
        seek.setVisibility(View.VISIBLE);

        tvConvertToReal.setOnClickListener(view -> validateVideo(snip));

        videoView.setOnPreparedListener(mp -> setVideoProgress());

        videoView.setOnCompletionListener(mp -> {
            videoView.stopPlayback();
            videoView.resume();
            play_pause.setChecked(true);
        });
        rlPlayPause.setOnClickListener(view -> play_pause.setChecked(true));
        play_pause.setOnCheckedChangeListener((compoundButton, b) -> {
            play_pause.setChecked(b);
            if (b) {
                videoView.pause();
                paused = true;

            } else {
                paused = false;
                seek.setProgress(0);
                videoView.start();
                if (snip.getIs_virtual_version() == 1 || snip.getParent_snip_id() != 0) {
                    new CountDownTimer((long) snip.getSnip_duration() * 1000, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            exo_duration.setText(timeConversion((long) 1000+ ((int)snip.getSnip_duration() * 1000) - (int)millisUntilFinished) + "/" + timeConversion((long) total_duration));
                            seek.setProgress(1000+ ((int)snip.getSnip_duration() * 1000) - (int)millisUntilFinished);
                        }

                        @Override
                        public void onFinish() {
                            if (!paused) {
                                videoView.stopPlayback();
                                videoView.resume();
                                play_pause.setChecked(true);
                            }
                            seek.setProgress(0);
                        }
                    }.start();
                }else{
                    new CountDownTimer((long) snip.getTotal_video_duration() * 1000, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            exo_duration.setText(timeConversion((long) 1000+ ((int)snip.getTotal_video_duration() * 1000) - (int)millisUntilFinished) + "/" + timeConversion((long) total_duration));
                            seek.setProgress(1000+ ((int)total_duration) - (int)millisUntilFinished);
                        }

                        @Override
                        public void onFinish() {
                            if (!paused) {
                                videoView.stopPlayback();
                                videoView.resume();
                                play_pause.setChecked(true);
                            }
//                            exo_duration.setText(timeConversion(videoView.getCurrentPosition() * 1000) + "/" + timeConversion((long) snip.getTotal_video_duration() * 1000));

                        }
                    }.start();
                }

//                if (snip.getIs_virtual_version() == 1) {
//                    long AUTO_DISMISS_MILLIS = (long) snip.getSnip_duration() * 1000 - (long) current_pos;
//                    new CountDownTimer(AUTO_DISMISS_MILLIS, 1000) {
//                        @Override
//                        public void onTick(long millisUntilFinished) {
//
//                        }
//
//                        @Override
//                        public void onFinish() {
//
//                            if (!paused) {
//                                videoView.stopPlayback();
//                                videoView.resume();
//                                play_pause.setChecked(true);
//                            }
//                        }
//                    }.start();
//                }

            }
        });

        if (snip.getIs_virtual_version() == 1) {
            tvConvertToReal.setVisibility(View.VISIBLE);
        }else{
            tvConvertToReal.setVisibility(View.GONE);
        }

        Seek(Orientation.HORIZONTAL, videoView);
        set(this);
    }

    // display video progress
    public void setVideoProgress() {
        if (snip.getIs_virtual_version() == 1 || snip.getParent_snip_id() != 0) {
            videoView.seekTo((int) snip.getStart_time() * 1000);
        }else{
            videoView.seekTo(100);
        }
        //get the video duration
        //TODO
        if (snip.getIs_virtual_version() == 1 || snip.getParent_snip_id() != 0) {
            total_duration = snip.getSnip_duration() * 1000;
        } else {
            total_duration = snip.getTotal_video_duration() * 1000;
            current_pos = videoView.getCurrentPosition();
        }
        //display video duration
        exo_duration.setText(timeConversion((long) current_pos) + "/" + timeConversion((long) total_duration));

        if (snip.getIs_virtual_version() == 1 || snip.getParent_snip_id() != 0) {
            seek.setMax((int) snip.getSnip_duration() * 1000);
        } else {
            seek.setMax((int) total_duration);
        }
//        seekbar change listner
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                paused = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                paused = false;
                current_pos = seekBar.getProgress();
                videoView.seekTo((int) current_pos);
            }
        });
    }

    public String timeConversion(long value) {
        String videoTime;
        int dur = (int) value;
        int hrs = (dur / 3600000);
        int mns = (dur / 60000) % 60000;
        int scs = dur % 60000 / 1000;

        if (hrs > 0) {
            videoTime = String.format("%02d:%02d:%02d", hrs, mns, scs);
        } else {
            videoTime = String.format("%02d:%02d", mns, scs);
        }
        return videoTime;
    }

    private void validateVideo(Snip snip) {
        String destinationPath = snip.getVideoFilePath();

        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                VIDEO_DIRECTORY_NAME);
        // Create storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return;
            }
        }
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + "VID_" + System.currentTimeMillis() + ".mp4");

        String[] complexCommand = {"ffmpeg", "-i", String.valueOf(destinationPath), "-ss", TrimmerUtils.formatCSeconds((long) snip.getStart_time()),
                "-to", TrimmerUtils.formatCSeconds((long) snip.getEnd_time()), "-async", "1", String.valueOf(mediaFile)};
        KProgressHUD hud = CommonUtils.showProgressDialog(this);
        FFmpegCmd.exec(complexCommand, 0, new OnEditorListener() {
            @Override
            public void onSuccess() {
//                EventData eventData= new EventData();
//                eventData.setEvent_id(event.getEvent_id());
//                eventData.setEvent_title(event.getEvent_title());
//                eventData.setEvent_created(event.getEvent_created());
//                eventData.addEventSnip(snip);
                snip.setIs_virtual_version(0);
                snip.setVideoFilePath(mediaFile.getAbsolutePath());
                AppClass.getAppInsatnce().setEventSnipsFromDb(event,snip);
                appRepository.updateSnip(snip);
                Hd_snips hdSnips = new Hd_snips();
                hdSnips.setVideo_path_processed(mediaFile.getAbsolutePath());
                hdSnips.setSnip_id(snip.getSnip_id());
                appRepository.insertHd_snips(hdSnips);
                AppClass.getAppInsatnce().setInsertionInProgress(true);
                if (hud.isShowing())
                    hud.dismiss();

                runOnUiThread(() -> {
                    tvConvertToReal.setVisibility(View.GONE);
                    Toast.makeText(getApplicationContext(), "Video saved to gallery", Toast.LENGTH_SHORT).show();
                });

//                appViewModel.loadGalleryDataFromDB(ActivityPlayVideo.this);

            }

            @Override
            public void onFailure() {
                if (hud.isShowing())
                    hud.dismiss();
                runOnUiThread(() ->
                        Toast.makeText(getApplicationContext(), "Failed to trim", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onProgress(float progress) {
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
