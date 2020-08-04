package com.hipoint.snipback;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;
import android.widget.Chronometer;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.hipoint.snipback.room.entities.Snip;

import java.util.concurrent.TimeUnit;

public class ActivityPlayVideo extends Swipper {
    VideoView videoView;
    private Snip snip;
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

        seek = findViewById(R.id.seek);
        exo_duration = findViewById(R.id.exo_duration);
        play_pause = findViewById(R.id.play_pause);

        Intent intent = getIntent();
        snip = intent.getParcelableExtra("snip");


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

//        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
//        mediaMetadataRetriever.setDataSource(this, Uri.parse(snip.getVideoFilePath()));

        videoView = (VideoView) findViewById(R.id.videoView);
        Uri video1 = Uri.parse(snip.getVideoFilePath());
        videoView.setVideoURI(video1);
        videoView.setOnPreparedListener(mp -> setVideoProgress());

        videoView.setOnCompletionListener(mp -> {
            // not playVideo
            // playVideo();
            mp.start();
            mp.stop();
        });
        play_pause.setOnCheckedChangeListener((compoundButton, b) -> {
            play_pause.setChecked(b);
            if (b) {
                if (videoView.isPlaying()) {
                    videoView.pause();
                }
            } else {
                videoView.start();
                seek.setProgress((int) current_pos);
            }
        });
        seek.setMax((int) total_duration);
        //TODO
//        seek.setMax((int) snip.getSnip_duration());
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
        videoView.requestFocus();
        videoView.start();

        if (snip.getIs_virtual_version() == 1) {
            new CountDownTimer(6000,1000) {
                @Override
                public void onTick(long millisUntilFinished) {

                }
                @Override
                public void onFinish() {
                    videoView.stopPlayback();
                    play_pause.setChecked(true);
                }
            }.start();
        }

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
        //TODO
        current_pos = videoView.getCurrentPosition();
        if (snip.getIs_virtual_version() == 1) {
            total_duration = 5 * 1000;
        } else {
            total_duration = videoView.getDuration();
        }

        //display video duration
        exo_duration.setText(timeConversion((long) current_pos) + "/" + timeConversion((long) total_duration));
//        total.setText(timeConversion((long) total_duration));
//        current.setText(timeConversion((long) current_pos));
        if (snip.getIs_virtual_version() == 1) {
            videoView.seekTo((int) snip.getStart_time() * 1000);
        }
        seek.setMax((int) total_duration);
        final Handler handler = new Handler();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    current_pos = videoView.getCurrentPosition();
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
                    seek.setProgress((int) current_pos);
                    handler.postDelayed(this, 1000);
                } catch (IllegalStateException ed) {
                    ed.printStackTrace();
                }
            }
        };
        handler.postDelayed(runnable, 500);

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
