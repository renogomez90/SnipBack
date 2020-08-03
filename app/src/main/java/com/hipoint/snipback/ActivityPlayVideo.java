package com.hipoint.snipback;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.animation.DecelerateInterpolator;
import android.widget.CompoundButton;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.hipoint.snipback.R;

public class ActivityPlayVideo extends Swipper {
    VideoView videoView;
    private String uri;
    PlayerView simpleExoPlayerView;
    private SimpleExoPlayer player;
    private TrackSelector trackSelector;
    private BandwidthMeter bandwidthMeter;
    private ExtractorsFactory extractorsFactory;
    private DefaultBandwidthMeter defaultBandwidthMeter;
    private MediaSource mediaSource;
    private TrackSelection.Factory trackSelectionFactory;
    private DataSource.Factory dataSourceFactory;
    private SeekBar seek;
    double current_pos, total_duration;
    private TextView exo_duration;
    private Switch play_pause;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
//        setContentView(R.layout.layout_play_video);

        seek=findViewById(R.id.seek);
        exo_duration=findViewById(R.id.exo_duration);
        play_pause=findViewById(R.id.play_pause);

        Intent intent=getIntent();
        uri=intent.getStringExtra("uri");


//        bandwidthMeter = new DefaultBandwidthMeter();
//
//        extractorsFactory = new DefaultExtractorsFactory();
//
//        trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
//
//        trackSelector = new DefaultTrackSelector(trackSelectionFactory);
//
//        defaultBandwidthMeter = new DefaultBandwidthMeter();
//        dataSourceFactory = new DefaultDataSourceFactory(this,
//                Util.getUserAgent(this, "mediaPlayerSample"), defaultBandwidthMeter);
//
//        mediaSource = new ExtractorMediaSource(Uri.parse(uri),
//                dataSourceFactory,
//                extractorsFactory,
//                null,
//                null);
//
//        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);


        int w;
        int h;

        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(this,Uri.parse(uri));
        String height = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String width = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        w = Integer.parseInt(width);
        h = Integer.parseInt(height);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height1 = displayMetrics.heightPixels;
        int width1 = displayMetrics.widthPixels;



        videoView = (VideoView) findViewById(R.id.videoView);
//        MediaController mediaController = new MediaController(this);
//        mediaController.setAnchorView(videoView);
//        videoView.setMediaController(mediaController);
        Uri video1 = Uri.parse(uri);
        videoView.setVideoURI(video1);
//        videoView.setMinimumWidth(width1);
//        videoView.setMinimumHeight(h);
        videoView.requestFocus();
        videoView.start();

        current_pos = videoView.getCurrentPosition();
        total_duration = videoView.getDuration();

// video finish listener
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                // not playVideo
                // playVideo();

                mp.start();
                mp.stop();
            }
        });


        play_pause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    if (videoView.isPlaying()) {
                        videoView.pause();

                    }
                } else {

//                    setVideoProgress();
                    videoView.start();
                    seek.setProgress((int) current_pos);


                }
            }
        });


        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                setVideoProgress();
            }
        });



        seek.setMax((int) total_duration);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                videoView.seekTo((int) progress);

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                current_pos = seekBar.getProgress();
                videoView.seekTo((int) current_pos);
            }
        });


//        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);
//        simpleExoPlayerView = (PlayerView)findViewById(R.id.player_view);
//        simpleExoPlayerView.setPlayer(player);
//        player.prepare(mediaSource);
//        player.setPlayWhenReady(true);

//        Brightness(Orientation.CIRCULAR);
//        Volume(Orientation.VERTICAL);
        Seek(Orientation.HORIZONTAL, videoView);
        set(this);
    }

    // display video progress
    public void setVideoProgress() {
        //get the video duration
        current_pos = videoView.getCurrentPosition();
        total_duration = videoView.getDuration();

        //display video duration
        exo_duration.setText(timeConversion((long) current_pos)+"/"+timeConversion((long) total_duration));
//        total.setText(timeConversion((long) total_duration));
//        current.setText(timeConversion((long) current_pos));
        seek.setMax((int) total_duration);
        final Handler handler = new Handler();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    current_pos = videoView.getCurrentPosition();
                    total_duration = videoView.getDuration();
                    exo_duration.setText(timeConversion((long) current_pos)+"/"+timeConversion((long) total_duration));
                    if (current_pos > 0) {
                        ObjectAnimator animation = ObjectAnimator.ofInt(seek, "progress", (int) current_pos);
                        animation.setDuration(400);
                        animation.setInterpolator(new DecelerateInterpolator());
                        animation.start();
                    }
                    seek.setProgress((int) current_pos);
                    handler.postDelayed(this, 1000);
                } catch (IllegalStateException ed){
                    ed.printStackTrace();
                }
            }
        };
        handler.postDelayed(runnable, 1000);

        //seekbar change listner
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
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
