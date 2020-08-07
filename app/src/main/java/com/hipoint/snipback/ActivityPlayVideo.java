package com.hipoint.snipback;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.VideoView;

import com.hipoint.snipback.room.entities.Snip;

public class ActivityPlayVideo extends Swipper {
    VideoView videoView;
    private Snip snip;
    private SeekBar seek;
    double current_pos, total_duration;
    private TextView exo_duration;
    private Switch play_pause;
    boolean paused=false;
    private RelativeLayout play_forwardbutton,back_arrow,button_camera;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        seek = findViewById(R.id.seek);
        exo_duration = findViewById(R.id.exo_duration);
        play_pause = findViewById(R.id.play_pause);
        videoView = (VideoView) findViewById(R.id.videoView);
        back_arrow=findViewById(R.id.back_arrow);
        button_camera=findViewById(R.id.button_camera);

        Intent intent = getIntent();
        snip = intent.getParcelableExtra("snip");
        Uri video1 = Uri.parse(snip.getVideoFilePath());
        videoView.setVideoURI(video1);
        videoView.requestFocus();
        videoView.start();

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
        play_forwardbutton=findViewById(R.id.play_forwardbutton);
        play_forwardbutton.setOnClickListener(v -> {
//            videoView.stopPlayback();
//            videoView.resume();
//            List<Snip> allSnips = AppClass.getAppInsatnce().getAllSnip();

//                Uri video = Uri.parse(allSnips.get(1)+"");
//                videoView.setVideoURI(video);

        });

        videoView.setOnPreparedListener(mp -> setVideoProgress());

        videoView.setOnCompletionListener(mp -> {
            videoView.stopPlayback();
            videoView.resume();
            play_pause.setChecked(true);
        });

        play_pause.setOnCheckedChangeListener((compoundButton, b) -> {
            play_pause.setChecked(b);
            if (b) {
                videoView.pause();
                paused=true;

            } else {
                paused=false;
                videoView.seekTo((int) snip.getStart_time()*1000);
                videoView.start();
                seek.setProgress((int) current_pos);

                if (snip.getIs_virtual_version() == 1) {
                    long AUTO_DISMISS_MILLIS = 6000-(long)current_pos;
                    new CountDownTimer(AUTO_DISMISS_MILLIS, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {

                        }

                        @Override
                        public void onFinish() {

                            if (!paused){
                                videoView.stopPlayback();
                                videoView.resume();
                                play_pause.setChecked(true);
                            }
                        }
                    }.start();
                }

            }
        });


        //TODO
        if (snip.getIs_virtual_version() == 1) {
            seek.setMax((int) snip.getSnip_duration()*1000);
        }else {
            seek.setMax((int) total_duration);
        }


        if (snip.getIs_virtual_version() == 1) {
            new CountDownTimer(6000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {

                }

                @Override
                public void onFinish() {
                    if (!paused){
                        videoView.stopPlayback();
                        videoView.resume();
                        play_pause.setChecked(true);
                    }
                }
            }.start();
        }

        Seek(Orientation.HORIZONTAL, videoView);
        set(this);
    }

    // display video progress
    public void setVideoProgress() {
        //get the video duration
        //TODO

        if (snip.getIs_virtual_version() == 1) {
            total_duration = 5 * 1000;
        } else {
            total_duration = videoView.getDuration();
            current_pos = videoView.getCurrentPosition();
        }
        //display video duration
        exo_duration.setText(timeConversion((long) current_pos) + "/" + timeConversion((long) total_duration));

        if (snip.getIs_virtual_version() == 1) {
            videoView.seekTo((int) snip.getStart_time()*1000);
        }
        if (snip.getIs_virtual_version() == 1) {
            seek.setMax((int) snip.getSnip_duration()*1000);
        }else {
            seek.setMax((int) total_duration);
        }

        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (snip.getIs_virtual_version() == 1) {
                        current_pos =videoView.getCurrentPosition()-(snip.getStart_time()*1000);
                    }else {
                        current_pos = videoView.getCurrentPosition();
                    }
                    if (snip.getIs_virtual_version() == 1) {
                        total_duration = 5 * 1000;
                    } else {
                        total_duration = videoView.getDuration();
                    }

                    exo_duration.setText(timeConversion((long) current_pos) + "/" + timeConversion((long) total_duration));
                    if (current_pos > 0) {
                        ObjectAnimator animation = ObjectAnimator.ofInt(seek, "progress", (int) current_pos);
                        animation.setDuration(400);
                        animation.setInterpolator(new DecelerateInterpolator());
                        animation.start();
                    }


                    handler.postDelayed(this, 1000);
                } catch (IllegalStateException ed) {
                    ed.printStackTrace();
                }
            }
        };
        handler.postDelayed(runnable, 500);

//        seekbar change listner
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                paused=true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                paused=false;
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

}
