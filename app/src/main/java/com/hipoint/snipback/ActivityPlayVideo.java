package com.hipoint.snipback;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
//        setContentView(R.layout.layout_play_video);

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

        videoView = (VideoView) findViewById(R.id.videoView);
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        Uri video1 = Uri.parse(uri);
        videoView.setVideoURI(video1);
        videoView.requestFocus();
        videoView.start();

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
}
