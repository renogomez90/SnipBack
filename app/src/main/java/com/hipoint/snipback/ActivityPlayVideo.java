package com.hipoint.snipback;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.Objects;

import Jni.FFmpegCmd;
import VideoHandle.OnEditorListener;

public class ActivityPlayVideo extends Swipper {
    private String VIDEO_DIRECTORY_NAME = "Snipback";
    private String VIDEO_DIRECTORY_NAME_SHARE = "Snipback_Share";
    VideoView videoView;
    private Snip snip;
    private SeekBar seek;
    double current_pos, total_duration;
    private TextView exo_duration;
    Bitmap bmp;
    private RelativeLayout rlPlayPause;
    private Switch play_pause;
    boolean paused = false;
    private RelativeLayout play_forwardbutton, back_arrow, button_camera, layout_share;
    private ImageButton tvConvertToReal;
    private Event event;
    private AppRepository appRepository;
    private AppViewModel appViewModel;
    boolean seeked = false;
    private CounterClass counterClass;
    private static final int pick = 100;
    String outputPath_share ="/storage/emulated/0/Snipback_Share/VID_Share.mp4";
    String yourAudioPath="/storage/emulated/0/Download/file_example_MP#_1MG.mp3";
    String yourAudioPathwav="/storage/emulated/0/Download/file_example_WAV_1MG.wav";
    String input_share ="/storage/emulated/0/Snipback/VID_1597235825538.mp4";
    int orientation;
//    String yourAudioPath = "/storage/emulated/0/Download/mp3.mp3";
    String output_path_audio = "/storage/emulated/0/Snipback_Share/VID_Share_Audio.mp4";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
//        setContentView(R.layout.activity_main2);


        orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {

            setContentView(R.layout.activity_main2);
        } else {

            setContentView(R.layout.land_video_mode);
        }


        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        seek = findViewById(R.id.seek);
        exo_duration = findViewById(R.id.exo_duration);
        play_pause = findViewById(R.id.play_pause);
        rlPlayPause = findViewById(R.id.play_pouse);
        videoView = (VideoView) findViewById(R.id.videoView);

        tvConvertToReal = findViewById(R.id.tvConvertToReal);
        appViewModel = ViewModelProviders.of(this).get(AppViewModel.class);
        back_arrow = findViewById(R.id.back_arrow);
        button_camera = findViewById(R.id.button_camera);

        Intent intent = getIntent();
        snip = intent.getParcelableExtra("snip");
        appRepository = new AppRepository(getApplicationContext());
        appViewModel.getEventByIdLiveData(snip.getEvent_id()).observe(this, snipevent -> {
            event = snipevent;
        });
        Uri video1 = Uri.parse(snip.getVideoFilePath());
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(String.valueOf(video1));

        //Set the video Uri as data source for MediaMetadataRetriever
        retriever.setDataSource(this, video1);
        //Get one "frame"/bitmap - * NOTE - no time was set, so the first available frame will be used
        bmp = retriever.getFrameAtTime();

        //Get the bitmap width and height
        int videoWidth = bmp.getWidth();
        int videoHeight = bmp.getHeight();

        Log.d("width", String.valueOf(videoWidth));
        Log.d("height", String.valueOf(videoHeight));
        retriever.release();
        videoView.setVideoURI(video1);
        videoView.requestFocus();
        play_pause.setChecked(true);

        // share a video
        layout_share = findViewById(R.id.layout_share);
        layout_share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveVideo(snip);
            }
        });

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

//        videoView.setOnPreparedListener(mp -> setVideoProgress()
//        );

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
                seek.setMax((int) snip.getSnip_duration() * 1000);
                videoView.start();
                if (snip.getIs_virtual_version() == 1 || snip.getParent_snip_id() != 0) {
                    videoView.seekTo((int) snip.getStart_time() * 1000);

                    new CountDownTimer((long) snip.getSnip_duration() * 1000, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {

                            exo_duration.setText(timeConversion((long) 1000 + ((int) snip.getSnip_duration() * 1000) - (int) millisUntilFinished) + "/" + timeConversion((long) snip.getSnip_duration() * 1000));
                            seek.setProgress(1000 + ((int) snip.getSnip_duration() * 1000) - (int) millisUntilFinished);
//                            current_pos = videoView.getCurrentPosition();
//                            total_duration = (int) snip.getTotal_video_duration() * 1000;
//                            exo_duration.setText(timeConversion((long) videoView.getCurrentPosition()) + "/" + timeConversion((long) snip.getSnip_duration() * 1000));
//                            seek.setProgress((int) current_pos);
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


                } else {
                    setVideoProgressParent();
//                    exo_duration.setText(timeConversion((long) 1000 + ((int) snip.getSnip_duration() * 1000) - (int) millisUntilFinished) + "/" + timeConversion((long) total_duration));
                    seek.setProgress(videoView.getCurrentPosition());
////                    new CountDownTimer((long) snip.getTotal_video_duration() * 1000, 1000) {
//                    //new
//                    new CountDownTimer((long) snip.getTotal_video_duration() * 1000-videoView.getCurrentPosition(), 1000) {
//                        @Override
//                        public void onTick(long millisUntilFinished) {
//                            Log.e("millisUntilFinished", "millisUntilFinished : " + millisUntilFinished);
//                            exo_duration.setText(timeConversion((long) 1000 + ((int) snip.getTotal_video_duration() * 1000) - (int) millisUntilFinished) + "/" + timeConversion((long) total_duration));
////                            seek.setProgress(1000 + ((int) total_duration) - (int) millisUntilFinished);
//                            if (!seeked){
//                                seek.setProgress(1000 + ((int) total_duration) - (int) millisUntilFinished);
//                                Log.e("total_duration", "total_duration : " + ((int) total_duration));
//                            }else {
//                                seek.setProgress(videoView.getCurrentPosition() + ((int) total_duration) - (int) millisUntilFinished);
//                            }
//
//
//                            // new change
//                            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//                                @Override
//                                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                                    Log.e("progress", "prog : " + progress);
//                                    videoView.seekTo(progress);
//                                    seek.setProgress(progress);
//
//
//                                }
//
//                                @Override
//                                public void onStartTrackingTouch(SeekBar seekBar) {
//
//                                    seeked = true;
//                                    seek.setProgress(seekBar.getProgress());
//                                    videoView.seekTo(seekBar.getProgress());
//
//
//                                }
//
//                                @Override
//                                public void onStopTrackingTouch(SeekBar seekBar) {
//                                    seeked = true;
//                                    videoView.seekTo(seekBar.getProgress());
//                                    seek.setProgress(seekBar.getProgress());
//
//
//                                }
//                            });
//
//                        }
//
//                        @Override
//                        public void onFinish() {
//                            if (!paused) {
//                                videoView.stopPlayback();
//                                videoView.resume();
//                                play_pause.setChecked(true);
//                            }
////                            exo_duration.setText(timeConversion(videoView.getCurrentPosition() * 1000) + "/" + timeConversion((long) snip.getTotal_video_duration() * 1000));
//
//                        }
//                    }.start();
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
        } else {
            tvConvertToReal.setVisibility(View.GONE);
        }

        Seek(Orientation.HORIZONTAL, videoView);

        set(this);
    }

    //     display video progress
    public void setVideoProgress() {
        //change now
//        videoView.postDelayed(onEverySecond, 60);

        if (snip.getIs_virtual_version() == 1 || snip.getParent_snip_id() != 0) {
            videoView.seekTo((int) snip.getStart_time() * 1000);
        } else {
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

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

    }

    public void setVideoProgressParent() {

//        total_duration = videoView.getDuration();
        seek.setMax((int) snip.getTotal_video_duration() * 1000);

        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {

                    current_pos = videoView.getCurrentPosition();
                    total_duration = (int) snip.getTotal_video_duration() * 1000;
                    exo_duration.setText(timeConversion((long) videoView.getCurrentPosition()) + "/" + timeConversion((long) total_duration));
                    seek.setProgress((int) current_pos);

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
                AppClass.getAppInsatnce().setEventSnipsFromDb(event, snip);
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


    private void saveVideo(Snip snip) {
        String destinationPath = snip.getVideoFilePath();

        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
                VIDEO_DIRECTORY_NAME_SHARE);
        // Create storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return;
            }
        }
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator
                + "VID_Share.mp4");
        File mediaFile_audio = new File(mediaStorageDir.getPath() + File.separator
                + "VID_Share_Audio.mp4");
//        String imagepath = "/storage/emulated/0/Screenshot/c.png";
        String imagepath = "/storage/emulated/0/DCIM/Screenshots/c.jpg";
        String imageUri = "drawable://" + R.drawable.ic_snipback_logo;

        // merge two videos
//        String[] complexCommand = {"-y","-i",destinationPath,"-i",destinationPath,"-strict","experimental","-filter_complex",
//                "[0:v]scale=480x640,setsar=1:1[v0];[1:v]scale=480x640,setsar=1:1[v1];[v0][0:a][v1][1:a] concat=n=2:v=1:a=1",
//                "-ab","48000","-ac","2","-ar","22050","-s","480x640","-vcodec","libx264","-crf","26","-q","4","-preset",
//                "ultrafast",String.valueOf(mediaFile)};
        // can be used for downloading
//        String[] complexCommand ={"-y","-i",destinationPath,"-codec","copy","-shortest","-preset","ultrafast",String.valueOf(mediaFile)};

//        String strCommand = "-y","-i","-loop", "1" ,"-t" ,"3" ,"-i",imagepath,"-loop", "1" ,"-t" ,"3" ,"-i",imagepath, "-loop", "1" ,"-t" ,"3" ,"-i",imagepath,"-loop", "1" ,"-t" ,"3" ,"-i",imagepath, "-filter_complex","[0:v]trim=duration=3,fade=t=out:st=2.5:d=0.5[v0];[1:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v1];[2:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v2];[3:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v3];[v0][v1][v2][v3]concat=n=4:v=1:a=0","format=yuv420p[v]", "-map", "[v]","-preset","ultrafast",String.valueOf(mediaFile);

// remove video audio
//        String[] complexCommand = {"-y","-i",input_share,"-vcodec","copy","-an",String.valueOf(mediaFile)};


//        {"-i", yourAudioPath, "-i", input_share, "-codec", "copy", "-shortest", String.valueOf(mediaFile)};

//        String[] complexCommand = {"-y","-i",input_share,"-vcodec","copy","-an",String.valueOf(mediaFile)};
//        String[] complexCommandaudio = {"-y","-i",outputPath_share,"-i",yourAudioPath,"-c:v","copy",String.valueOf(mediaFile_audio)};

//        ffmpeg -i output1.mp4 -i audio.mp3 finalout.mp4

//        ffmpeg -y -i image.png -i audio.mp3 -c:a copy result.avi


//        String[] complexCommand = {"-y","-i",imagepath,"-i",yourAudioPath,"c:a","copy",String.valueOf(mediaFile)};
//        String[] complexCommand = {"-y","-i",imagepath,"-i",yourAudioPath,"c:a","copy",String.valueOf(mediaFile)};

//        String[] complexCommand = {"-y","-i",imagepath,"-i",yourAudioPathwav,"-c:v","libx264","-tune","stillimage","-c:a","aac","-b:a","192k","-pix_fmt","yuv420p","-shortest"
//                ,String.valueOf(mediaFile)};

//        String[] complexCommand = {"-y","-i","-loop","-vframes","14490","-i",imagepath,"-i",yourAudioPath,"-y","-r","30","-b","2500k","-acodec","ac3","-ab","234k","-c:v","mpeg4",
//                "ultrafast",String.valueOf(mediaFile)};
//        ffmpeg -loop 1 -i image.jpg -i audio.wav -c:v libx264 -tune stillimage -c:a aac -b:a 192k -pix_fmt yuv420p -shortest out.mp4

//        ffmpeg -loop_input -vframes 14490 -i imagine.jpg -i audio.mp3 -y -r 30
//                -b 2500k -acodec ac3 -ab 384k -vcodec mpeg4 result.mp4

//        String[] complexCommand ={"-y","-i",destinationPath,"-codec","copy","-shortest","-preset","ultrafast",String.valueOf(mediaFile)};

//        ffmpeg -y -loop 1 -t 3.03 -i ~/Pictures/yaya_speech_choose2.png   -r 1  -vcodec libx264 -b:v 200k -bt 350k   -f mp4 ~/Videos/dummy.mp4

//        ffmpeg -loop_input -vframes 14490 -i imagine.jpg -i audio.mp3 -y -r 30
//                -b 2500k -acodec ac3 -ab 384k -vcodec mpeg4 result.mp4


//        String[] complexCommand = {"-y","-i","-t", String.valueOf(3.03),"-i",imagepath,"-r","1","-vcodec",
//                "libx264",
//                "-b:v","200k","-bt","350k","-f","mp4",
//                "ultrafast",String.valueOf(mediaFile)};
//        String[] complexCommand = {,"-loop","1","-i",imagepath,"-i",yourAudioPathwav,"-c:v","libx264","-tune","stillimage","-c:a","aac","-b:a","192k","-pix_fmt","yuv420p","-shortest"
//                ,String.valueOf(mediaFile)};

//        String strCommand = "ffmpeg -loop 1 -t 3 -i " + /sdcard/videokit/1.jpg + " -loop 1 -t 3 -i " + /sdcard/videokit/2.jpg + " -loop 1 -t 3 -i " + /sdcard/videokit/3.jpg + " -loop 1 -t 3 -i " + /sdcard/videokit/4.jpg + " -filter_complex [0:v]trim=duration=3,fade=t=out:st=2.5:d=0.5[v0];[1:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v1];[2:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v2];[3:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v3];[v0][v1][v2][v3]concat=n=4:v=1:a=0,format=yuv420p[v] -map [v] -preset ultrafast " + /sdcard/videolit/output.mp4;


//        String[] complexCommand ={"ffmpeg","-loop", "1" ,"-t" ,"3" ,"-i",imagepath,"-loop", "1" ,"-t" ,"3" ,"-i",imagepath, "-loop", "1" ,"-t" ,"3" ,"-i",imagepath,"-loop", "1" ,"-t" ,"3" ,"-i",imagepath, "-filter_complex","[0:v]trim=duration=3,fade=t=out:st=2.5:d=0.5[v0];[1:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v1];[2:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v2];[3:v]trim=duration=3,fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5[v3];[v0][v1][v2][v3]concat=n=4:v=1:a=0","yuv420p","-map","[v]","-preset","ultrafast",String.valueOf(mediaFile)};
//        String[] complexCommand ={ "ffmpeg" ,"-loop" ,"1" ,"-i ",imagepath,"-i" ,yourAudioPath ,"-c:v" ,"libx264" ,"-tune","stillimage","-c:a","aac","-b:a ","192k","-pix_fmt","yuv420p" ,"-shortest",String.valueOf(mediaFile)};

        // image to video working
//        String[] complexCommandaddaudio ={
//                "-y",
//                "-r",
//                "1/5",
//                "-i",
//                imagepath, // only one image file path
//                "-c:v",
//                "libx264",
//                "-vf",
//                "fps=25",
//                "-pix_fmt",
//                "yuv420p",
//                String.valueOf(mediaFile_audio)};

//        String[] complexCommand ={"ffmpeg","-r","1","-loop","1","-i",imagepath,"-i",yourAudioPathwav,"copy","-r","1","-shortest","-vf","scale=1280:720", String.valueOf(mediaFile)};
//        String[] complexCommand ={"ffmpeg","-i",imagepath,"-i",yourAudioPath,"-c:v","libx264","-tune","stillimage","-c:a","copy", String.valueOf(mediaFile)};
        //        ffmpeg -loop 1 -y -i image.jpg -i music.mp3 -shortest -pix_fmt yuv420p output.mp4

        String[] complexCommandaddaudio = {"ffmpeg", "-loop", "1", "-y", "-i", imagepath, "-i", yourAudioPath, "-shortest", "-pix_fmt", "yuv420p", "-preset", "ultrafast", String.valueOf(mediaFile_audio)};
        String[] complexCommandmergevideo = {"-y", "-i", String.valueOf(mediaFile_audio), "-i", destinationPath, "-strict", "experimental", "-filter_complex",
                "[0:v]scale=480x640,setsar=1:1[v0];[1:v]scale=480x640,setsar=1:1[v1];[v0][0:a][v1][1:a] concat=n=2:v=1:a=1",
                "-ab", "48000", "-ac", "2", "-ar", "22050", "-s", "480x640", "-vcodec", "libx264", "-crf", "26", "-q", "4", "-preset",
                "ultrafast", String.valueOf(mediaFile)};


        KProgressHUD hud = CommonUtils.showProgressDialog(this);
        FFmpegCmd.exec(complexCommandaddaudio, 0, new OnEditorListener() {
            @Override
            public void onSuccess() {

                runOnUiThread(() -> {

                    Toast.makeText(getApplicationContext(), "Video audio saving..", Toast.LENGTH_SHORT).show();

                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            // Do something after 5s = 5000ms

                            FFmpegCmd.exec(complexCommandmergevideo, 0, new OnEditorListener() {
                                @Override
                                public void onSuccess() {

                                    runOnUiThread(() -> {

                                        Toast.makeText(getApplicationContext(), "Video audio merge", Toast.LENGTH_SHORT).show();
//
                                        final Handler handler = new Handler();
                                        handler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                // Do something after 5s = 5000ms
                                                Uri fileUri = null;
                                                File f = new File(outputPath_share);
                                                fileUri = Uri.fromFile(f);
                                                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                                                if (mediaFile.exists()) {
                                                    sharingIntent.setType("video/*");
                                                    sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                    sharingIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                                    sharingIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                                                    startActivityForResult(Intent.createChooser(sharingIntent, "Share Video"), 777);
                                                }
                                            }
                                        }, 1000);

                                    });


                                    if (hud.isShowing())
                                        hud.dismiss();

                                }

                                @Override
                                public void onFailure() {
                                    if (hud.isShowing())
                                        hud.dismiss();
                                    runOnUiThread(() ->
                                            Toast.makeText(getApplicationContext(), "Failed to Save", Toast.LENGTH_SHORT).show());
                                }

                                @Override
                                public void onProgress(float progress) {

                                }
                            });
                        }
                    }, 1000);

                });




            }

            @Override
            public void onFailure() {
                if (hud.isShowing())
                    hud.dismiss();
                runOnUiThread(() ->
                        Toast.makeText(getApplicationContext(), "Failed to Save", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onProgress(float progress) {

            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //Check if it is from the same code, if yes delete the temp file
        if (requestCode == 777) {

            File delete = new File(outputPath_share);
            if (delete.exists()) {
                if (delete.delete()) {
//                            Log.d("Deleted", ""+uri);
                }
                boolean exists = delete.exists();
                Log.d("Deleted", "does it exist? " + exists);
            }


            File delete1 = new File(output_path_audio);
            if (delete1.exists()) {
                if (delete1.delete()) {
//                            Log.d("Deleted", ""+uri);
                }
                boolean exists = delete1.exists();
                Log.d("Deleted", "does it exist? " + exists);
            }
        }
    }

    public String getURLForResource(int resourceId) {
        //use BuildConfig.APPLICATION_ID instead of R.class.getPackage().getName() if both are not same
        return Uri.parse("android.resource://" + R.class.getPackage().getName() + "/" + resourceId).toString();
    }
}